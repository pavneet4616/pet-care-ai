package com.petcare.companionlite.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Springy press feedback. Pass the same [interactionSource] to the clickable/Button it decorates. */
fun Modifier.bouncyPress(interactionSource: MutableInteractionSource, pressedScale: Float = 0.94f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bouncyPress",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Fades and slides content up once when it first appears; [index] staggers list items. */
fun Modifier.popIn(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 420, delayMillis = (index.coerceIn(0, 8)) * 60, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** Gentle idle "breathing" bob, for mascots and hero emoji. */
fun Modifier.gentleBob(amplitude: Dp = 4.dp, periodMillis: Int = 1800): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "gentleBob")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gentleBobOffset",
    )
    graphicsLayer { translationY = offset * amplitude.toPx() }
}

/** A short wiggle each time [trigger] changes (e.g. a task was logged). */
fun Modifier.wiggleOn(trigger: Any?): Modifier = composed {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        for (angle in listOf(-8f, 7f, -5f, 3f, 0f)) rotation.animateTo(angle, tween(70, easing = LinearEasing))
    }
    graphicsLayer { rotationZ = rotation.value }
}

/**
 * Celebration burst of paw prints radiating from the centre. Place it inside a Box that overlays the
 * element being celebrated; it plays once whenever [trigger] changes to a non-null value.
 */
@Composable
fun BoxScope.PawBurst(trigger: Any?, emoji: String = "🐾", count: Int = 8, radius: Dp = 56.dp) {
    if (trigger == null) return
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    if (progress.value >= 1f) return
    repeat(count) { i ->
        val angle = (2 * Math.PI * i / count).toFloat()
        Text(
            emoji,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val distance = radius.toPx() * progress.value
                    translationX = cos(angle) * distance
                    translationY = sin(angle) * distance
                    alpha = 1f - progress.value
                    scaleX = 0.6f + progress.value * 0.6f
                    scaleY = scaleX
                    rotationZ = angle * 57.3f
                },
        )
    }
}

/** Emoji avatar with a gentle bob, used as the fallback when a pet has no readable photo. */
@Composable
fun BobbingEmoji(emoji: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).gentleBob(), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.62f).sp)
    }
}
