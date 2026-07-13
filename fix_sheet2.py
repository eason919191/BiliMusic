with open('app/src/main/java/com/bilimusic/ui/screens/player/PlayerScreen.kt', encoding='utf-8') as f:
    content = f.read()

# Add dialog states
old = '    var showMoreMenu by remember { mutableStateOf(false) }\n\n    Row(\n        modifier = modifier,'
new = (
    '    var showMoreMenu by remember { mutableStateOf(false) }\n'
    '    var showSongDetailDialog by remember { mutableStateOf(false) }\n'
    '    var showLyricEditDialog by remember { mutableStateOf(false) }\n'
    '    var showQualityDialog by remember { mutableStateOf(false) }\n'
    '    var showTimerDialog by remember { mutableStateOf(false) }\n'
    '    var showSpeedSheet by remember { mutableStateOf(false) }\n'
    '    var showPitchSheet by remember { mutableStateOf(false) }\n'
    '\n    Row(\n        modifier = modifier,'
)
content = content.replace(old, new, 1)

# Update subtitles
content = content.replace(
    'supportingContent = { /* TODO: timer status */ Text("关闭") }',
    'supportingContent = { Text(if (uiState.isTimerActive) { val m = uiState.sleepTimerRemaining / 60000; val s = (uiState.sleepTimerRemaining % 60000) / 1000; "剩余 ${String.format(\"%02d:%02d\", m, s)}" } else "关闭") }'
)
content = content.replace('supportingContent = { Text("1.0x") },', 'supportingContent = { Text("${uiState.playbackSpeed}x") },')
content = content.replace('supportingContent = { Text("1.0") },', 'supportingContent = { Text("${uiState.pitch}") },')

# Replace TODOs
for old_t, new_t in [
    ('/* TODO: song detail */ }', 'showSongDetailDialog = true }'),
    ('/* TODO: lyric edit */ }', 'showLyricEditDialog = true }'),
    ('/* TODO: quality */ }', 'showQualityDialog = true }'),
    ('/* TODO: timer */ }', 'showTimerDialog = true }'),
    ('/* TODO: speed */ }', 'showSpeedSheet = true }'),
    ('/* TODO: pitch */ }', 'showPitchSheet = true }'),
]:
    content = content.replace(old_t, new_t, 1)

# Add callbacks to TopButtonsRow params
old = '    onShowPlaylist: () -> Unit,\n    modifier: Modifier = Modifier,\n) {'
new = (
    '    onShowPlaylist: () -> Unit,\n'
    '    onEditLyrics: (List<com.bilimusic.data.api.LyricLine>) -> Unit = {},\n'
    '    onSetSleepTimer: (Long) -> Unit = {},\n'
    '    onClearSleepTimer: () -> Unit = {},\n'
    '    onSetPlaybackSpeed: (Float) -> Unit = {},\n'
    '    onSetPitch: (Float) -> Unit = {},\n'
    '    onSetAudioQuality: (String) -> Unit = {},\n'
    '    modifier: Modifier = Modifier,\n) {'
)
content = content.replace(old, new, 1)

# Add callbacks to PlayerContent params
old = '    onShare: () -> Unit = {},\n    coverSharedModifier: Modifier = Modifier'
new = (
    '    onShare: () -> Unit = {},\n'
    '    onSetSleepTimer: (Long) -> Unit = {},\n'
    '    onClearSleepTimer: () -> Unit = {},\n'
    '    onSetPlaybackSpeed: (Float) -> Unit = {},\n'
    '    onSetPitch: (Float) -> Unit = {},\n'
    '    onSetAudioQuality: (String) -> Unit = {},\n'
    '    onEditLyrics: (List<com.bilimusic.data.api.LyricLine>) -> Unit = {},\n'
    '    coverSharedModifier: Modifier = Modifier'
)
content = content.replace(old, new, 1)

# Update TopButtonsRow calls - replace all occurrences
old_call = '                                    TopButtonsRow(uiState = uiState, onToggleLyrics = onToggleLyrics, onCycleLyricsMode = onCycleLyricsMode, onMinimize = onMinimize, onShare = onShare, onAddToPlaylist = onAddToPlaylist, onShowPlaylist = { showPlaylist = true })'
new_call = '                                    TopButtonsRow(uiState = uiState, onToggleLyrics = onToggleLyrics, onCycleLyricsMode = onCycleLyricsMode, onMinimize = onMinimize, onShare = onShare, onAddToPlaylist = onAddToPlaylist, onShowPlaylist = { showPlaylist = true }, onEditLyrics = onEditLyrics, onSetSleepTimer = onSetSleepTimer, onClearSleepTimer = onClearSleepTimer, onSetPlaybackSpeed = onSetPlaybackSpeed, onSetPitch = onSetPitch, onSetAudioQuality = onSetAudioQuality)'
content = content.replace(old_call, new_call)

# Add callbacks to PlayerScreen -> PlayerContent call
old_screen = '                onShare = {'
new_screen = '                onSetSleepTimer = { viewModel.setSleepTimer(it) },\n                onClearSleepTimer = { viewModel.clearSleepTimer() },\n                onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },\n                onSetPitch = { viewModel.setPitch(it) },\n                onSetAudioQuality = { viewModel.setAudioQuality(it) },\n                onEditLyrics = { viewModel.updateLyrics(it) },\n                onShare = {'
content = content.replace(old_screen, new_screen, 1)

with open('app/src/main/java/com/bilimusic/ui/screens/player/PlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done phase 1')
