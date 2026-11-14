package com.mindunlock.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.remote.GitHubPublicEntry
import com.mindunlock.app.data.remote.GitHubPublicFile
import com.mindunlock.app.data.remote.GitHubPublicRepository
import kotlinx.coroutines.launch

@Composable
fun GitHubBrowserScreen() {
    val repository = remember { GitHubPublicRepository() }
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<GitHubPublicEntry>>(emptyList()) }
    var preview by remember { mutableStateOf<GitHubPublicFile?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }

    fun load(path: String) {
        scope.launch {
            loading = true
            error = ""
            preview = null
            try {
                entries = repository.list(path)
                currentPath = path
            } catch (failure: Exception) {
                error = failure.message ?: "دریافت فهرست GitHub ناموفق بود."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load("") }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("مخزن عمومی MindUnlock", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "MrUtility01/MindUnlock · شاخه main · فقط خواندن عمومی",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "برای مرور و پیش‌نمایش فایل‌ها نیازی به ورود نیست. ارسال فایل و همگام‌سازی نوشتنی در مرحله احراز هویت GitHub App اضافه می‌شود.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = currentPath.isNotBlank() && !loading,
                onClick = {
                    val parent = currentPath.substringBeforeLast('/', "")
                    load(parent)
                }
            ) { Text("پوشه بالاتر") }
            OutlinedButton(enabled = !loading, onClick = { load(currentPath) }) { Text("تازه‌سازی") }
        }
        Text("مسیر: /" + currentPath, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("جست‌وجو در نام فایل‌ها") }
        )

        if (loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator()
                Text("در حال دریافت از GitHub…")
            }
        }
        if (error.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { load(currentPath) }) { Text("تلاش دوباره") }
                }
            }
        }

        preview?.let { file ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پیش‌نمایش فایل", fontWeight = FontWeight.Bold)
                    Text(file.path, style = MaterialTheme.typography.labelSmall)
                    Text("اندازه: ${file.size} بایت · SHA: ${file.sha.take(12)}…", style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider()
                    Text(
                        file.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 18,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("پیش‌نمایش محدود به ۱۸ خط نمایشی است؛ محتوای اصلی در مخزن تغییر نمی‌کند.", style = MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick = { preview = null }) { Text("بستن پیش‌نمایش") }
                }
            }
        }

        val visible = entries.filter { it.name.contains(filter.trim(), ignoreCase = true) }
        Text("${visible.size} مورد", style = MaterialTheme.typography.labelSmall)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(visible, key = { it.path }) { entry ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !loading) {
                    if (entry.isDirectory) load(entry.path)
                    else {
                        scope.launch {
                            loading = true
                            error = ""
                            try {
                                preview = repository.readTextFile(entry.path)
                            } catch (failure: Exception) {
                                error = failure.message ?: "خواندن فایل ناموفق بود."
                            } finally {
                                loading = false
                            }
                        }
                    }
                }) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(if (entry.isDirectory) "📁" else "📄")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (entry.isDirectory) "پوشه" else "${entry.size} بایت · ${entry.path}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(if (entry.isDirectory) "باز کردن" else "پیش‌نمایش", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (visible.isEmpty() && !loading && error.isBlank()) {
                item { Text("در این مسیر موردی پیدا نشد.", modifier = Modifier.padding(12.dp)) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
