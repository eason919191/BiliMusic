with open('app/src/main/java/com/bilimusic/ui/screens/player/PlayerScreen.kt', encoding='utf-8') as f:
    content = f.read()

old = '\n}\n\n@Composable\nprivate fun LyricsView('

dialog_block = '''
    // Dialog composables for BottomSheet
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

// ===== BottomSheet Dialogs =====

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
                    val items = mutableListOf("X" to info.name, "Y" to info.artistName)
                    if (info.album != null) items.add("Z" to info.album.name)
                    if (info.genre.isNotBlank()) items.add("G" to info.genre)
                    if (info.language.isNotBlank()) items.add("L" to info.language)
                    if (info.pop > 0) items.add("P" to "${info.pop.toInt()}%")
                    detailInfo = items
                }
            }
        } catch (_: Exception) {}
        isLoading = false
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Song Info") }, text = {
        if (isLoading) Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column { detailInfo.forEach { (label, value) -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label + ": ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp)); Text(value, style = MaterialTheme.typography.bodyMedium) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun LyricEditDialog(lyrics: List<com.bilimusic.data.api.LyricLine>, onSave: (List<com.bilimusic.data.api.LyricLine>) -> Unit, onDismiss: () -> Unit) {
    var editedText by remember(lyrics) { mutableStateOf(lyrics.joinToString("\n") { it.timeMs.toString() + ":" + it.text }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lyric Edit") }, text = {
        Column {
            Text("Edit lyrics (ts:text per line)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = editedText, onValueChange = { editedText = it }, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), maxLines = 20)
        }
    }, confirmButton = { TextButton(onClick = {
        val newLyrics = editedText.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val idx = line.indexOf(':'); if (idx > 0) { val t = line.substring(0, idx).toLongOrNull(); val txt = line.substring(idx + 1); if (t != null) com.bilimusic.data.api.LyricLine(timeMs = t, text = txt) else null } else null
        }; if (newLyrics.isNotEmpty()) onSave(newLyrics)
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun QualitySelectDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Quality") }, text = {
        Column {
            listOf("standard" to "128kbps", "higher" to "192kbps", "exhigh" to "320kbps", "lossless" to "FLAC", "hires" to "Hi-Res").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = current == value, onClick = { onSelect(value) }); Spacer(Modifier.width(8.dp)); Text(label) }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun TimerDialog(isActive: Boolean, remaining: Long, onSelect: (Long) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val timerOptions = listOf(15L*60*1000 to "15m", 30L*60*1000 to "30m", 45L*60*1000 to "45m", 60L*60*1000 to "60m", 90L*60*1000 to "90m", 120L*60*1000 to "120m")
    var showCustomDialog by remember { mutableStateOf(false) }; var customMins by remember { mutableStateOf("") }
    if (showCustomDialog) {
        AlertDialog(onDismissRequest = { showCustomDialog = false }, title = { Text("Custom") }, text = { OutlinedTextField(value = customMins, onValueChange = { customMins = it.filter { c -> c.isDigit() } }, label = { Text("min") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { val mins = customMins.toLongOrNull(); if (mins != null && mins > 0) { onSelect(mins * 60 * 1000); showCustomDialog = false } }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") } }); return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Timer") }, text = {
        Column {
            if (isActive) { Text("Remaining ${String.format("%02d:%02d", remaining / 60000, (remaining % 60000) / 1000)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)) }
            TextButton(onClick = { onClear() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (isActive) "Stop" else "No timer", Modifier.weight(1f)) }; HorizontalDivider()
            timerOptions.forEach { (millis, label) -> TextButton(onClick = { onSelect(millis) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Timer, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label, Modifier.weight(1f)) } }; HorizontalDivider()
            TextButton(onClick = { showCustomDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Custom...", Modifier.weight(1f)) }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(currentSpeed: Float, onSetSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    var speed by remember { mutableFloatStateOf(currentSpeed) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
            Text("Speed", style = MaterialTheme.typography.titleMedium)
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
            Text("Pitch", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(String.format("%.1f", pitch), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Slider(value = pitch, onValueChange = { pitch = (it * 10).roundToInt() / 10f; onSetPitch(pitch) }, valueRange = 0.1f..5.0f, steps = 49, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0.1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("5.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun LyricsView('

content = content.replace(old, dialog_block, 1)

with open('app/src/main/java/com/bilimusic/ui/screens/player/PlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done phase 2')
