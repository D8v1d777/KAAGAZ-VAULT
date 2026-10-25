package com.kaagazvault

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private data class AmbientParticle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val drift: Float,
    val phase: Float
)

/**
 * A deliberately quiet visual layer: no touch handling, no document-derived values, and no
 * motion when Android's animator duration scale is disabled. It is atmosphere, not status.
 */
@Composable
fun AmbientVaultBackdrop(modifier: Modifier = Modifier) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val reducedMotion = vaultReducedMotionEnabled()
    val particles = remember {
        listOf(
            AmbientParticle(0.12f, 0.18f, 44f, 0.018f, 0.0f),
            AmbientParticle(0.78f, 0.16f, 30f, 0.014f, 1.7f),
            AmbientParticle(0.92f, 0.48f, 54f, 0.012f, 3.2f),
            AmbientParticle(0.18f, 0.68f, 34f, 0.016f, 4.4f),
            AmbientParticle(0.66f, 0.82f, 48f, 0.011f, 5.3f),
            AmbientParticle(0.42f, 0.40f, 20f, 0.010f, 2.4f)
        )
    }

    if (reducedMotion) {
        Canvas(modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.035f),
                        colors.tertiary.copy(alpha = 0.025f)
                    )
                )
            )
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "vault-ambient")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient-phase"
    )
    val colorShift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-color"
    )
    val shiftedPrimary = lerp(colors.primary, colors.tertiary, colorShift.value * 0.22f)
    val shiftedTertiary = lerp(colors.tertiary, colors.primary, colorShift.value * 0.16f)

    Canvas(modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    shiftedPrimary.copy(alpha = 0.045f),
                    Color.Transparent,
                    shiftedTertiary.copy(alpha = 0.04f)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        particles.forEach { particle ->
            val angle = particle.phase + phase.value * (2f * PI.toFloat())
            val x = (particle.x + sin(angle) * particle.drift) * size.width
            val y = (particle.y + sin(angle * 0.72f) * particle.drift * 1.4f) * size.height
            drawCircle(
                color = shiftedPrimary.copy(alpha = 0.035f),
                radius = particle.radius.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}
