package com.bilimusic.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bilimusic.data.api.netease.NeteasePlaylist

@Composable
fun ImportNeteasePlaylistDialog(
    playlist: NeteasePlaylist,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.QueueMusic, null) },
        title = { Text("导入歌单") },
        text = {
            Column {
                Text("将「${playlist.name}」导入为本地歌单？")
                Spacer(Modifier.height(4.dp))
                Text("${playlist.songCount} 首歌曲", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
