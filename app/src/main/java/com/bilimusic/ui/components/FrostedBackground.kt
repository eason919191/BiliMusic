package com.bilimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Frosted glass background using Haze blur.
 *
 * Applies a blurred, tinted background with a subtle top-edge highlight
 * to create the visual appearance of frosted glass.
 *
 * Note: the actual behind-content blur is provided by the parent's
 * [dev.chrisbanes.haze.HazeState] via [hazeSource]. This composable
 * renders the visual tint/edge layers that sit on top of the blur.
 */
@Composable
fun FrostedBackground(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    tintAlpha: Float = 0.72f,
    shape: RoundedCornerShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    showEdgeHighlight: Boolean = true,
    content: @Composable () -> Unit
) {
    val blendedTint = tintColor.copy(alpha = tintAlpha)

    Box(modifier = modifier) {
        // Tinted background
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(blendedTint)
        )

        // Top edge highlight for glass depth
        if (showEdgeHighlight) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 3f
                        )
                    )
            )
        }

        // Content on top
        content()
    }
}
