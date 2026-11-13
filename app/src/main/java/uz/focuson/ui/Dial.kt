package uz.focuson.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object DialMath {
    const val MIN = 10
    const val MAX = 120
    const val STEP = 5

    /**
     * Angle in degrees (0 at the top, clockwise) to minutes, rounded to 5.
     * Crossing the top does not jump from 120 to 10 (or back): it stays at the end.
     */
    fun minutesFor(angleDeg: Float, current: Int): Int {
        val raw = ((angleDeg % 360f) + 360f) % 360f / 360f * MAX
        var m = (raw / STEP).roundToInt() * STEP
        if (current >= 100 && m <= 30) m = MAX
        if (current <= 25 && m >= 100) m = MIN
        return m.coerceIn(MIN, MAX)
    }
}

/**
 * The round timer. When [running] is false, dragging around the ring sets minutes.
 * When running, the ring fills up as time passes.
 */
@Composable
fun FocusDial(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    running: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 280.dp,
    center: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(minutes)
    val change by rememberUpdatedState(onMinutesChange)

    Box(
        modifier
            .size(diameter)
            .pointerInput(running) {
                if (running) return@pointerInput
                fun update(pos: Offset) {
                    val dx = pos.x - size.width / 2f
                    val dy = pos.y - size.height / 2f
                    val deg = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                    val m = DialMath.minutesFor(deg, current)
                    if (m != current) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        change(m)
                    }
                }
                detectDragGestures(onDragStart = { update(it) }) { c, _ -> update(c.position) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val r = size.minDimension / 2 - stroke
            val tl = Offset(size.width / 2 - r, size.height / 2 - r)
            val arc = Size(r * 2, r * 2)
            drawCircle(C.chip, r + stroke * 0.5f)
            drawArc(C.line, 0f, 360f, false, tl, arc, style = Stroke(stroke))
            val sweep = if (running) progress * 360f else minutes / DialMath.MAX.toFloat() * 360f
            drawArc(
                C.primary, -90f, sweep, false, tl, arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (!running) {
                val a = Math.toRadians((sweep - 90f).toDouble())
                val knob = Offset(
                    size.width / 2 + r * cos(a).toFloat(),
                    size.height / 2 + r * sin(a).toFloat(),
                )
                drawCircle(C.dialInner, stroke * 0.95f, knob)
                drawCircle(C.primary, stroke * 0.95f, knob, style = Stroke(3.dp.toPx()))
            }
            drawCircle(C.dialInner, r - stroke * 1.4f)
        }
        center()
    }
}
