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
import com.mindunlock.app.data.remote.GitHubRepositorySettingsStore

@Composable
fun GitHubRepositorySettingsCard(onSaved: () -> Unit) {
    val context = LocalContext.current
    val initial = remember(context) { GitHubRepositorySettingsStore.read(context) }
    var owner by remember { mutableStateOf(initial.owner) }
    var repository by remember { mutableStateOf(initial.repository) }
    var repositoryReference by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf(initial.branch) }
    var manifest by remember { mutableStateOf(initial.manifestPath) }
    var message by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("تنظیم دستی منبع GitHub", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(repositoryReference, { repositoryReference = it; message = "" }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("نشانی مخزن یا owner/repository (اختیاری)") })
            Text("نمونه: https://github.com/MrUtility01/MindUnlock", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(owner, { owner = it; message = "" }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("مالک / Organization") })
            OutlinedTextField(repository, { repository = it; message = "" }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام مخزن") })
            OutlinedTextField(branch, { branch = it; message = "" }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("شاخه (Branch)") })
            OutlinedTextField(manifest, { manifest = it; message = "" }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("مسیر Manifest") })
            Button(onClick = {
                try {
                    val parsed = repositoryReference.trim().takeIf { it.isNotBlank() }?.let { GitHubRepositorySettingsStore.parseRepositoryReference(it) }
                    GitHubRepositorySettingsStore.save(context, parsed?.first ?: owner, parsed?.second ?: repository, branch, manifest)
                    message = "تنظیمات ذخیره شد."
                    onSaved()
                } catch (failure: Exception) {
                    message = failure.message ?: "ذخیره تنظیمات ناموفق بود."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره و اتصال مجدد") }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
