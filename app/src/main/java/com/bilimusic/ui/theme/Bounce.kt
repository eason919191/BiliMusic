package com.bilimusic.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntOffset

/** 运行时可观察的回弹配置 */
object AnimationConfig {
    val dampingRatio = mutableFloatStateOf(0.7f)
    val stiffness = mutableFloatStateOf(260f)
    val springEnabled = mutableStateOf(true)
}

/** 从当前配置创建回弹 spring，关闭时退回 tween */
fun bounceSpring(): FiniteAnimationSpec<Float> {
    if (!AnimationConfig.springEnabled.value) return tween(300)
    return spring(
        dampingRatio = AnimationConfig.dampingRatio.floatValue,
        stiffness = AnimationConfig.stiffness.floatValue
    )
}

fun bounceGentle(): FiniteAnimationSpec<Float> {
    if (!AnimationConfig.springEnabled.value) return tween(300)
    return spring(
        dampingRatio = (AnimationConfig.dampingRatio.floatValue + 0.2f).coerceAtMost(1.5f),
        stiffness = AnimationConfig.stiffness.floatValue
    )
}

fun bounceSpringIntOffset(): FiniteAnimationSpec<IntOffset> {
    if (!AnimationConfig.springEnabled.value) return tween(300)
    return spring(
        dampingRatio = AnimationConfig.dampingRatio.floatValue,
        stiffness = AnimationConfig.stiffness.floatValue
    )
}
