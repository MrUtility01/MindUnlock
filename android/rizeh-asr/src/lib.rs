//! Cache-aware, bounded-memory Shenava Rizeh INT4 streaming ASR.
//! The Android layer supplies packaged local assets; this crate has no networking code.

use anyhow::{bail, ensure, Context, Result};
use jni::objects::{JClass, JShortArray, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;
use rustfft::{num_complex::Complex, Fft, FftPlanner};
use std::sync::Arc;
use std::panic::{catch_unwind, AssertUnwindSafe};
use tract_onnx::prelude::*;

const SAMPLE_RATE: usize = 16_000;
const HOP: usize = 160;
const N_FFT: usize = 512;
const WINDOW: usize = 400;
const MEL_BINS: usize = 80;
const INPUT_FRAMES: usize = 25;
const FIRST_CHUNK_FRAMES: usize = 9;
const SHIFT_FRAMES: usize = 16;
const PRE_ENCODE_OVERLAP: usize = 9;
const CACHE_LAYERS: usize = 16;
const CACHE_D_MODEL: usize = 256;
const CACHE_LEFT: usize = 70;
const CACHE_TIME: usize = 8;
const VOCAB: usize = 1025;
const BLANK_ID: usize = 1024;
const LEFT_CONTEXT: usize = 16;
const FINAL_PAD_SAMPLES: usize = (SAMPLE_RATE * 72) / 100;
const DEDUP_GAP: usize = 6;

struct FeatureExtractor {
    mel: Vec<Vec<f32>>,
    hann: Vec<f32>,
    fft: Arc<dyn Fft<f32>>,
}

impl FeatureExtractor {
    fn load(path: &str) -> Result<Self> {
        let bytes = std::fs::read(path).with_context(|| format!("read mel filters: {}", path))?;
        let mel: Vec<Vec<f32>> = serde_json::from_slice(&bytes)?;
        ensure!(mel.len() == MEL_BINS, "expected 80 mel filters, got {}", mel.len());
        ensure!(
            mel.iter().all(|row| row.len() == N_FFT / 2 + 1),
            "mel filters must have shape [80,257]"
        );
        let hann = (0..WINDOW)
            .map(|i| {
                0.5 - 0.5
                    * ((2.0 * std::f32::consts::PI * i as f32) / (WINDOW as f32 - 1.0)).cos()
            })
            .collect();
        let fft = FftPlanner::<f32>::new().plan_fft_forward(N_FFT);
        Ok(Self { mel, hann, fft })
    }

    /// NeMo-compatible unnormalized log-mel, feature-major [80 * fixed_frames].
    fn compute(&self, pcm: &[f32], fixed_frames: usize) -> (Vec<f32>, usize) {
        let max_samples = fixed_frames.saturating_sub(1) * HOP;
        let source = if pcm.len() > max_samples {
            &pcm[pcm.len() - max_samples..]
        } else {
            pcm
        };
        let mut features = vec![0.0f32; MEL_BINS * fixed_frames];
        if source.is_empty() {
            return (features, 1);
        }

        let frame_count = (source.len() / HOP + 1).min(fixed_frames).max(1);
        let mut signal = vec![0.0f32; source.len()];
        signal[0] = source[0];
        for i in 1..source.len() {
            signal[i] = source[i] - 0.97 * source[i - 1];
        }

        let offset = (N_FFT - WINDOW) / 2;
        let guard = 2.0f32.powi(-24);
        let mut buffer = vec![Complex { re: 0.0, im: 0.0 }; N_FFT];
        for frame in 0..frame_count {
            let frame_start = frame as i64 * HOP as i64 - 256;
            buffer.fill(Complex { re: 0.0, im: 0.0 });
            for i in 0..WINDOW {
                let sample_index = frame_start + offset as i64 + i as i64;
                buffer[offset + i].re = reflect_sample(&signal, sample_index) * self.hann[i];
            }
            self.fft.process(&mut buffer);
            for (mel_index, filter) in self.mel.iter().enumerate() {
                let mut energy = 0.0f32;
                for (bin, weight) in filter.iter().enumerate() {
                    energy += (buffer[bin].re * buffer[bin].re
                        + buffer[bin].im * buffer[bin].im)
                        * weight;
                }
                features[mel_index * fixed_frames + frame] = (energy + guard).ln();
            }
        }
        (features, frame_count)
    }
}

fn reflect_sample(signal: &[f32], index: i64) -> f32 {
    let n = signal.len() as i64;
    if n == 0 {
        return 0.0;
    }
    if n == 1 {
        return signal[0];
    }
    let mut position = index;
    while position < 0 || position >= n {
        if position < 0 {
            position = -position;
        }
        if position >= n {
            position = 2 * n - 2 - position;
        }
    }
    signal[position as usize]
}

#[derive(Clone, Copy)]
struct OutputIndices {
    ctc: usize,
    encoded_len: Option<usize>,
    next_channel: usize,
    next_time: usize,
    next_channel_len: usize,
}

fn output_names(model: &TypedModel) -> Vec<String> {
    let outlets = model
        .output_outlets()
        .map(|items| items.to_vec())
        .unwrap_or_default();
    outlets
        .iter()
        .enumerate()
        .map(|(index, outlet)| {
            model
                .outlet_label(*outlet)
                .map(str::to_owned)
                .unwrap_or_else(|| format!("output_{}", index))
        })
        .collect()
}

fn resolve_outputs(names: &[String]) -> Result<OutputIndices> {
    let find = |aliases: &[&str]| {
        aliases
            .iter()
            .find_map(|name| names.iter().position(|item| item == name))
    };
    let required = |role: &str, aliases: &[&str]| -> Result<usize> {
        find(aliases).with_context(|| format!("Rizeh graph is missing {}; outputs={:?}", role, names))
    };
    Ok(OutputIndices {
        ctc: required("CTC log probabilities", &["ctc_logprobs", "logprobs"])?,
        encoded_len: find(&["encoded_len", "encoded_lengths"]),
        next_channel: required(
            "next channel cache",
            &["next_cache_last_channel", "cache_last_channel_next"],
        )?,
        next_time: required(
            "next time cache",
            &["next_cache_last_time", "cache_last_time_next"],
        )?,
        next_channel_len: required(
            "next channel-cache length",
            &["next_cache_last_channel_len", "cache_last_channel_next_len"],
        )?,
    })
}

fn load_tokens(path: &str) -> Result<Vec<String>> {
    let content =
        std::fs::read_to_string(path).with_context(|| format!("read tokens: {}", path))?;
    let mut tokens = vec![String::new(); VOCAB];
    let mut seen = vec![false; VOCAB];
    for line in content.lines() {
        let Some((piece, id_text)) = line.rsplit_once(' ') else {
            continue;
        };
        let Ok(id) = id_text.trim().parse::<usize>() else {
            continue;
        };
        if id < VOCAB {
            tokens[id] = piece.to_owned();
            seen[id] = true;
        }
    }
    ensure!(
        seen.iter().all(|value| *value),
        "tokens.txt must define all 1025 token IDs"
    );
    ensure!(
        tokens[BLANK_ID].contains("blk") || tokens[BLANK_ID].is_empty(),
        "token ID 1024 is not the expected CTC blank"
    );
    Ok(tokens)
}

struct RizehSession {
    model: Arc<TypedRunnableModel>,
    outputs: OutputIndices,
    features: FeatureExtractor,
    tokens: Vec<String>,
    cache_channel: Tensor,
    cache_time: Tensor,
    cache_channel_len: Tensor,
    pending: Vec<f32>,
    base_frame: usize,
    chunk_index: usize,
    previous_token: i64,
    last_emitted_token: i64,
    frames_since_emit: usize,
    previous_chunk_had_emit: bool,
    transcript: String,
}

impl RizehSession {
    fn new(model_path: &str, tokens_path: &str, mel_path: &str) -> Result<Self> {
        let features = FeatureExtractor::load(mel_path)?;
        let tokens = load_tokens(tokens_path)?;
        // The exported graph has dynamic batch/time dimensions. Pin every streaming
        // input contract before tract type inference, matching the model author's native runtime.
        let typed = tract_onnx::onnx()
            .model_for_path(model_path)
            .with_context(|| format!("load Rizeh INT4 ONNX: {}", model_path))?
            .with_input_fact(
                0,
                InferenceFact::dt_shape(f32::datum_type(), tvec!(1, MEL_BINS, INPUT_FRAMES)),
            )?
            .with_input_fact(1, InferenceFact::dt_shape(i64::datum_type(), tvec!(1)))?
            .with_input_fact(
                2,
                InferenceFact::dt_shape(
                    f32::datum_type(),
                    tvec!(1, CACHE_LAYERS, CACHE_LEFT, CACHE_D_MODEL),
                ),
            )?
            .with_input_fact(
                3,
                InferenceFact::dt_shape(
                    f32::datum_type(),
                    tvec!(1, CACHE_LAYERS, CACHE_D_MODEL, CACHE_TIME),
                ),
            )?
            .with_input_fact(4, InferenceFact::dt_shape(i64::datum_type(), tvec!(1)))?
            .into_typed()
            .context("type-check Rizeh ONNX graph")?;
        let outputs = resolve_outputs(&output_names(&typed))?;
        let model = typed
            .into_optimized()
            .context("optimize Rizeh ONNX graph")?
            .into_runnable()
            .context("prepare Rizeh inference graph")?;

        Ok(Self {
            model,
            outputs,
            features,
            tokens,
            cache_channel: Tensor::zero::<f32>(&[1, CACHE_LAYERS, CACHE_LEFT, CACHE_D_MODEL])?,
            cache_time: Tensor::zero::<f32>(&[1, CACHE_LAYERS, CACHE_D_MODEL, CACHE_TIME])?,
            cache_channel_len: Tensor::from_shape(&[1usize], &[0i64])?,
            pending: Vec::with_capacity(SAMPLE_RATE * 2),
            base_frame: 0,
            chunk_index: 0,
            previous_token: -1,
            last_emitted_token: -1,
            frames_since_emit: 999,
            previous_chunk_had_emit: true,
            transcript: String::new(),
        })
    }

    fn chunk_start(&self, index: usize) -> usize {
        if index == 0 {
            0
        } else {
            (FIRST_CHUNK_FRAMES - PRE_ENCODE_OVERLAP) + (index - 1) * SHIFT_FRAMES
        }
    }

    fn accept(&mut self, pcm: &[f32]) -> Result<&str> {
        self.pending.extend_from_slice(pcm);
        self.drain(false)?;
        Ok(&self.transcript)
    }

    fn finish(&mut self) -> Result<&str> {
        self.pending
            .extend(std::iter::repeat(0.0).take(FINAL_PAD_SAMPLES));
        self.drain(true)?;
        Ok(&self.transcript)
    }

    fn drain(&mut self, flush: bool) -> Result<()> {
        let mut fired = 0usize;
        while fired < 16 {
            let start = self.chunk_start(self.chunk_index);
            let target = if self.chunk_index == 0 {
                FIRST_CHUNK_FRAMES
            } else {
                INPUT_FRAMES
            };
            let available = self.base_frame + self.pending.len() / HOP + 1;
            if available < start + target && !flush {
                break;
            }
            if available <= start {
                break;
            }
            let valid_frames = target.min(available - start);
            if valid_frames == 0 {
                break;
            }
            let feature = self.make_chunk(start)?;
            self.run_step(&feature, valid_frames)?;
            self.chunk_index += 1;
            fired += 1;
            self.trim_buffer();
        }
        Ok(())
    }

    fn make_chunk(&self, absolute_start: usize) -> Result<Vec<f32>> {
        let relative_start = absolute_start
            .checked_sub(self.base_frame)
            .context("streaming window moved behind retained PCM")?;
        let stride = relative_start + INPUT_FRAMES + 4;
        let (computed, available) = self.features.compute(&self.pending, stride);
        let mut output = vec![0.0f32; MEL_BINS * INPUT_FRAMES];
        for mel in 0..MEL_BINS {
            for frame in 0..INPUT_FRAMES {
                let source_frame = relative_start + frame;
                if source_frame < available {
                    output[mel * INPUT_FRAMES + frame] = computed[mel * stride + source_frame];
                }
            }
        }
        Ok(output)
    }

    fn run_step(&mut self, feature: &[f32], valid_frames: usize) -> Result<()> {
        let audio = Tensor::from_shape(&[1usize, MEL_BINS, INPUT_FRAMES], feature)?;
        let length = Tensor::from_shape(&[1usize], &[valid_frames as i64])?;
        let inputs = tvec!(
            audio.into(),
            length.into(),
            self.cache_channel.clone().into(),
            self.cache_time.clone().into(),
            self.cache_channel_len.clone().into()
        );
        let output = self.model.run(inputs).context("Rizeh streaming inference failed")?;

        let logits = output[self.outputs.ctc].cast_to::<f32>()?.into_owned();
        let shape = logits.shape().to_vec();
        ensure!(
            shape.len() == 3 && shape[0] == 1 && shape[2] == VOCAB,
            "unexpected Rizeh CTC output shape: {:?}",
            shape
        );
        let view = logits.to_plain_array_view::<f32>()?;
        let valid_outputs = match self.outputs.encoded_len {
            Some(index) => {
                let value = output[index].cast_to::<i64>()?.into_owned();
                let array = value.to_plain_array_view::<i64>()?;
                (array.iter().next().copied().unwrap_or(shape[1] as i64).max(0) as usize)
                    .min(shape[1])
            }
            None => shape[1],
        };

        let mut chunk_had_emit = false;
        for frame in 0..valid_outputs {
            let mut best = 0usize;
            let mut best_score = f32::NEG_INFINITY;
            for token in 0..VOCAB {
                let score = view[[0, frame, token]];
                if score > best_score {
                    best_score = score;
                    best = token;
                }
            }
            self.frames_since_emit += 1;
            if best != BLANK_ID && best as i64 != self.previous_token {
                if best as i64 == self.last_emitted_token
                    && self.frames_since_emit <= DEDUP_GAP
                    && !self.previous_chunk_had_emit
                {
                    self.frames_since_emit = 0;
                } else {
                    self.append_token(best)?;
                    self.last_emitted_token = best as i64;
                    self.frames_since_emit = 0;
                    chunk_had_emit = true;
                }
            }
            self.previous_token = best as i64;
        }
        self.previous_chunk_had_emit = chunk_had_emit;

        self.cache_channel = output[self.outputs.next_channel].clone().into_tensor();
        self.cache_time = output[self.outputs.next_time].clone().into_tensor();
        self.cache_channel_len =
            output[self.outputs.next_channel_len].cast_to::<i64>()?.into_owned();
        Ok(())
    }

    fn append_token(&mut self, id: usize) -> Result<()> {
        let token = self.tokens.get(id).context("decoded token ID out of range")?;
        if token.is_empty() || (token.starts_with('<') && token.ends_with('>')) {
            return Ok(());
        }
        let piece = token.strip_prefix('▁').unwrap_or(token);
        if token.starts_with('▁') && !self.transcript.is_empty() && !self.transcript.ends_with(' ') {
            self.transcript.push(' ');
        }
        self.transcript.push_str(piece);
        Ok(())
    }

    fn trim_buffer(&mut self) {
        let next_start = self.chunk_start(self.chunk_index);
        let keep_from = next_start.saturating_sub(LEFT_CONTEXT);
        let drop_frames = keep_from.saturating_sub(self.base_frame);
        if drop_frames > 0 {
            let drop_samples = (drop_frames * HOP).min(self.pending.len());
            self.pending.drain(0..drop_samples);
            self.base_frame += drop_samples / HOP;
        }
    }
}

fn read_string(env: &mut JNIEnv, value: JString) -> Result<String> {
    Ok(env.get_string(&value)?.into())
}

fn throw_java(env: &mut JNIEnv, message: &str) {
    let _ = env.throw_new("java/lang/IllegalStateException", message);
}

fn to_java_string(env: &mut JNIEnv, value: &str) -> jstring {
    match env.new_string(value) {
        Ok(string) => string.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_mindunlock_app_RizehNative_nativeCreate(
    mut env: JNIEnv,
    _class: JClass,
    model_path: JString,
    tokens_path: JString,
    mel_path: JString,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<RizehSession> {
        let model = read_string(&mut env, model_path)?;
        let tokens = read_string(&mut env, tokens_path)?;
        let mel = read_string(&mut env, mel_path)?;
        RizehSession::new(&model, &tokens, &mel)
    }));
    match result {
        Ok(Ok(session)) => Box::into_raw(Box::new(session)) as jlong,
        Ok(Err(error)) => {
            throw_java(&mut env, &format!("راه‌اندازی موتور Shenava Rizeh ناموفق بود: {:#}", error));
            0
        }
        Err(_) => {
            throw_java(&mut env, "موتور بومی تشخیص گفتار هنگام راه‌اندازی با خطای داخلی متوقف شد.");
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_mindunlock_app_RizehNative_nativePush(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    samples: JShortArray,
    count: jint,
) -> jstring {
    if handle == 0 {
        throw_java(&mut env, "نشست تشخیص گفتار معتبر نیست.");
        return std::ptr::null_mut();
    }
    if count <= 0 {
        return to_java_string(&mut env, "");
    }
    let count = count as usize;
    let array_len = match env.get_array_length(&samples) {
        Ok(length) => length as usize,
        Err(error) => {
            throw_java(&mut env, &format!("خواندن بافر صدا ناموفق بود: {}", error));
            return std::ptr::null_mut();
        }
    };
    if count > array_len {
        throw_java(&mut env, "طول قطعه صوتی نامعتبر است.");
        return std::ptr::null_mut();
    }
    let mut input = vec![0i16; count];
    if let Err(error) = env.get_short_array_region(&samples, 0, &mut input) {
        throw_java(&mut env, &format!("دریافت نمونه‌های صوتی ناموفق بود: {}", error));
        return std::ptr::null_mut();
    }
    let pcm: Vec<f32> = input
        .into_iter()
        .map(|sample| sample as f32 / 32768.0)
        .collect();
    let session = unsafe { (handle as *mut RizehSession).as_mut() };
    let Some(session) = session else {
        throw_java(&mut env, "نشست تشخیص گفتار آزاد شده است.");
        return std::ptr::null_mut();
    };
    let result = catch_unwind(AssertUnwindSafe(|| session.accept(&pcm).map(str::to_owned)));
    match result {
        Ok(Ok(text)) => to_java_string(&mut env, &text),
        Ok(Err(error)) => {
            throw_java(&mut env, &format!("پردازش صوت ناموفق بود: {:#}", error));
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_java(&mut env, "موتور بومی هنگام پردازش صوت با خطای داخلی متوقف شد.");
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_mindunlock_app_RizehNative_nativeFinish(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    if handle == 0 {
        throw_java(&mut env, "نشست تشخیص گفتار معتبر نیست.");
        return std::ptr::null_mut();
    }
    let session = unsafe { (handle as *mut RizehSession).as_mut() };
    let Some(session) = session else {
        throw_java(&mut env, "نشست تشخیص گفتار آزاد شده است.");
        return std::ptr::null_mut();
    };
    let result = catch_unwind(AssertUnwindSafe(|| session.finish().map(str::to_owned)));
    match result {
        Ok(Ok(text)) => to_java_string(&mut env, &text),
        Ok(Err(error)) => {
            throw_java(&mut env, &format!("تکمیل تشخیص گفتار ناموفق بود: {:#}", error));
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_java(&mut env, "موتور بومی هنگام نهایی‌سازی گفتار با خطای داخلی متوقف شد.");
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_mindunlock_app_RizehNative_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        let _ = catch_unwind(AssertUnwindSafe(|| unsafe {
            drop(Box::from_raw(handle as *mut RizehSession));
        }));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rizeh_chunk_geometry_is_the_80ms_stream_contract() {
        assert_eq!(FIRST_CHUNK_FRAMES, 9);
        assert_eq!(INPUT_FRAMES, 25);
        assert_eq!(SHIFT_FRAMES, 16);
        assert_eq!(INPUT_FRAMES - SHIFT_FRAMES, PRE_ENCODE_OVERLAP);
        assert_eq!(CACHE_LAYERS, 16);
        assert_eq!(CACHE_D_MODEL, 256);
    }

    #[test]
    fn reflect_padding_excludes_the_edge_sample() {
        assert_eq!(reflect_sample(&[1.0, 2.0, 3.0], -1), 2.0);
        assert_eq!(reflect_sample(&[1.0, 2.0, 3.0], 3), 2.0);
        assert_eq!(reflect_sample(&[4.0], -10), 4.0);
    }
}
