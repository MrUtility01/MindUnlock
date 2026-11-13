package com.mindunlock.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.local.MindMapNodeEntity
import com.mindunlock.app.data.local.UserDatabase
import kotlinx.coroutines.launch

@Composable
fun MindMapScreen(userDb: UserDatabase) {
    val nodes by userDb.mindMapDao().observeNodes().collectAsState(initial = emptyList())
    val edges by userDb.mindMapDao().observeEdges().collectAsState(initial = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var showNodeDialog by rememberSaveable { mutableStateOf(false) }
    var showEdgeDialog by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var nodeTitle by rememberSaveable { mutableStateOf("") }
    var nodeDescription by rememberSaveable { mutableStateOf("") }
    var nodeType by rememberSaveable { mutableStateOf("ایده") }
    var selectedSource by rememberSaveable { mutableStateOf(0L) }
    var selectedTarget by rememberSaveable { mutableStateOf(0L) }
    var relationType by rememberSaveable { mutableStateOf("مرتبط با") }
    var sourceMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }
    val visibleNodes = remember(nodes, query) {
        val q = query.trim()
        if (q.isBlank()) nodes else nodes.filter {
            it.title.contains(q, true) || it.description.contains(q, true) || it.nodeType.contains(q, true)
        }
    }
    val names = remember(nodes) { nodes.associate { it.id to it.title } }
    val relatedByNode = remember(edges) {
        buildMap<Long, MutableList<com.mindunlock.app.data.local.MindMapEdgeEntity>> {
            edges.forEach { edge ->
                getOrPut(edge.sourceNodeId) { mutableListOf() }.add(edge)
                getOrPut(edge.targetNodeId) { mutableListOf() }.add(edge)
            }
        }
    }

    fun edit(node: MindMapNodeEntity? = null) {
        editingId = node?.id
        nodeTitle = node?.title.orEmpty()
        nodeDescription = node?.description.orEmpty()
        nodeType = node?.nodeType ?: "ایده"
        showNodeDialog = true
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("نقشه ذهنی و گراف دانش", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("ایده‌ها، یادداشت‌ها، پروژه‌ها و موضوعات یادگیری را به گره تبدیل کن و ارتباط معنایی میان آن‌ها بساز. داده‌ها به‌صورت محلی ذخیره می‌شوند.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { edit() }, modifier = Modifier.weight(1f)) { Text("گره جدید") }
            OutlinedButton(
                onClick = {
                    selectedSource = nodes.first().id
                    selectedTarget = nodes[1].id
                    relationType = "مرتبط با"
                    showEdgeDialog = true
                },
                enabled = nodes.size >= 2,
                modifier = Modifier.weight(1f)
            ) { Text("ایجاد ارتباط") }
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("جست‌وجوی گره‌ها") }, singleLine = true)
        Text("گره‌ها: ${nodes.size}  ·  ارتباط‌ها: ${edges.size}", style = MaterialTheme.typography.labelLarge)
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        if (nodes.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نقشه هنوز خالی است.", fontWeight = FontWeight.SemiBold)
                    Text("با ساخت چند گره و اتصال آن‌ها، شبکه دانش شخصی خودت را شکل بده.")
                }
            }
        } else if (visibleNodes.isEmpty()) Text("گرهی با این عبارت پیدا نشد.")

        visibleNodes.forEach { node ->
            val related = relatedByNode[node.id].orEmpty()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(node.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(node.nodeType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("#${node.id}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (node.description.isNotBlank()) Text(node.description)
                    Text("ارتباط‌ها: ${related.size}", style = MaterialTheme.typography.labelMedium)
                    related.forEach { edge ->
                        val otherId = if (edge.sourceNodeId == node.id) edge.targetNodeId else edge.sourceNodeId
                        val direction = if (edge.sourceNodeId == node.id) "به" else "از"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• ${direction} «${names[otherId] ?: "گره حذف‌شده"}» — ${edge.relationType}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { scope.launch { userDb.mindMapDao().deleteEdge(edge.id); status = "ارتباط حذف شد." } }) { Text("حذف") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { edit(node) }) { Text("ویرایش") }
                        TextButton(onClick = { pendingDeleteId = node.id }) { Text("حذف") }
                    }
                }
            }
        }
    }

    if (pendingDeleteId != null) {
        val deletingNode = nodes.firstOrNull { it.id == pendingDeleteId }
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("حذف گره؟") },
            text = { Text("«${deletingNode?.title ?: "این گره"}» و تمام ارتباط‌های متصل به آن حذف می‌شود. این عمل قابل بازگشت مستقیم نیست.") },
            confirmButton = {
                Button(onClick = {
                    val id = pendingDeleteId
                    pendingDeleteId = null
                    if (id != null) scope.launch {
                        userDb.mindMapDao().deleteNode(id)
                        status = "گره و ارتباط‌های وابسته حذف شدند."
                    }
                }) { Text("حذف قطعی") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("انصراف") } }
        )
    }

    if (showNodeDialog) AlertDialog(
        onDismissRequest = { showNodeDialog = false },
        title = { Text(if (editingId == null) "ساخت گره" else "ویرایش گره") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nodeTitle, { nodeTitle = it.take(200) }, label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(nodeDescription, { nodeDescription = it.take(4000) }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(nodeType, { nodeType = it.take(40) }, label = { Text("نوع گره؛ ایده، یادداشت، پروژه، یادگیری...") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = nodeTitle.isNotBlank() && nodeType.isNotBlank(), onClick = {
                scope.launch {
                    val now = System.currentTimeMillis()
                    val id = editingId
                    if (id == null) {
                        userDb.mindMapDao().insertNode(MindMapNodeEntity(title = nodeTitle.trim(), description = nodeDescription.trim(), nodeType = nodeType.trim(), createdAt = now, updatedAt = now))
                    } else {
                        nodes.firstOrNull { it.id == id }?.let {
                            userDb.mindMapDao().updateNode(it.copy(title = nodeTitle.trim(), description = nodeDescription.trim(), nodeType = nodeType.trim(), updatedAt = now))
                        }
                    }
                    status = "گره ذخیره شد."
                    showNodeDialog = false
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = { showNodeDialog = false }) { Text("انصراف") } }
    )

    if (showEdgeDialog) AlertDialog(
        onDismissRequest = { showEdgeDialog = false },
        title = { Text("ایجاد ارتباط معنایی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { sourceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("مبدأ: ${names[selectedSource] ?: "انتخاب گره"}")
                    }
                    DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                        nodes.forEach { n -> DropdownMenuItem(text = { Text(n.title) }, onClick = { selectedSource = n.id; sourceMenu = false }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("مقصد: ${names[selectedTarget] ?: "انتخاب گره"}")
                    }
                    DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                        nodes.forEach { n -> DropdownMenuItem(text = { Text(n.title) }, onClick = { selectedTarget = n.id; targetMenu = false }) }
                    }
                }
                OutlinedTextField(relationType, { relationType = it.take(80) }, label = { Text("نوع ارتباط؛ مانند پیش‌نیاز یا مرتبط با") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = selectedSource > 0 && selectedTarget > 0 && selectedSource != selectedTarget, onClick = {
                scope.launch {
                    val added = userDb.mindMapDao().connect(selectedSource, selectedTarget, relationType)
                    status = if (added) "ارتباط ذخیره شد." else "این ارتباط نامعتبر است یا قبلاً ثبت شده است."
                    showEdgeDialog = false
                }
            }) { Text("ثبت ارتباط") }
        },
        dismissButton = { TextButton(onClick = { showEdgeDialog = false }) { Text("انصراف") } }
    )
}
