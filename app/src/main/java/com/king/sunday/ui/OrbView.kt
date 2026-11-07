package com.king.sunday.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Sunday's face for now: a living orb. Colour and speed follow her emotion, the rings ripple with her voice
 * while speaking and with your mic level while listening, particles orbit faster while she thinks.
 */
@Composable
fun OrbView(emotion: Emotion, mode: Mode, level: Float, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "orb")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "phase"
    )
    val pulse by inf.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val c1 by animateColorAsState(emotion.c1, tween(700), label = "c1")
    val c2 by animateColorAsState(emotion.c2, tween(700), label = "c2")
    val energy by animateFloatAsState(
        when (mode) {
            Mode.LISTENING -> 0.35f + level * 0.65f
            Mode.SPEAKING -> 0.75f
            Mode.THINKING -> 0.5f
            Mode.IDLE -> 0.12f
        },
        tween(250),
        label = "energy"
    )
    val spin = if (mode == Mode.THINKING) 2.2f else 1f

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val centre = Offset(cx, cy)
        val base = min(size.width, size.height) / 3.3f
        val r = base * pulse * (1f + energy * 0.10f)
        val twoPi = (2 * PI).toFloat()

        // soft outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c1.copy(alpha = 0.35f), Color.Transparent),
                center = centre,
                radius = r * 2.4f
            ),
            radius = r * 2.4f,
            center = centre
        )

        // rippling rings
        for (ring in 0 until 3) {
            val path = Path()
            val steps = 120
            val rr = r * (1.25f + ring * 0.18f)
            val dir = if (ring % 2 == 0) 1f else -1f
            for (s in 0..steps) {
                val a = s / steps.toFloat() * twoPi
                val wob = sin(a * (5 + ring * 2) + phase * emotion.speed * (ring + 1) * dir) *
                    r * 0.045f * (0.3f + energy * 2f)
                val x = cx + (rr + wob) * cos(a)
                val y = cy + (rr + wob) * sin(a)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, c2.copy(alpha = 0.55f - ring * 0.15f), style = Stroke(width = 2.5f))
        }

        // orbiting particles
        val n = 36
        for (i in 0 until n) {
            val a = i / n.toFloat() * twoPi + phase * emotion.speed * 0.6f * spin
            val rad = r * (1.7f + 0.12f * sin(phase * 3f + i) * (0.5f + energy))
            drawCircle(
                color = c2.copy(alpha = 0.7f),
                radius = 2.5f + 2f * energy,
                center = Offset(cx + rad * cos(a), cy + rad * sin(a))
            )
        }

        // glowing core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.95f), c2, c1, c1.copy(alpha = 0.2f)),
                center = Offset(cx - r * 0.25f, cy - r * 0.3f),
                radius = r * 1.15f
            ),
            radius = r,
            center = centre
        )
    }
}
