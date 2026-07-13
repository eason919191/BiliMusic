with open('app/src/main/java/com/bilimusic/ui/screens/player/PlayerScreen.kt', encoding='utf-8') as f:
    c = f.read()

# Find and replace the marker right before LyricsView
import re
old = re.search(r'\}\n\n@Composable\nprivate fun LyricsView\(', c)
assert old, 'Marker not found'

new_text = '''
    // Dialogs from BottomSheet
    if (showSongDetailDialog) { SongDetailDialog(uiState = uiState, onDismiss = { showSongDetailDialog = false }) }
    if (showLyricEditDialog) { LyricEditDialog(lyrics = uiState.lyrics, onSave = { onEditLyrics(it); showLyricEditDialog = false }, onDismiss = { showLyricEditDialog = false }) }
    if (showQualityDialog) { QualitySelectDialog(current = uiState.audioQuality, onSelect = { onSetAudioQuality(it); showQualityDialog = false }, onDismiss = { showQualityDialog = false }) }
    if (showTimerDialog) { TimerDialog(isActive = uiState.isTimerActive, remaining = uiState.sleepTimerRemaining, onSelect = { onSetSleepTimer(it); showTimerDialog = false }, onClear = { onClearSleepTimer(); showTimerDialog = false }, onDismiss = { showTimerDialog = false }) }
    if (showSpeedSheet) { SpeedSheet(currentSpeed = uiState.playbackSpeed, onSetSpeed = { onSetPlaybackSpeed(it) }, onDismiss = { showSpeedSheet = false }) }
    if (showPitchSheet) { PitchSheet(currentPitch = uiState.pitch, onSetPitch = { onSetPitch(it) }, onDismiss = { showPitchSheet = false }) }
}

// ===== DIALOGS =====

@Composable
private fun SongDetailDialog(uiState: PlayerUiState, onDismiss: () -> Unit) {
    val song = uiState.currentSong ?: return
    var info by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(song.id) {
        try {
            if (song.neteaseId > 0) {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.bilimusic.data.api.netease.NeteaseApiClient.getSongDetail(listOf(song.neteaseId)) }
                val s = com.bilimusic.data.api.netease.NeteaseSongParser.parseSongDetail(r).firstOrNull()
                if (s != null) {
                    val items = mutableListOf("Song" to s.name, "Artist" to s.artistName)
                    if (s.album != null) items.add("Album" to s.album.name)
                    if (s.genre.isNotBlank()) items.add("Genre" to s.genre)
                    if (s.language.isNotBlank()) items.add("Language" to s.language)
                    if (s.pop > 0) items.add("Pop" to "${s.pop.toInt()}%")
                    info = items
                }
            }
        } catch (_: Exception) {}
        loading = false
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Song Details") }, text = {
        if (loading) Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column { info.forEach { (l,v) -> Text(l + ": " + v, modifier = Modifier.padding(vertical = 2.dp)) } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun LyricEditDialog(lyrics: List<com.bilimusic.data.api.LyricLine>, onSave: (List<com.bilimusic.data.api.LyricLine>) -> Unit, onDismiss: () -> Unit) {
    var txt by remember(lyrics) { mutableStateOf(lyrics.joinToString("\\n") { it.timeMs.toString() + ":" + it.text }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit Lyrics") }, text = {
        Column {
            Text("Format: timestamp:text per line", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = txt, onValueChange = { txt = it }, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), maxLines = 20)
        }
    }, confirmButton = { TextButton(onClick = {
        val nl = txt.lines().filter { it.isNotBlank() }.mapNotNull { ln -> val ci = ln.indexOf(":"); if (ci > 0) { val t = ln.substring(0, ci).toLongOrNull(); val text = ln.substring(ci + 1); if (t != null) com.bilimusic.data.api.LyricLine(timeMs = t, text = text) else null } else null }
        if (nl.isNotEmpty()) onSave(nl)
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun QualitySelectDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val opts = listOf("standard" to "Std 128k", "higher" to "High 192k", "exhigh" to "ExHigh 320k", "lossless" to "Lossless FLAC", "hires" to "Hi-Res")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Quality") }, text = { Column { opts.forEach { (v,l) -> Row(Modifier.fillMaxWidth().clickable { onSelect(v) }.padding(vertical = 8.dp, horizontal = 4.dp)) { RadioButton(selected = current == v, onClick = { onSelect(v) }); Spacer(Modifier.width(8.dp)); Text(l) } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun TimerDialog(isActive: Boolean, remaining: Long, onSelect: (Long) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val opts = listOf(15L*60*1000 to "15min", 30L*60*1000 to "30min", 45L*60*1000 to "45min", 60L*60*1000 to "60min", 90L*60*1000 to "90min", 120L*60*1000 to "120min")
    var showC by remember { mutableStateOf(false) }; var cm by remember { mutableStateOf("") }
    if (showC) { AlertDialog(onDismissRequest = { showC = false }, title = { Text("Custom") }, text = { OutlinedTextField(value = cm, onValueChange = { cm = it.filter { c -> c.isDigit() } }, label = { Text("minutes") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { val mins = cm.toLongOrNull(); if (mins != null && mins > 0) { onSelect(mins * 60 * 1000); showC = false } }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showC = false }) { Text("Cancel") } }); return }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sleep Timer") }, text = {
        Column {
            if (isActive) { Text("Remaining: " + String.format("%02d:%02d", remaining/60000, (remaining%60000)/1000), style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(8.dp)) }
            TextButton(onClick = { onClear() }, modifier = Modifier.fillMaxWidth()) { Text(if (isActive) "Stop" else "Off", Modifier.weight(1f)) }
            Divider()
            opts.forEach { (m,l) -> TextButton(onClick = { onSelect(m) }, modifier = Modifier.fillMaxWidth()) { Text(l, Modifier.weight(1f)) } }
            Divider()
            TextButton(onClick = { showC = true }, modifier = Modifier.fillMaxWidth()) { Text("Custom...", Modifier.weight(1f)) }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(speed: Float, onSetSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableFloatStateOf(speed) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Speed", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(String.format("%.1f", s)+"x", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Slider(value = s, onValueChange = { s = (it*10).roundToInt()/10f; onSetSpeed(s) }, valueRange = 0.1f..5.0f, steps = 49, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0.1x"); Text("5.0x") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PitchSheet(p: Float, onSetPitch: (Float) -> Unit, onDismiss: () -> Unit) {
    var pitch by remember { mutableFloatStateOf(p) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Pitch", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(String.format("%.1f", pitch), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Slider(value = pitch, onValueChange = { pitch = (it*10).roundToInt()/10f; onSetPitch(pitch) }, valueRange = 0.1f..5.0f, steps = 49, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0.1"); Text("5.0") }
        }
    }
}

@Composable
private fun LyricsView('

pos = old.start()
c = c[:pos] + new_text + c[pos + len(old.group()):]

with open('app/src/main/java/com/bilimusic/ui/screens/player/PlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(c)
print('Done')
