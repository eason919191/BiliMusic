with open('app/src/main/java/com/bilimusic/ui/screens/player/PlayerScreen.kt', encoding='utf-8') as f:
    content = f.read()

# Add dialog states to TopButtonsRow
old = '''    var showMoreMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,'''

new = '''    var showMoreMenu by remember { mutableStateOf(false) }
    var showSongDetailDialog by remember { mutableStateOf(false) }
    var showLyricEditDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showPitchSheet by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,'''
assert old in content, "Pattern 1 not found"
content = content.replace(old, new, 1)

# Update supporting texts
content = content.replace(
    'supportingContent = { /* TODO: timer status */ Text("关闭") }',
    'supportingContent = { Text(if (uiState.isTimerActive) { val m = uiState.sleepTimerRemaining / 60000; val s = (uiState.sleepTimerRemaining % 60000) / 1000; "剩余 ${String.format("%02d:%02d", m, s)}" } else "关闭") }')
content = content.replace('supportingContent = { Text("1.0x") },', 'supportingContent = { Text("${uiState.playbackSpeed}x") },')
content = content.replace('supportingContent = { Text("1.0") },', 'supportingContent = { Text("${uiState.pitch}") },')

# Replace TODO handlers
content = content.replace("/* TODO: song detail */ }", "showSongDetailDialog = true }")
content = content.replace("/* TODO: lyric edit */ }", "showLyricEditDialog = true }")
content = content.replace("/* TODO: quality */ }", "showQualityDialog = true }")
content = content.replace("/* TODO: timer */ }", "showTimerDialog = true }")
content = content.replace("/* TODO: speed */ }", "showSpeedSheet = true }")
content = content.replace("/* TODO: pitch */ }", "showPitchSheet = true }")

# Add callbacks to TopButtonsRow parameter list
old = '''    onShowPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {'''
new = '''    onShowPlaylist: () -> Unit,
    onEditLyrics: (List<com.bilimusic.data.api.LyricLine>) -> Unit = {},
    onSetSleepTimer: (Long) -> Unit = {},
    onClearSleepTimer: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onSetPitch: (Float) -> Unit = {},
    onSetAudioQuality: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {'''
assert old in content, "Pattern 3 not found"
content = content.replace(old, new, 1)

# Add dialog composables before LyricsView
old = '''}

@Composable
private fun LyricsView('''

dialog_code = """
    // ===== BottomSheet Dialogs =====
    if (showSongDetailDialog) {
        SongDetailDialog(uiState = uiState, onDismiss = { showSongDetailDialog = false })
    }
    if (showLyricEditDialog) {
        LyricEditDialog(lyrics = uiState.lyrics, onSave = { onEditLyrics(it); showLyricEditDialog = false }, onDismiss = { showLyricEditDialog = false })
    }
    if (showQualityDialog) {
        QualitySelectDialog(current = uiState.audioQuality, onSelect = { onSetAudioQuality(it); showQualityDialog = false }, onDismiss = { showQualityDialog = false })
    }
    if (showTimerDialog) {
        TimerDialog(isActive = uiState.isTimerActive, remaining = uiState.sleepTimerRemaining, onSelect = { onSetSleepTimer(it); showTimerDialog = false }, onClear = { onClearSleepTimer(); showTimerDialog = false }, onDismiss = { showTimerDialog = false })
    }
    if (showSpeedSheet) {
        SpeedSheet(currentSpeed = uiState.playbackSpeed, onSetSpeed = { onSetPlaybackSpeed(it) }, onDismiss = { showSpeedSheet = false })
    }
    if (showPitchSheet) {
        PitchSheet(currentPitch = uiState.pitch, onSetPitch = { onSetPitch(it) }, onDismiss = { showPitchSheet = false })
    }
}

// ===== Dialog composables =====

@Composable
private fun SongDetailDialog(uiState: PlayerUiState, onDismiss: () -> Unit) {
    val song = uiState.currentSong ?: return
    var detailInfo by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(song.id) {
        try {
            if (song.neteaseId > 0) {
                val resp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseApiClient.getSongDetail(listOf(song.neteaseId)) }
                val songs = com.bilimusic.data.api.netease.NeteaseSongParser.parseSongDetail(resp)
                val info = songs.firstOrNull()
                if (info != null) {
                    val items = mutableListOf("歌曲" to info.name, "歌手" to info.artistName)
                    if (info.album != null) items.add("专辑" to info.album.name)
                    if (info.genre.isNotBlank()) items.add("曲风" to info.genre)
                    if (info.language.isNotBlank()) items.add("语种" to info.language)
                    if (info.pop > 0) items.add("人气" to "${info.pop.toInt()}%")
                    items.add("来源" to (if (song.source == "NETEASE") "网易云音乐" else "哔哩哔哩"))
                    detailInfo = items
                } else { detailInfo = listOf("歌曲" to song.title, "歌手" to song.artist) }
            } else { detailInfo = listOf("歌曲" to song.title, "歌手" to song.artist) }
        } catch (_: Exception) { detailInfo = listOf("歌曲" to song.title, "歌手" to song.artist) }
        isLoading = false
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("歌曲详情") }, text = {
        if (isLoading) Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column { detailInfo.forEach { (label, value) -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label + ": ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp)); Text(value, style = MaterialTheme.typography.bodyMedium) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
private fun LyricEditDialog(lyrics: List<com.bilimusic.data.api.LyricLine>, onSave: (List<com.bilimusic.data.api.LyricLine>) -> Unit, onDismiss: () -> Unit) {
    var editedText by remember(lyrics) { mutableStateOf(lyrics.joinToString("\\n") { it.timeMs.toString() + ":" + it.text }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("歌词编辑") }, text = {
        Column {
            Text("编辑歌词（格式：时间戳:文本，每行一句）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = editedText, onValueChange = { editedText = it }, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), maxLines = 20)
        }
    }, confirmButton = { TextButton(onClick = {
        val newLyrics = editedText.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val idx = line.indexOf(':'); if (idx > 0) { val t = line.substring(0, idx).toLongOrNull(); val txt = line.substring(idx + 1); if (t != null) com.bilimusic.data.api.LyricLine(timeMs = t, text = txt) else null } else null
        }; if (newLyrics.isNotEmpty()) onSave(newLyrics)
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun QualitySelectDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("音质选项") }, text = {
        Column {
            listOf("standard" to "标准 (128kbps)", "higher" to "较高 (192kbps)", "exhigh" to "极高 (320kbps)", "lossless" to "无损 (FLAC)", "hires" to "Hi-Res").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = current == value, onClick = { onSelect(value) }); Spacer(Modifier.width(8.dp)); Text(label) }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun TimerDialog(isActive: Boolean, remaining: Long, onSelect: (Long) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val timerOptions = listOf(15L*60*1000 to "15分钟", 30L*60*1000 to "30分钟", 45L*60*1000 to "45分钟", 60L*60*1000 to "60分钟", 90L*60*1000 to "90分钟", 120L*60*1000 to "120分钟")
    var showCustomDialog by remember { mutableStateOf(false) }; var customMins by remember { mutableStateOf("") }
    if (showCustomDialog) {
        AlertDialog(onDismissRequest = { showCustomDialog = false }, title = { Text("自定义时间") }, text = { OutlinedTextField(value = customMins, onValueChange = { customMins = it.filter { c -> c.isDigit() } }, label = { Text("分钟") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { val mins = customMins.toLongOrNull(); if (mins != null && mins > 0) { onSelect(mins * 60 * 1000); showCustomDialog = false } }) { Text("确定") } }, dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("取消") } }); return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("定时关闭") }, text = {
        Column {
            if (isActive) { Text("剩余 ${String.format("%02d:%02d", remaining / 60000, (remaining % 60000) / 1000)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)) }
            TextButton(onClick = { onClear() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (isActive) "关闭定时" else "不使用定时", Modifier.weight(1f)) }; HorizontalDivider()
            timerOptions.forEach { (millis, label) -> TextButton(onClick = { onSelect(millis) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Timer, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label, Modifier.weight(1f)) } }; HorizontalDivider()
            TextButton(onClick = { showCustomDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("自定义...", Modifier.weight(1f)) }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(currentSpeed: Float, onSetSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    var speed by remember { mutableFloatStateOf(currentSpeed) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
            Text("倍速播放", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(String.format("%.1f", speed) + "x", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Slider(value = speed, onValueChange = { speed = (it * 10).roundToInt() / 10f; onSetSpeed(speed) }, valueRange = 0.1f..5.0f, steps = 49, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0.1x", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("5.0x", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PitchSheet(currentPitch: Float, onSetPitch: (Float) -> Unit, onDismiss: () -> Unit) {
    var pitch by remember { mutableFloatStateOf(currentPitch) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
            Text("音调调节", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(String.format("%.1f", pitch), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Slider(value = pitch, onValueChange = { pitch = (it * 10).roundToInt() / 10f; onSetPitch(pitch) }, valueRange = 0.1f..5.0f, steps = 49, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0.1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("5.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(8.dp)); Text("提示：音调独立于速度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LyricsView('''
assert old in content, "Pattern 4 not found"
content = content.replace(old, dialog_code, 1)

# Now update both TopButtonsRow calls in PlayerContent to pass the new callbacks
# Both calls are the same pattern - we need to add callbacks that use viewModel
# But TopButtonsRow is inside PlayerContent which doesn't have viewModel
# Solution: add new callback params to PlayerContent too

with open('app/src/main/java/com/bilimusic/ui/screens/player/PlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")
