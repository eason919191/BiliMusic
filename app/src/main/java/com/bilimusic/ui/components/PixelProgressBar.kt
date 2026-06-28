package com.bilimusic.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bilimusic.data.model.ProgressBarStyle

/**
 * Pixel线条风格进度条 - 仿Pixel Launcher音乐小部件样式
 */
@Composable
fun PixelProgressBar(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    style: ProgressBarStyle = ProgressBarStyle.LINEAR,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    when (style) {
        ProgressBarStyle.LINEAR -> {
            Slider(
                value = progress,
                onValueChange = onProgressChange,
                onValueChangeFinished = onProgressChangeFinished,
                colors = SliderDefaults.colors(
                    thumbColor = activeColor,
                    activeTrackColor = activeColor,
                    inactiveTrackColor = inactiveColor
                ),
                modifier = modifier
            )
        }
    }
}

@Composable
private fun PixelStyleProgress(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    var localProgress by remember { mutableFloatStateOf(progress) }
    localProgress = progress

    Slider(
        value = localProgress,
        onValueChange = {
            localProgress = it
            onProgressChange(it)
        },
        onValueChangeFinished = onProgressChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = Color.Transparent,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent
        ),
        modifier = modifier
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
    ) {
        val width = size.width
        val height = size.height
        val barHeight = 3.dp.toPx()
        val y = height / 2

        // Segment count for Pixel style
        val segmentCount = 60
        val segmentWidth = width / segmentCount
        val gapWidth = 1.dp.toPx()
        val segmentBarWidth = segmentWidth - gapWidth

        for (i in 0 until segmentCount) {
            val segmentProgress = i.toFloat() / segmentCount
            val isActive = segmentProgress <= progress
            val x = i * segmentWidth

            drawRoundRect(
                color = if (isActive) activeColor else inactiveColor,
                topLeft = Offset(x, y - barHeight / 2),
                size = Size(segmentBarWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2)
            )
        }

        // Progress dot indicator
        if (progress > 0) {
            val dotX = width * progress
            drawCircle(
                color = activeColor,
                radius = 4.dp.toPx(),
                center = Offset(dotX, y)
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(dotX, y)
            )
        }
    }
}

@Composable
private fun RoundedStyleProgress(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    var localProgress by remember { mutableFloatStateOf(progress) }
    localProgress = progress

    Slider(
        value = localProgress,
        onValueChange = {
            localProgress = it
            onProgressChange(it)
        },
        onValueChangeFinished = onProgressChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = activeColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor
        ),
        modifier = modifier
    )
}

@Composable
private fun DotStyleProgress(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val width = size.width
        val height = size.height
        val y = height / 2
        val dotCount = 40
        val dotSpacing = width / dotCount
        val dotRadius = 2.5.dp.toPx()

        for (i in 0 until dotCount) {
            val dotProgress = i.toFloat() / dotCount
            val isActive = dotProgress <= progress
            val x = i * dotSpacing + dotSpacing / 2

            drawCircle(
                color = if (isActive) activeColor else inactiveColor,
                radius = dotRadius,
                center = Offset(x, y)
            )
        }

        // Active large dot
        if (progress > 0) {
            val dotX = width * progress
            drawCircle(
                color = activeColor,
                radius = dotRadius * 1.8f,
                center = Offset(dotX, y)
            )
        }
    }
}
