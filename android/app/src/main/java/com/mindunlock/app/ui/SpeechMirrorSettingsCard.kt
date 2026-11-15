package com.mindunlock.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mindunlock.app.SpeechDownloadMirrors
import com.mindunlock.app.SpeechMirrorSource

@Composable
fun SpeechMirrorSettingsCard() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("mindunlock_preferences", android.content.Context.MODE_PRIVATE) }
    var hfMirrors by remember(context) { mutableStateOf(preferences.getString(SpeechDownloadMirrors.PREF_HF_MIRRORS, null) ?: SpeechDownloadMirrors.defaultEditableText(SpeechMirrorSource.HUGGING_FACE)) }
    var githubMirrors by remember(context) { mutableStateOf(preferences.getString(SpeechDownloadMirrors.PREF_GITHUB_MIRRORS, null) ?: SpeechDownloadMirrors.defaultEditableText(SpeechMirrorSource.GITHUB_RELEASE)) }
    var message by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("میرورهای دریافت مدل گفتار", style = MaterialTheme.typography.titleMedium)
            Text("هر آدرس را در یک خط وارد کن. برای پراکسی GitHub می‌توانی از {url} یا {encodedUrl} استفاده کنی. برنامه فقط HTTPS را می‌پذیرد و پس از میرورها، نشانی اصلی را نیز امتحان می‌کند.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(hfMirrors, { hfMirrors = it; message = "" }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("میرورهای Hugging Face") })
            OutlinedTextField(githubMirrors, { githubMirrors = it; message = "" }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("میرورهای GitHub Release") })
            Button(onClick = {
                preferences.edit()
                    .putString(SpeechDownloadMirrors.PREF_HF_MIRRORS, hfMirrors)
                    .putString(SpeechDownloadMirrors.PREF_GITHUB_MIRRORS, githubMirrors)
                    .apply()
                message = "میرورها ذخیره شدند. دریافت بعدی با تنظیمات جدید انجام می‌شود."
            }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره میرورها") }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
