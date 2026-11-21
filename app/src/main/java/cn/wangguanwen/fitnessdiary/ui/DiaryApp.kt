@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cn.wangguanwen.fitnessdiary.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.wangguanwen.fitnessdiary.DiaryViewModel
import cn.wangguanwen.fitnessdiary.EditorDraft
import cn.wangguanwen.fitnessdiary.BuildConfig
import cn.wangguanwen.fitnessdiary.data.*
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val PagePadding = 20.dp
private data class ScreenRoute(val page: Int, val draft: EditorDraft? = null)

@Composable
fun DiaryApp(model: DiaryViewModel) {
    SketchTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var deleting by remember { mutableStateOf<String?>(null) }
        var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
        val snackbar = remember { SnackbarHostState() }
        var choosingDate by rememberSaveable { mutableStateOf(false) }
        val editor = model.editor
        val route = ScreenRoute(if (editor == null) tab else 3, editor)
        val motionOffset = with(LocalDensity.current) { 8.dp.roundToPx() }
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(model::export) }
        val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::prepareRestore) }
        val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50), model::addPhotos)

        fun pickDate() { choosingDate = true }

        LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
        BackHandler(enabled = model.busy || editor != null) { if (!model.busy) model.requestClose() }

        Box(Modifier.fillMaxSize().background(Paper)) {
            SketchBackground(if (editor != null) 1 else tab)
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(if (editor != null) "每日记录" else when (tab) { 0 -> "健身日记"; 1 -> "我的记录"; else -> "设置与备份" }, fontFamily = Handwriting, fontSize = 28.sp, lineHeight = 34.sp)
                                if (editor == null) Text(when(tab) { 0 -> "一点一滴，看见自己的变化"; 1 -> "记录身体，也记录心情"; else -> "把你的记录，好好留在身边" }, fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                            }
                        },
                        navigationIcon = { if (editor != null) IconButton(onClick = model::requestClose, enabled = !model.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                        actions = {
                            if (editor != null) TextButton(onClick = model::save, enabled = !model.busy) { Text("保存", fontWeight = FontWeight.Bold) }
                            else if (tab != 2) IconButton(onClick = ::pickDate, enabled = model.loaded && !model.busy) { Icon(Icons.Outlined.CalendarMonth, "选择日期补记") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper.copy(alpha = .80f)),
                    )
                },
                bottomBar = {
                    if (editor == null) NavigationBar(modifier = Modifier.sketchBorder(), containerColor = Paper.copy(alpha = .84f), tonalElevation = 0.dp) {
                        listOf("趋势" to Icons.AutoMirrored.Outlined.ShowChart, "日记" to Icons.AutoMirrored.Outlined.MenuBook, "设置" to Icons.Outlined.Settings).forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icon, label) }, label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Ink, selectedIconColor = Paper, selectedTextColor = Ink, unselectedIconColor = Muted, unselectedTextColor = Muted))
                        }
                    }
                },
                floatingActionButton = {
                    if (editor == null && tab != 2 && model.loaded) ExtendedFloatingActionButton(
                        onClick = { model.openEditor(LocalDate.now().toString()) }, icon = { Icon(Icons.Outlined.Add, "记今天") }, text = { Text("记今天") },
                        shape = SketchShape, containerColor = Ink, contentColor = Paper)
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when {
                        model.loadError != null -> EmptyState(Icons.Outlined.ErrorOutline, "暂时无法读取记录", model.loadError!!, "重新读取", model::retryLoading)
                        !model.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        else -> AnimatedContent(targetState = route, contentKey = { it.page }, modifier = Modifier.fillMaxSize(),
                            transitionSpec = { (fadeIn(tween(220, 30, FastOutSlowInEasing)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { motionOffset }) togetherWith fadeOut(tween(120)) },
                            label = "纸页内容过渡") { current ->
                        when (current.page) {
                        3 -> current.draft?.let { draft -> EditorScreen(draft, model.editorError, model::updateWeight, model::updateNote,
                            { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            model::removePhoto, { photos, index -> viewer = ViewerRequest(photos, index, draft.date) }) }
                        0 -> TrendScreen(model.records) { model.openEditor(LocalDate.now().toString()) }
                        1 -> RecordsScreen(model.records, model::openEditor, { deleting = it }, { record, index ->
                            viewer = ViewerRequest(record.orderedPhotos.map { DraftPhoto(it.id, model.repository.photoFile(it)) }, index, record.entry.date)
                        }, model.repository::photoFile, ::pickDate)
                        else -> SettingsScreen(model.records.size, model.records.sumOf { it.photos.size },
                            { exportLauncher.launch("健身日记备份-${LocalDate.now()}.zip") },
                            { restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) })
                        }
                        }
                    }
                }
            }
            if (model.busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.sketchBorder(), shape = SketchShape, color = Paper.copy(alpha = .94f)) {
                    Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        CircularProgressIndicator(); Text(model.busyLabel)
                    }
                }
            }
        }

        if (model.confirmClose) AlertDialog(onDismissRequest = model::keepEditing, shape = SketchShape, containerColor = Paper.copy(alpha = .96f),
            title = { Text("保留这次修改？") }, text = { Text("这条记录还有未保存的内容。") },
            confirmButton = { TextButton(onClick = model::keepEditing) { Text("继续编辑") } },
            dismissButton = { TextButton(onClick = model::closeEditor) { Text("放弃修改") } })

        deleting?.let { date -> AlertDialog(onDismissRequest = { deleting = null }, shape = SketchShape, containerColor = Paper.copy(alpha = .96f), title = { Text("删除这天的记录？") },
            text = { Text("将删除 $date 的体重、感想和应用内照片，相册原图不受影响。") },
            confirmButton = { TextButton(onClick = { deleting = null; model.delete(date) }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }

        model.pendingRestore?.let { pending -> AlertDialog(onDismissRequest = { if (!model.busy) model.cancelRestore() }, shape = SketchShape, containerColor = Paper.copy(alpha = .96f),
            title = { Text("恢复这个备份？") },
            text = { Text("备份校验通过：${pending.records.size} 天记录、${pending.photoCount} 张照片。\n\n恢复将完整替换手机中现有的 ${model.records.size} 天记录。建议先导出当前备份。") },
            confirmButton = { TextButton(onClick = model::restore, enabled = !model.busy) { Text("确认替换并恢复") } },
            dismissButton = { TextButton(onClick = model::cancelRestore, enabled = !model.busy) { Text("取消") } }) }

        viewer?.let { request -> PhotoViewer(request) { viewer = null } }
        if (choosingDate) SketchDatePicker(onDismiss = { choosingDate = false }) { date -> choosingDate = false; model.openEditor(date.toString()) }
    }
}

@Composable
private fun TrendScreen(records: List<EntryWithPhotos>, onRecord: () -> Unit) {
    var days by rememberSaveable { mutableStateOf<Int?>(30) }
    val today = LocalDate.now()
    val allWeights = records.weightEntries(null, today)
    val latest = allWeights.lastOrNull()
    val previous = allWeights.dropLast(1).lastOrNull()
    LazyColumn(contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 14.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            SketchCard {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("最近体重", color = Ink, fontFamily = Handwriting, fontSize = 18.sp)
                        Icon(Icons.Outlined.MonitorWeight, null, tint = Ink)
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(latest?.weightHundredths?.let(WeightFormat::display) ?: "—", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("kg", Modifier.padding(bottom = 9.dp), color = Muted)
                    }
                    Text(if (latest == null) "从今天开始，留下第一笔记录" else "${latest.date}  ·  " +
                        if (previous == null) "你的第一条体重记录" else "较上次 ${WeightFormat.change(latest.weightHundredths!! - previous.weightHundredths!!)} kg", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(60.dp)) }
        item {
            SectionHeading("体重变化", "每一条记录都算数")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "7 天", 30 to "30 天", 90 to "90 天", null to "全部").forEach { (value, label) ->
                    FilterChip(selected = days == value, onClick = { days = value }, label = { Text(label) }, modifier = Modifier.weight(1f), shape = SketchShape,
                        colors = FilterChipDefaults.filterChipColors(containerColor = Paper.copy(alpha = .78f), labelColor = Ink,
                            selectedContainerColor = Ink, selectedLabelColor = Paper))
                }
            }
            Spacer(Modifier.height(12.dp))
            WeightChart(records.weightEntries(days, today))
        }
        item {
            val hasToday = records.any { it.entry.date == today.toString() }
            SketchCard(onClick = onRecord) {
                Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(if (hasToday) Icons.Outlined.CheckCircle else Icons.Outlined.EditNote, null, tint = Ink, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (hasToday) "今天已留下记录" else "今天感觉怎么样？", fontWeight = FontWeight.Bold)
                        Text(if (hasToday) "点击查看或继续补充" else "写点感想，拍下此刻的自己", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun RecordsScreen(records: List<EntryWithPhotos>, onEdit: (String) -> Unit, onDelete: (String) -> Unit,
    onPhoto: (EntryWithPhotos, Int) -> Unit, photoFile: (DiaryPhoto) -> java.io.File, onPickDate: () -> Unit) {
    if (records.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Outlined.MenuBook, "第一篇日记，留给今天", "体重、心情和照片，都可以单独记录。", "选择日期开始记录", onPickDate)
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 8.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(records, key = { _, item -> item.entry.date }) { index, record ->
            val date = LocalDate.parse(record.entry.date)
            if (index == 0 || records[index - 1].entry.date.take(7) != record.entry.date.take(7)) {
                Text("${date.year} 年 ${date.monthValue} 月", Modifier.padding(top = 12.dp, bottom = 12.dp).background(Paper.copy(alpha = .66f), SketchShape).padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, color = Muted)
            }
            SketchCard() {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(date.format(DateTimeFormatter.ofPattern("M 月 d 日")), fontSize = 20.sp, fontFamily = Handwriting)
                            Text(if (date == LocalDate.now()) "今天" else "星期" + listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.value - 1], fontSize = 12.sp, color = Muted)
                        }
                        record.entry.weightHundredths?.let { Text("${WeightFormat.display(it)} kg", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    if (record.entry.note.isNotBlank()) Text(record.entry.note, maxLines = 6, overflow = TextOverflow.Ellipsis, lineHeight = 23.sp)
                    if (record.photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(record.orderedPhotos, key = { _, photo -> photo.id }) { photoIndex, photo ->
                            AsyncImage(model = photoFile(photo), contentDescription = "${record.entry.date} 第 ${photoIndex + 1} 张照片", contentScale = ContentScale.Crop,
                                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(Paper.copy(alpha = .72f)).clickable { onPhoto(record, photoIndex) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (record.photos.isEmpty()) "每日记录" else "${record.photos.size} 张照片", color = Muted, fontSize = 12.sp)
                        Row {
                            TextButton(onClick = { onEdit(record.entry.date) }) { Text("查看 / 编辑") }
                            IconButton(onClick = { onDelete(record.entry.date) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.DeleteOutline, "删除 ${record.entry.date} 的记录", tint = Muted, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(draft: EditorDraft, error: String?, onWeight: (String) -> Unit, onNote: (String) -> Unit,
    onAddPhoto: () -> Unit, onRemovePhoto: (String) -> Unit, onViewPhoto: (List<DraftPhoto>, Int) -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Surface(modifier = Modifier.sketchBorder(), color = Paper.copy(alpha = .72f), shape = SketchShape) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CalendarToday, null, tint = Ink, modifier = Modifier.size(18.dp))
                Text(LocalDate.parse(draft.date).format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")), color = Ink, fontWeight = FontWeight.Medium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("今日体重", "公斤 · 保留两位小数")
            OutlinedTextField(value = draft.weight, onValueChange = onWeight, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如 65.25") }, suffix = { Text("kg") }, singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = sketchFieldColors(), shape = SketchShape, isError = error != null)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("今日感想", "随便写一点，长短都可以")
            OutlinedTextField(value = draft.note, onValueChange = onNote, modifier = Modifier.fillMaxWidth(), minLines = 5,
                placeholder = { Text("今天的状态、训练后的感受，或想对自己说的话……") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), colors = sketchFieldColors(), shape = SketchShape)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("身材照片", "${draft.photos.size} 张 · 仅保存在本机")
            draft.photos.chunked(3).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { columnIndex, photo ->
                        Box(Modifier.weight(1f).aspectRatio(.8f).clip(RoundedCornerShape(14.dp)).background(Paper.copy(alpha = .72f))) {
                            AsyncImage(model = photo.file, contentDescription = "第 ${rowIndex * 3 + columnIndex + 1} 张照片，点击查看大图", contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clickable { onViewPhoto(draft.photos, rowIndex * 3 + columnIndex) })
                            IconButton(onClick = { onRemovePhoto(photo.id) }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).background(Color.Black.copy(alpha = .55f), CircleShape)) {
                                Icon(Icons.Outlined.Close, "移除这张照片", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            OutlinedButton(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth().height(52.dp), shape = SketchShape,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper.copy(alpha = .74f), contentColor = Ink)) {
                Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("从相册添加照片")
            }
            Text("添加后会保存一份照片副本，不影响相册原图。", Modifier.fillMaxWidth().background(Paper.copy(alpha = .72f), SketchShape).padding(10.dp), color = Muted, fontSize = 12.sp)
        }
        error?.let { Text(it, Modifier.fillMaxWidth().background(Paper.copy(alpha = .82f), SketchShape).padding(10.dp), color = MaterialTheme.colorScheme.error) }
        Text("体重、感想和照片至少填写一项；完成后点击右上角“保存”。", Modifier.fillMaxWidth().background(Paper.copy(alpha = .72f), SketchShape).padding(10.dp), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsScreen(recordCount: Int, photoCount: Int, onExport: () -> Unit, onRestore: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SketchCard() {
            Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FavoriteBorder, null, tint = Ink, modifier = Modifier.size(30.dp))
                Column { Text("属于你的健身日记", fontFamily = Handwriting, fontSize = 20.sp); Text("$recordCount 天记录  ·  $photoCount 张照片", color = Muted, fontSize = 13.sp) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("备份与恢复", "文字、体重、照片一起保存")
            SettingsAction(Icons.Outlined.FileDownload, "导出完整备份", "保存为 ZIP 文件，换手机时可以恢复", onExport)
            SettingsAction(Icons.Outlined.Restore, "从备份恢复", "校验后确认，完整替换当前记录", onRestore)
        }
        SketchCard() {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.Lock, null, tint = Ink, modifier = Modifier.size(20.dp)); Text("记录只留在你手里", fontFamily = Handwriting, fontSize = 18.sp) }
                Text("无需账号，不联网，不上传照片，也不收集使用数据。朋友安装后，记录彼此独立。", color = Muted, lineHeight = 22.sp)
                HorizontalDivider(color = Paper)
                Text("换手机、卸载应用或清除应用数据前，请先导出备份，并保存到应用以外的位置。备份包含私人照片，请妥善保管。", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            }
        }
        Text("健身日记  ${BuildConfig.VERSION_NAME}\n慢慢记录，慢慢看见变化。", modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).background(Paper.copy(alpha = .72f), SketchShape).padding(12.dp), color = Muted, fontSize = 12.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, subtitle: String, action: () -> Unit) {
    SketchCard(onClick = action) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = Ink)
            Column(Modifier.weight(1f)) { Text(title, fontFamily = Handwriting, fontSize = 18.sp); Text(subtitle, fontSize = 12.sp, color = Muted) }
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().background(Paper.copy(alpha = .70f), SketchShape).padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 20.sp, fontFamily = Handwriting)
        Text(subtitle, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        SketchCard(opacity = .82f) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp)); Text(title, fontSize = 20.sp, fontFamily = Handwriting)
        Spacer(Modifier.height(10.dp)); Text(subtitle, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp)); FilledTonalButton(onClick = onClick) { Text(button) }
        }
        }
    }
}

private data class ViewerRequest(val photos: List<DraftPhoto>, val index: Int, val date: String)

@Composable
private fun PhotoViewer(request: ViewerRequest, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = request.index) { request.photos.size }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Ink).systemBarsPadding()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(model = request.photos[page].file, contentDescription = "${request.date} 第 ${page + 1} 张照片", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(vertical = 72.dp))
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "关闭照片", tint = Color.White) }
                Text(request.date, color = Color.White, modifier = Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${request.photos.size}", color = Color.White, modifier = Modifier.padding(end = 12.dp))
            }
            Text("左右滑动查看照片", color = Color.White.copy(alpha = .65f), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp))
        }
    }
}
