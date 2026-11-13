package com.mindunlock.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindunlock.app.data.local.MindMapNodeEntity
import com.mindunlock.app.data.local.MindMapSourceItem
import com.mindunlock.app.data.local.UserDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MindMapScreen(userDb: UserDatabase) {
    val nodes by userDb.mindMapDao().observeNodes().collectAsState(initial = emptyList())
    val edges by userDb.mindMapDao().observeEdges().collectAsState(initial = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGraphNodeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var graphScale by rememberSaveable { mutableStateOf(1f) }
    var graphPanX by rememberSaveable { mutableStateOf(0f) }
    var graphPanY by rememberSaveable { mutableStateOf(0f) }
    var status by rememberSaveable { mutableStateOf("") }
    var showNodeDialog by rememberSaveable { mutableStateOf(false) }
    var showEdgeDialog by rememberSaveable { mutableStateOf(false) }
    var showSourceDialog by rememberSaveable { mutableStateOf(false) }
    var sourceKind by rememberSaveable { mutableStateOf("mind_dump") }
    var sourceKindMenu by remember { mutableStateOf(false) }
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
    var sourceSearch by rememberSaveable { mutableStateOf("") }
    var sourceChoices by remember { mutableStateOf<List<MindMapSourceItem>>(emptyList()) }
    val sourceKindLabel = when (sourceKind) {
        "mind_dump" -> "تخلیه ذهن"
        "note" -> "یادداشت"
        "task" -> "وظیفه"
        "later_topic" -> "موضوع بعدی"
        "journal" -> "ژورنال"
        else -> "منبع"
    }

    // Query only while the picker is visible. SQL returns a bounded projection,
    // not complete journal/note/task bodies; typing is debounced to avoid query churn.
    LaunchedEffect(showSourceDialog, sourceKind, sourceSearch) {
        if (!showSourceDialog) {
            sourceChoices = emptyList()
            return@LaunchedEffect
        }
        if (sourceSearch.isNotBlank()) delay(250)
        val term = sourceSearch.trim().take(120)
        sourceChoices = try {
            when (sourceKind) {
                "mind_dump" -> userDb.mindDumpDao().searchMindMapSources(term, 100)
                "note" -> userDb.noteDao().searchMindMapSources(term, 100)
                "task" -> userDb.studyTaskDao().searchMindMapSources(term, 100)
                "later_topic" -> userDb.laterTopicDao().searchMindMapSources(term, 100)
                "journal" -> userDb.journalEntryDao().searchMindMapSources(term, 100)
                else -> emptyList()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            status = "منابع برای افزودن بارگذاری نشدند؛ دوباره تلاش کن."
            emptyList()
        }
    }
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

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("نقشه ذهنی و گراف دانش", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("ایده‌ها، یادداشت‌ها، پروژه‌ها و موضوعات یادگیری را به گره تبدیل کن و ارتباط معنایی میان آن‌ها بساز. داده‌ها به‌صورت محلی ذخیره می‌شوند.")
        }
        item {
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
            OutlinedButton(onClick = { showSourceDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("افزودن گره از اطلاعات موجود")
            }
        }
        item {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("جست‌وجوی گره‌ها") }, singleLine = true)
            Text("گره‌ها: ${nodes.size}  ·  ارتباط‌ها: ${edges.size}", style = MaterialTheme.typography.labelLarge)
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        if (visibleNodes.isNotEmpty()) {
            item {
                val graphIds = remember(visibleNodes) { visibleNodes.take(16).map { it.id }.toSet() }
                val graphEdges = remember(edges, graphIds) { edges.filter { it.sourceNodeId in graphIds && it.targetNodeId in graphIds } }
                Text("نمای گراف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("برای جابه‌جایی بکش؛ برای بزرگ‌نمایی از دو انگشت استفاده کن؛ روی گره بزن تا انتخاب شود.", style = MaterialTheme.typography.bodySmall)
                MindMapGraphCanvas(nodes = visibleNodes.take(16), edges = graphEdges, selectedNodeId = selectedGraphNodeId, scale = graphScale, panX = graphPanX, panY = graphPanY, onSelectNode = { selectedGraphNodeId = it }, onTransform = { zoom, dx, dy -> graphScale = (graphScale * zoom).coerceIn(0.65f, 2.8f); graphPanX += dx; graphPanY += dy })
                // Compose text labels keep Persian shaping, accessibility and selection reliable;
                // the Canvas itself only renders compact numeric markers for performance.
                visibleNodes.take(16).chunked(2).forEachIndexed { rowIndex, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEachIndexed { columnIndex, node ->
                            val number = rowIndex * 2 + columnIndex + 1
                            TextButton(
                                onClick = { selectedGraphNodeId = node.id },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$number · ${node.title}",
                                    maxLines = 1,
                                    color = if (node.id == selectedGraphNodeId) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("بزرگ‌نمایی: " + (graphScale * 100).toInt() + "٪", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { graphScale = 1f; graphPanX = 0f; graphPanY = 0f }) { Text("بازنشانی نما") }
                }
                val selected = visibleNodes.firstOrNull { it.id == selectedGraphNodeId }
                if (selected != null) Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(selected.title, fontWeight = FontWeight.SemiBold)
                        Text(selected.nodeType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (selected.description.isNotBlank()) Text(selected.description, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { edit(selected) }) { Text("ویرایش گره انتخاب‌شده") }
                    }
                }
                if (visibleNodes.size > 16) Text("برای حفظ سرعت، حداکثر ۱۶ گره در نمای گراف نمایش داده می‌شود؛ فهرست پایین همه گره‌ها را دارد.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (nodes.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("نقشه هنوز خالی است.", fontWeight = FontWeight.SemiBold)
                        Text("با ساخت چند گره و اتصال آن‌ها، شبکه دانش شخصی خودت را شکل بده.")
                    }
                }
            }
        } else if (visibleNodes.isEmpty()) {
            item { Text("گرهی با این عبارت پیدا نشد.") }
        }
        itemsIndexed(visibleNodes, key = { _, node -> node.id }) { index, node ->
            val related = relatedByNode[node.id].orEmpty()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(node.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(node.nodeType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (node.sourceType != null && node.sourceId != null) {
                                val sourceLabel = when (node.sourceType) {
                                    "mind_dump" -> "تخلیه ذهن"
                                    "note" -> "یادداشت"
                                    "task" -> "وظیفه"
                                    "later_topic" -> "موضوع بعدی"
                                    "journal" -> "ژورنال"
                                    else -> "منبع"
                                }
                                Text("منبع پیوندشده: $sourceLabel #${node.sourceId}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text("${index + 1} · #${node.id}", style = MaterialTheme.typography.labelSmall)
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

    if (showSourceDialog) AlertDialog(
        onDismissRequest = { showSourceDialog = false },
        title = { Text("افزودن از اطلاعات موجود") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { sourceKindMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("نوع منبع: $sourceKindLabel")
                    }
                    DropdownMenu(expanded = sourceKindMenu, onDismissRequest = { sourceKindMenu = false }) {
                        listOf(
                            "mind_dump" to "تخلیه ذهن",
                            "note" to "یادداشت",
                            "task" to "وظیفه",
                            "later_topic" to "موضوع بعدی",
                            "journal" to "ژورنال"
                        ).forEach { (kind, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { sourceKind = kind; sourceKindMenu = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = sourceSearch,
                    onValueChange = { sourceSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("جست‌وجو در منابع") },
                    singleLine = true
                )
                Text("حداکثر ۱۰۰ نتیجهٔ مرتبط؛ برای یافتن موارد قدیمی‌تر، عبارت دقیق‌تری جست‌وجو کن.", style = MaterialTheme.typography.bodySmall)
                if (sourceChoices.isEmpty()) {
                    Text(if (sourceSearch.isBlank()) "در این بخش داده‌ای برای افزودن وجود ندارد." else "موردی با این عبارت پیدا نشد.")
                } else {
                    Text("یک مورد را انتخاب کن؛ فقط عنوان و شناسه از پایگاه داده خوانده می‌شود.", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(sourceChoices, key = { it.id }) { choice ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val title = choice.title.trim().ifBlank { "بدون عنوان" }.take(200)
                                        val created = userDb.mindMapDao().createLinkedNode(
                                            MindMapNodeEntity(
                                                title = title,
                                                description = "ایجادشده از بخش $sourceKindLabel",
                                                nodeType = sourceKindLabel,
                                                sourceType = sourceKind,
                                                sourceId = choice.id
                                            )
                                        )
                                        status = if (created > 0) "گره منبع با موفقیت افزوده شد." else "این منبع قبلاً به نقشه اضافه شده است."
                                        showSourceDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(choice.title, maxLines = 2) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSourceDialog = false }) { Text("بستن") } }
    )

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
@Composable
private fun MindMapGraphCanvas(
    nodes: List<MindMapNodeEntity>,
    edges: List<com.mindunlock.app.data.local.MindMapEdgeEntity>,
    selectedNodeId: Long?,
    scale: Float,
    panX: Float,
    panY: Float,
    onSelectNode: (Long) -> Unit,
    onTransform: (Float, Float, Float) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val currentOnTransform by rememberUpdatedState(onTransform)
    val nodeRadius = 13.dp
    Canvas(
        Modifier.fillMaxWidth().height(250.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ -> currentOnTransform(zoom, pan.x, pan.y) }
            }
            .pointerInput(nodes, scale, panX, panY) {
                detectTapGestures { tap ->
                    if (nodes.isNotEmpty()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val local = Offset((tap.x - panX - center.x) / scale + center.x, (tap.y - panY - center.y) / scale + center.y)
                        val orbit = minOf(size.width, size.height) * 0.34f
                        val hitRadius = nodeRadius.toPx() * 2.2f
                        val hit = nodes.mapIndexed { index, node ->
                            val angle = -Math.PI / 2.0 + (2.0 * Math.PI * index / nodes.size)
                            val point = if (nodes.size == 1) center else Offset(center.x + orbit * kotlin.math.cos(angle).toFloat(), center.y + orbit * kotlin.math.sin(angle).toFloat())
                            node to (point - local).getDistance()
                        }.filter { it.second <= hitRadius }.minByOrNull { it.second }
                        hit?.first?.let { onSelectNode(it.id) }
                    }
                }
            }
    ) {
        drawRoundRect(color = scheme.surfaceVariant, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        val center = Offset(size.width / 2f, size.height / 2f)
        val orbit = minOf(size.width, size.height) * 0.34f
        val radiusPx = nodeRadius.toPx()
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = scheme.onPrimary.toArgb()
            textSize = 10.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val positions = nodes.mapIndexed { index, node ->
            val angle = -Math.PI / 2.0 + (2.0 * Math.PI * index / nodes.size.coerceAtLeast(1))
            node.id to if (nodes.size == 1) center else Offset(center.x + orbit * kotlin.math.cos(angle).toFloat(), center.y + orbit * kotlin.math.sin(angle).toFloat())
        }.toMap()
        withTransform({
            translate(left = panX, top = panY)
            scale(scaleX = scale, scaleY = scale, pivot = center)
        }) {
            edges.forEach { edge ->
                val from = positions[edge.sourceNodeId]
                val to = positions[edge.targetNodeId]
                if (from != null && to != null) drawLine(color = scheme.outline, start = from, end = to, strokeWidth = 2.dp.toPx())
            }
            nodes.forEachIndexed { index, node ->
                val point = positions[node.id] ?: return@forEachIndexed
                val selected = node.id == selectedNodeId
                if (selected) drawCircle(color = scheme.tertiary.copy(alpha = 0.25f), radius = radiusPx * 1.9f, center = point)
                drawCircle(color = if (selected) scheme.tertiary else scheme.primary, radius = radiusPx, center = point)
                drawCircle(color = scheme.surface, radius = 3.dp.toPx(), center = point)
                drawContext.canvas.nativeCanvas.drawText((index + 1).toString(), point.x, point.y + labelPaint.textSize * 0.34f, labelPaint)
            }
        }
    }
}
