package com.kidfromjupiter.shareby.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A horizontal squiggly progress bar.
 * [progress] in 0f..1f for determinate, or -1f for indeterminate (full-width animated wave).
 */
@Composable
fun SquigglyLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color,
    enabled: Boolean = true,
    strokeWidth: Dp = 3.dp,
    amplitude: Dp = 2.dp,
    wavelength: Dp = 24.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "squiggle_linear")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_linear"
    )

    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val ampPx = if (enabled) amplitude.toPx() else 0f
        val wavePx = wavelength.toPx()
        val cy = size.height / 2f
        val indeterminate = progress < 0f
        val endX = if (indeterminate) size.width else (size.width * progress).coerceIn(0f, size.width)

        if (endX <= 0f) return@Canvas

        val steps = (endX / 1.5f).toInt().coerceIn(4, 1000)
        val path = Path()
        for (i in 0..steps) {
            val x = endX * i.toFloat() / steps
            val y = cy + ampPx * sin(x / wavePx * (2f * PI).toFloat() + phase)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
