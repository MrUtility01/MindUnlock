# MindUnlock — Shenava Rizeh offline dictation

This module provides streaming Persian speech-to-text on Android ARM64 devices.

## Runtime guarantees

- Audio recognition runs locally through the Rust tract-onnx runtime.
- The Android application does not download ASR weights, tokens, or feature assets at runtime.
- The CI workflow fetches the model at a pinned Hugging Face revision, verifies its SHA-256, and puts it in the APK assets.
- The native code has no HTTP client or network path.
- Microphone samples are consumed in 2,048-sample PCM16 chunks. Audio is not accumulated as a complete recording.
- The encoder cache is retained for the active session; after each encoder step, obsolete PCM is trimmed while retaining left feature context.
- A dictation session has no hard 45-second timeout. The user stops it explicitly.
- The transcript itself necessarily grows with the amount of dictated text.

The application's general Internet permission remains because other MindUnlock features use networking. That permission is not used by this ASR module.

## Model provenance

- Repository: Reza2kn/Shenava-Rizeh-v1.0-tract-streaming
- Revision: a13172bd81cbed1319b92034f4703731334a6340
- Variant: model.int4.onnx (approximately 40 MB)
- SHA-256: 2cbebb39be531e04f0f31b15f7053365ac9941080c7f7ad5c98d6e0aa7638770
- License: Apache-2.0; its LICENSE is bundled with the APK assets.
- Runtime: tract-onnx 0.23.10.

The binary model is fetched during CI instead of stored as a large Git blob. It is nevertheless packaged into assets/rizeh/ in the generated APK and is available from first launch without an Internet connection.

## Streaming contract

The implementation follows the current Rizeh configuration in the model author's VisualEars streaming runtime:

- Mono audio at 16 kHz.
- NeMo-compatible 80-bin Slaney log-mel features.
- Encoder input width: 25 mel frames.
- Priming chunk: 9 frames.
- Advance per step: 16 frames, with 9-frame pre-encoder overlap.
- Encoder caches: [1,16,70,256], [1,16,256,8], plus cache length [1].
- CTC vocabulary size 1,025; blank token ID 1,024.

The old model-card note that shows a 121-frame window is not used for this Rizeh 80 ms stream path; in the current runtime registry the 121/105/112 configuration is the separate Koochik-HD profile.

## Persian text normalization

The Kotlin display layer performs conservative Persian normalization:
- Arabic ي / ك and Arabic-Indic digits are normalized to Persian forms.
- Spoken Persian number words (units, tens, hundreds, thousands, millions and billions) are converted to Persian digits.
- A small exact phrase dictionary handles frequent infrastructure terms such as MikroTik, FortiGate, Issabel, Asterisk, DHCP, DNS, VLAN and Active Directory.
- Dots in IP addresses and command/version strings are preserved.

## Build

The GitHub Actions Android workflow installs Rust stable and Android NDK 27.2, builds libmindunlock_asr.so for arm64-v8a, downloads and verifies the model assets, then assembles the APK.

This source change is not a substitute for a successful CI build or device test. Real-device CPU, memory, battery, and recognition-quality measurements must be gathered on the target phone before making performance claims.
