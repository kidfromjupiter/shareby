package com.kidfromjupiter.shareby.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class DeviceType {
    PHONE, TABLET, LAPTOP, DESKTOP
}

enum class ConnectionState {
    IDLE, CONNECTING, TRANSFERRING, COMPLETE, FAILED
}

data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val color: Color
)

@Composable
fun DeviceItem(
    device: Device,
    connectionState: ConnectionState = ConnectionState.IDLE,
    progress: Float = 0f,
    onTap: (Device) -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (device.type) {
        DeviceType.PHONE -> Icons.Default.PhoneAndroid
        DeviceType.TABLET -> Icons.Default.Tablet
        DeviceType.LAPTOP -> Icons.Default.Laptop
        DeviceType.DESKTOP -> Icons.Default.Computer
    }

    Column(
        modifier = modifier
            .padding(8.dp)
            .clickable(
                enabled = connectionState == ConnectionState.IDLE || connectionState == ConnectionState.FAILED
            ) { onTap(device) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(84.dp)
        ) {
            // Squiggly ring: full animated for CONNECTING, progress arc for TRANSFERRING/COMPLETE
            when (connectionState) {
                ConnectionState.CONNECTING -> SquigglyCircularProgress(
                    progress = -1f,
                    modifier = Modifier.size(84.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                ConnectionState.TRANSFERRING -> SquigglyCircularProgress(
                    progress = progress,
                    modifier = Modifier.size(84.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                ConnectionState.COMPLETE -> SquigglyCircularProgress(
                    progress = 1f,
                    modifier = Modifier.size(84.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                ConnectionState.FAILED -> SquigglyCircularProgress(
                    progress = 1f,
                    modifier = Modifier.size(84.dp),
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.errorContainer,
                )
                else -> {}
            }

            // Avatar circle
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        when (connectionState) {
                            ConnectionState.COMPLETE -> MaterialTheme.colorScheme.tertiary
                            ConnectionState.FAILED -> MaterialTheme.colorScheme.error
                            ConnectionState.CONNECTING,
                            ConnectionState.TRANSFERRING -> MaterialTheme.colorScheme.primary
                            else -> device.color
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (connectionState == ConnectionState.COMPLETE) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Complete",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                } else if (connectionState == ConnectionState.FAILED) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Failed",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                } else {
                    Icon(
                        icon,
                        contentDescription = device.type.name,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = device.name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        when (connectionState) {
            ConnectionState.CONNECTING -> Text(
                text = "Connecting...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            ConnectionState.TRANSFERRING -> Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            ConnectionState.COMPLETE -> Text(
                text = "Complete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            ConnectionState.FAILED -> Text(
                text = "Failed — tap to retry",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            else -> {}
        }
    }
}

/**
 * A squiggly circular progress ring drawn on Canvas.
 * [progress] = -1f for an indeterminate full spinning ring;
 * [progress] = 0f..1f for a determinate arc swept from the top.
 * The wave phase animates continuously so the squiggle looks alive.
 */
@Composable
private fun SquigglyCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp = 3.dp,
    amplitude: Dp = 1.5.dp,
    wavelength: Dp = 18.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "squiggle")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val amplitudePx = amplitude.toPx()
        val wavelengthPx = wavelength.toPx()
        // Shrink radius so the wave never clips outside the bounds
        val radius = (size.minDimension / 2f) - strokePx - amplitudePx
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Background track ring — removed (transparent)

        val indeterminate = progress < 0f
        val sweepDeg = if (indeterminate) 360f else (progress * 360f).coerceIn(0f, 360f)
        if (sweepDeg <= 0f) return@Canvas

        val startAngleRad = (-PI / 2.0).toFloat() // top of circle
        val sweepRad = (sweepDeg * PI / 180.0).toFloat()
        // ~1 sample per 1.5px of arc for a smooth curve
        val steps = (radius * sweepRad / 1.5f).toInt().coerceIn(4, 500)

        val path = Path()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angleRad = startAngleRad + sweepRad * t
            val arcDist = radius * (sweepRad * t)
            val wave = amplitudePx * sin(arcDist / wavelengthPx * (2f * PI).toFloat() + phase)
            val r = radius + wave
            val x = cx + r * cos(angleRad)
            val y = cy + r * sin(angleRad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        )
    }
}

