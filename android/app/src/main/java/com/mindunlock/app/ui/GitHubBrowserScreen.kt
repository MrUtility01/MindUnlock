package com.mindunlock.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.remote.GitHubDeviceAuth
import com.mindunlock.app.data.remote.GitHubPublicEntry
import com.mindunlock.app.data.remote.GitHubPublicFile
import com.mindunlock.app.data.remote.GitHubPublicRepository
import com.mindunlock.app.data.remote.GitHubRepositorySettingsStore
import com.mindunlock.app.data.remote.GitHubWriteRepository
import kotlinx.coroutines.launch

@Composable
fun GitHubBrowserScreen() {
    val context = LocalContext.current
    var repository by remember(context) { mutableStateOf(GitHubPublicRepository.configured(context)) }
    val writer = remember { GitHubWriteRepository(GitHubDeviceAuth()) }
    var settingsVersion by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<GitHubPublicEntry>>(emptyList()) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var preview by remember { mutableStateOf<GitHubPublicFile?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var editorPath by remember { mutableStateOf("") }
    var editorText by remember { mutableStateOf("") }
    var editorTitle by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editorIsNew by remember { mutableStateOf(false) }
    var operationStatus by remember { mutableStateOf("") }
    var pullRequestUrl by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

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
            } finally { loading = false }
        }
    }
    fun refreshBranches() {
        scope.launch {
            try { branches = repository.listBranches() }
            catch (failure: Exception) { error = failure.message ?: "دریافت شاخه‌ها ناموفق بود." }
        }
    }
    fun openEditor(file: GitHubPublicFile, isNew: Boolean = false) {
        editorPath = file.path
        editorText = file.text
        editorTitle = if (isNew) "ایجاد ${file.path}" else "ویرایش ${file.path}"
        editorIsNew = isNew
        editorOpen = true
        pullRequestUrl = null
        operationStatus = ""
    }
    LaunchedEffect(Unit) { load(""); refreshBranches() }
    val visible = entries.filter { it.name.contains(filter.trim(), ignoreCase = true) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("مدیریت مخزن GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("مرور، جست‌وجوی سراسری، انتخاب شاخه، ایجاد، ویرایش و حذف فایل با Pull Request.", style = MaterialTheme.typography.bodySmall)
        }
        item { GitHubAuthCard() }
        item { GitHubRepositorySettingsCard(onSaved = {
            repository = GitHubPublicRepository.configured(context)
            settingsVersion++
            load("")
            refreshBranches()
        }) }
        item { SpeechMirrorSettingsCard() }
        item { key(settingsVersion) { GitHubContentSyncCard() } }
        item {
            Text("شاخه فعال: ${GitHubRepositorySettingsStore.read(context).branch}", fontWeight = FontWeight.SemiBold)
            if (branches.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    branches.forEach { branchName ->
                        OutlinedButton(onClick = {
                            val settings = GitHubRepositorySettingsStore.read(context)
                            runCatching {
                                GitHubRepositorySettingsStore.save(context, settings.owner, settings.repository, branchName, settings.manifestPath)
                            }.onSuccess {
                                repository = GitHubPublicRepository(settings.owner, settings.repository, branchName)
                                load("")
                            }.onFailure { error = it.message ?: "تغییر شاخه ناموفق بود." }
                        }) { Text(branchName) }
                    }
                }
            }
            OutlinedButton(onClick = { refreshBranches() }) { Text("به‌روزرسانی شاخه‌ها") }
        }
        item {
            Text("همه تغییرات نوشتنی در شاخه جداگانه ثبت می‌شوند؛ شاخه فعال مستقیماً تغییر نمی‌کند و برای اعمال تغییرات باید Pull Request بازبینی و ادغام شود.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = currentPath.isNotBlank() && !loading, onClick = { load(currentPath.substringBeforeLast('/', "")) }) { Text("پوشه بالاتر") }
                OutlinedButton(enabled = !loading, onClick = { load(currentPath) }) { Text("تازه‌سازی") }
                OutlinedButton(enabled = !loading, onClick = {
                    editorPath = if (currentPath.isBlank()) "" else "$currentPath/"
                    editorText = ""
                    editorTitle = "ایجاد فایل جدید"
                    editorIsNew = true
                    editorOpen = true
                    preview = null
                    pullRequestUrl = null
                }) { Text("فایل جدید") }
            }
        }
        item { Text("مسیر: /$currentPath", style = MaterialTheme.typography.labelMedium) }
        item {
            OutlinedTextField(value = filter, onValueChange = { filter = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام فایل یا عبارت جست‌وجوی سراسری") })
            OutlinedButton(enabled = filter.isNotBlank() && !loading, onClick = {
                scope.launch {
                    loading = true
                    error = ""
                    preview = null
                    try {
                        entries = repository.searchAllFiles(filter)
                        currentPath = ""
                        operationStatus = "نتایج جست‌وجوی کل مخزن: ${entries.size} فایل"
                    } catch (failure: Exception) { error = failure.message ?: "جست‌وجوی سراسری ناموفق بود." }
                    finally { loading = false }
                }
            }) { Text("جست‌وجوی کل مخزن") }
        }
        if (loading) { item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(); Text("در حال دریافت از GitHub…") } } }
        if (error.isNotBlank()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { load(currentPath) }) { Text("تلاش دوباره") }
                    }
                }
            }
        }
        if (editorOpen) {
            item(key = "github-editor") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (editorIsNew) "ایجاد فایل در مخزن" else "ویرایش فایل", fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = editorPath, onValueChange = { editorPath = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("مسیر فایل در مخزن") })
                        OutlinedTextField(value = editorTitle, onValueChange = { editorTitle = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("عنوان Pull Request") })
                        OutlinedTextField(value = editorText, onValueChange = { editorText = it }, modifier = Modifier.fillMaxWidth(), minLines = 8, maxLines = 16, label = { Text("محتوای فایل UTF-8 (حداکثر ۲۰ مگابایت)") })
                        Text("اندازه فعلی: ${editorText.toByteArray(Charsets.UTF_8).size} بایت", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !saving && editorPath.isNotBlank() && editorText.isNotBlank(), onClick = {
                                scope.launch {
                                    saving = true
                                    operationStatus = "در حال ثبت تغییر و ساخت Pull Request…"
                                    pullRequestUrl = null
                                    try {
                                        val result = writer.submitManagedFile(context, editorPath, editorText, editorTitle)
                                        operationStatus = "تغییر ثبت شد؛ Pull Request شماره ${result.pullRequestNumber} ساخته شد."
                                        pullRequestUrl = result.pullRequestUrl
                                        editorOpen = false
                                        load(currentPath)
                                    } catch (failure: Exception) { operationStatus = failure.message ?: "ثبت تغییر ناموفق بود." }
                                    finally { saving = false }
                                }
                            }) { Text(if (saving) "در حال ثبت…" else "ثبت و ساخت Pull Request") }
                            OutlinedButton(enabled = !saving, onClick = { editorOpen = false }) { Text("انصراف") }
                            if (!editorIsNew) {
                                OutlinedButton(enabled = !saving, onClick = {
                                    scope.launch {
                                        saving = true
                                        operationStatus = "در حال ساخت Pull Request برای حذف فایل…"
                                        pullRequestUrl = null
                                        try {
                                            val result = writer.submitManagedFile(context, editorPath, null, "حذف $editorPath")
                                            operationStatus = "درخواست حذف در Pull Request شماره ${result.pullRequestNumber} ثبت شد."
                                            pullRequestUrl = result.pullRequestUrl
                                            editorOpen = false
                                            load(currentPath)
                                        } catch (failure: Exception) { operationStatus = failure.message ?: "ثبت درخواست حذف ناموفق بود." }
                                        finally { saving = false }
                                    }
                                }) { Text("درخواست حذف") }
                            }
                        }
                    }
                }
            }
        }
        if (operationStatus.isNotBlank()) {
            item {
                Text(operationStatus, style = MaterialTheme.typography.bodySmall)
                pullRequestUrl?.let { url ->
                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) { Text("باز کردن Pull Request") }
                }
            }
        }
        preview?.let { file ->
            item(key = "preview:${file.path}") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("پیش‌نمایش فایل", fontWeight = FontWeight.Bold)
                        Text(file.path, style = MaterialTheme.typography.labelSmall)
                        Text("اندازه: ${file.size} بایت · SHA: ${file.sha.take(12)}…", style = MaterialTheme.typography.labelSmall)
                        HorizontalDivider()
                        Text(file.text, style = MaterialTheme.typography.bodySmall, maxLines = 18, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { openEditor(file) }) { Text("ویرایش فایل") }
                            OutlinedButton(onClick = { preview = null }) { Text("بستن") }
                        }
                    }
                }
            }
        }
        item { Text("${visible.size} مورد", style = MaterialTheme.typography.labelSmall) }
        items(visible, key = { it.path }) { entry ->
            Card(Modifier.fillMaxWidth().clickable(enabled = !loading) {
                if (entry.isDirectory) load(entry.path)
                else scope.launch {
                    loading = true
                    error = ""
                    try { preview = repository.readTextFile(entry.path) }
                    catch (failure: Exception) { error = failure.message ?: "خواندن فایل ناموفق بود." }
                    finally { loading = false }
                }
            }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (entry.isDirectory) "📁" else "📄")
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.name, fontWeight = FontWeight.SemiBold)
                        Text(if (entry.isDirectory) "پوشه" else "${entry.size} بایت · ${entry.path}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (entry.isDirectory) "باز کردن" else "پیش‌نمایش", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (visible.isEmpty() && !loading && error.isBlank()) item { Text("در این مسیر موردی پیدا نشد.", modifier = Modifier.padding(12.dp)) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
