package com.bilimusic.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bilimusic.data.model.Music
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

@Composable
fun MiniPlayerBar(
    currentSong: Music?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverSharedModifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    alpha: Float = 0.72f
) {
    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        var dragTotal by remember { mutableFloatStateOf(0f) }
        val swipeThreshold = 150f
        val dragFraction by animateFloatAsState(
            targetValue = (kotlin.math.abs(dragTotal) / swipeThreshold).coerceIn(0f, 1f),
            animationSpec = tween(50),
            label = "drag_fraction"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = dragTotal
                    this.alpha = 1f - dragFraction * 0.3f
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragTotal > swipeThreshold) onPreviousClick()
                            else if (dragTotal < -swipeThreshold) onNextClick()
                            dragTotal = 0f
                        },
                        onDragCancel = { dragTotal = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount }
                    )
                }
        ) {
            val fogMod = if (hazeState != null) Modifier.hazeChild(state = hazeState) else Modifier
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(fogMod)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                                    androidx.compose.ui.graphics.Color.Transparent
                                ),
                                startY = 0f,
                                endY = 3f
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentSong?.coverUrl != null) {
                            BiliAsyncImage(
                                model = currentSong.coverUrl,
                                contentDescription = "封面",
                                modifier = Modifier.size(48.dp).then(coverSharedModifier).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        AnimatedContent(
                            targetState = currentSong?.id ?: "",
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            transitionSpec = {
                                val direction = if (targetState > initialState) 1 else -1
                                (slideInHorizontally { width -> width * direction } + fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith
                                        (slideOutHorizontally { width -> -width * direction } + fadeOut() + scaleOut(targetScale = 0.85f))
                            },
                            label = "mini_player_song"
                        ) { Column {
                            Text(currentSong?.title ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            Text(currentSong?.artist ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } }

                        IconButton(onClick = onPlayPauseClick) {
                            Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "暂停" else "播放", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onNextClick) {
                            Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "下一首", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
