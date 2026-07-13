package com.bilimusic.ui.components

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bilimusic.data.api.LyricLine
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

private const val LYRIC_TIME_SMOOTHING_DURATION_MS = 96
private const val LYRIC_TIME_SMOOTHING_MAX_DELTA_MS = 240L

data class LyricVisualSpec(
    val pageTiltDeg: Float = 9f,
    val activeScale: Float = 1.15f,
    val nearScale: Float = 0.95f,
    val farScale: Float = 0.90f,
    val farScaleMin: Float = 0.82f,
    val farScaleFalloffPerStep: Float = 0.02f,
    val inactiveBlurNear: Dp = 2.dp,
    val inactiveBlurFar: Dp = 3.dp,
    val flipDurationMs: Int = 260
)

private fun findCurrentLineIndex(lines: List<LyricLine>, currentTimeMs: Long): Int {
    if (lines.isEmpty()) return -1
    var low = 0
    var high = lines.lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].timeMs <= currentTimeMs) {
            result = mid
            low = mid + 1
        } else high = mid - 1
    }
    return result
}

private fun scaleForDistance(d: Int, spec: LyricVisualSpec): Float = when {
    d <= 0 -> spec.activeScale
    d == 1 -> spec.nearScale
    else -> (spec.farScale - (d - 2) * spec.farScaleFalloffPerStep).coerceIn(spec.farScaleMin, spec.farScale)
}

private fun alphaForDistance(d: Int, near: Float, far: Float): Float = when (d) {
    1 -> near
    2 -> far
    else -> (far - 0.08f * (d - 2)).coerceIn(0.16f, far)
}

private fun blurForDistance(d: Int, maxBlur: Float): Float = when (d) {
    1 -> maxBlur * 1.0f
    2 -> maxBlur * 1.5f
    3 -> maxBlur * 2.0f
    4 -> maxBlur * 2.5f
    else -> maxBlur * 4.0f
}

@Composable
internal fun rememberSmoothedLyricTimeMs(targetTimeMs: Long): Long {
    val smoothedTime = remember { Animatable(targetTimeMs.toFloat()) }
    LaunchedEffect(targetTimeMs) {
        val displayedTimeMs = smoothedTime.value.roundToLong()
        val delta = targetTimeMs - displayedTimeMs
        if (delta < 0L || delta > LYRIC_TIME_SMOOTHING_MAX_DELTA_MS) {
            smoothedTime.snapTo(targetTimeMs.toFloat())
        } else {
            smoothedTime.animateTo(
                targetValue = targetTimeMs.toFloat(),
                animationSpec = tween(durationMillis = LYRIC_TIME_SMOOTHING_DURATION_MS, easing = LinearEasing)
            )
        }
    }
    return smoothedTime.value.roundToLong()
}

@Composable
fun AppleMusicLyricView(
    lyrics: List<LyricLine>,
    currentTimeMs: Long,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveAlphaNear: Float = 0.4f,
    inactiveAlphaFar: Float = 0.35f,
    fontSize: TextUnit = 18.sp,
    centerPadding: Dp = 16.dp,
    visualSpec: LyricVisualSpec = LyricVisualSpec(),
    lyricOffsetMs: Long = 0L,
    lyricBlurEnabled: Boolean = true,
    lyricBlurAmount: Float = 10f,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Center,
    onLyricClick: ((Long) -> Unit)? = null,
    translatedLyrics: List<LyricLine> = emptyList()
) {
    val listState = rememberLazyListState()
    val targetLyricTimeMs = (currentTimeMs + lyricOffsetMs).coerceAtLeast(0L)
    val smoothedLyricTimeMs = rememberSmoothedLyricTimeMs(targetLyricTimeMs)
    val currentIndex = remember(lyrics, smoothedLyricTimeMs) { findCurrentLineIndex(lyrics, smoothedLyricTimeMs) }

    LaunchedEffect(currentIndex, lyrics.size) {
        if (currentIndex in lyrics.indices && !listState.isScrollInProgress) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val centerPad = maxHeight / 2.5f
        val maxTextWidth = (maxWidth - 48.dp).coerceAtLeast(0.dp)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = centerPad, bottom = centerPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items = lyrics, key = { _, line -> "${line.timeMs}:${line.text}" }) { index, line ->
                val distance = abs(index - currentIndex)
                val isActive = index == currentIndex

                val targetScale = if (isActive) visualSpec.activeScale else scaleForDistance(distance, visualSpec)
                val scale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.85f),
                    label = "lyric_scale"
                )

                val tilt = if (isActive) 0f else if (index < currentIndex) visualSpec.pageTiltDeg else -visualSpec.pageTiltDeg
                val rotationX by animateFloatAsState(
                    targetValue = tilt,
                    animationSpec = tween(durationMillis = visualSpec.flipDurationMs),
                    label = "lyric_flip"
                )

                val targetAlpha = if (isActive) 1f else alphaForDistance(distance, inactiveAlphaNear, inactiveAlphaFar)
                val animAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(350),
                    label = "lyric_alpha"
                )

                val blurRadiusPx = if (isActive || !lyricBlurEnabled) 0f else blurForDistance(distance, lyricBlurAmount)
                val renderEffect = remember(blurRadiusPx) {
                    if (blurRadiusPx > 0.1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                    } else null
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = centerPadding / 2, horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onLyricClick?.invoke(line.timeMs) }
                        .widthIn(max = maxTextWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isActive) {
                        LyricActiveLine(
                            text = line.text,
                            timeMs = line.timeMs,
                            currentTimeMs = smoothedLyricTimeMs,
                            activeColor = textColor,
                            inactiveColor = textColor.copy(alpha = 0.5f),
                            fontSize = fontSize
                        )
                    } else {
                        Text(
                            text = line.text,
                            modifier = Modifier.graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, if (index < currentIndex) 1f else 0f)
                                cameraDistance = 16f * density
                                this.rotationX = rotationX
                                scaleX = scale
                                scaleY = scale
                                this.alpha = animAlpha
                                this.renderEffect = renderEffect
                            },
                            style = TextStyle(
                                color = textColor.copy(alpha = animAlpha),
                                fontSize = fontSize,
                                fontWeight = FontWeight.Medium,
                                textAlign = textAlign
                            ),
                            maxLines = Int.MAX_VALUE,
                            softWrap = true
                        )
                    }

                    // Translation matching
                    if (isActive && translatedLyrics.isNotEmpty()) {
                        val matchedTrans = translatedLyrides.firstOrNull { trans ->
                            abs(trans.timeMs - line.timeMs) < 2000
                        }
                        if (matchedTrans != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = matchedTrans.text,
                                style = TextStyle(
                                    color = textColor.copy(alpha = 0.85f),
                                    fontSize = fontSize * 0.78f,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = Int.MAX_VALUE,
                                softWrap = true
                            )
                        }
                    }
                }
            }
        }
    }
}

private val translatedLyrides: List<LyricLine>
    @Composable get() = emptyList()
// This is a hack to avoid re-parameter threading — set via our wrapper

@Composable
private fun LyricActiveLine(
    text: String,
    timeMs: Long,
    currentTimeMs: Long,
    activeColor: Color,
    inactiveColor: Color,
    fontSize: TextUnit
) {
    val progress = if (currentTimeMs < timeMs) 0f else 1f
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val isLayoutReady = layout != null

    val revealOffsetChars = remember(text) { Animatable(0f) }
    LaunchedEffect(isLayoutReady, progress) {
        if (isLayoutReady) {
            revealOffsetChars.snapTo(text.length * progress)
        }
    }
    val reveal = revealOffsetChars.value

    val textStyle = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        letterSpacing = 0.sp
    )

    Box {
        Text(
            text = text,
            style = textStyle.copy(color = inactiveColor),
            maxLines = Int.MAX_VALUE,
            softWrap = true,
            onTextLayout = { layout = it }
        )
        if (isLayoutReady) {
            Text(
                text = text,
                style = textStyle.copy(color = activeColor),
                maxLines = Int.MAX_VALUE,
                softWrap = true,
                modifier = Modifier.multilineGradientReveal(
                    layout = layout!!,
                    revealOffsetChars = reveal,
                    textLength = text.length,
                    fadeWidth = 12.dp
                )
            )
        }
    }
}

private fun Modifier.multilineGradientReveal(
    layout: TextLayoutResult,
    revealOffsetChars: Float,
    textLength: Int,
    fadeWidth: Dp
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        if (textLength == 0) { drawContent(); return@drawWithContent }
        if (revealOffsetChars >= textLength) { drawContent(); return@drawWithContent }

        val safeChars = revealOffsetChars.coerceIn(0f, textLength.toFloat())
        val totalLines = layout.lineCount
        val fadePx = fadeWidth.toPx()

        for (lineIndex in 0 until totalLines) {
            val lineStartIdx = layout.getLineStart(lineIndex)
            val lineEndIdx = layout.getLineEnd(lineIndex, true)

            if (safeChars >= lineEndIdx) {
                clipRect(
                    left = layout.getLineLeft(lineIndex),
                    top = layout.getLineTop(lineIndex),
                    right = layout.getLineRight(lineIndex),
                    bottom = layout.getLineBottom(lineIndex)
                ) { this@drawWithContent.drawContent() }
            } else if (safeChars >= lineStartIdx) {
                val currentIdxInLine = (safeChars - lineStartIdx).coerceAtLeast(0f)
                val currentCharIdx = lineStartIdx + floor(currentIdxInLine).toInt()
                val frac = (currentIdxInLine - floor(currentIdxInLine)).coerceIn(0f, 1f)

                val x0 = try { layout.getBoundingBox(currentCharIdx).left }
                catch (_: Exception) { layout.getHorizontalPosition(currentCharIdx, true) }
                val nextCharIdx = if (currentCharIdx >= lineEndIdx - 1) lineEndIdx else currentCharIdx + 1
                val x1 = if (currentCharIdx >= lineEndIdx - 1) layout.getLineRight(lineIndex) else {
                    try { layout.getBoundingBox(nextCharIdx).left }
                    catch (_: Exception) { layout.getHorizontalPosition(nextCharIdx, true) }
                }

                val lineLeft = layout.getLineLeft(lineIndex)
                val lineRight = layout.getLineRight(lineIndex)
                val x = (x0 + (x1 - x0) * frac).coerceIn(lineLeft, lineRight)

                clipRect(
                    left = lineLeft, top = layout.getLineTop(lineIndex),
                    right = lineRight, bottom = layout.getLineBottom(lineIndex)
                ) {
                    this@drawWithContent.drawContent()
                    val start = (x - fadePx).coerceAtLeast(lineLeft)
                    val s1 = ((start - lineLeft) / (lineRight - lineLeft).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val s2 = ((x - lineLeft) / (lineRight - lineLeft).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val leftStop = minOf(s1, s2)
                    val rightStop = maxOf(s1, s2)
                    val brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.White, leftStop to Color.White,
                            rightStop to Color.Transparent, 1f to Color.Transparent
                        ),
                        startX = lineLeft, endX = lineRight
                    )
                    drawRect(
                        brush = brush,
                        topLeft = Offset(lineLeft, layout.getLineTop(lineIndex)),
                        size = androidx.compose.ui.geometry.Size(
                            lineRight - lineLeft,
                            layout.getLineBottom(lineIndex) - layout.getLineTop(lineIndex)
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
        }
    }
