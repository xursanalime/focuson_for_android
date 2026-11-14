package uz.focuson.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small line icons drawn for this app. */
object AppIcons {
    /** Outline icons on a 24 grid, 2 px rounded lines; [fill] adds small solid parts. */
    private fun icon(
        name: String,
        fill: (androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit)? = null,
        block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
        if (fill != null) path(fill = SolidColor(Color.Black), pathBuilder = fill)
    }.build()

    /** Focus: a stopwatch with a leaf inside. */
    val Focus = icon("focus", fill = {
        moveTo(12f, 17.2f)
        curveTo(11.6f, 14.2f, 13.2f, 11.6f, 16.2f, 10.9f)
        curveTo(16.6f, 14.1f, 15f, 16.6f, 12f, 17.2f)
        close()
    }) {
        moveTo(12f, 6f); arcTo(7.6f, 7.6f, 0f, true, true, 11.99f, 6f)
        moveTo(9.8f, 2.6f); lineTo(14.2f, 2.6f)
        moveTo(12f, 2.6f); lineTo(12f, 6f)
        moveTo(18.2f, 6.4f); lineTo(19.6f, 5f)
        moveTo(12f, 17.2f); lineTo(12f, 13.4f)
    }

    /** Garden: a leafy tree on the ground. */
    val Garden = icon("garden") {
        moveTo(12f, 3f)
        curveTo(15.2f, 3f, 17.3f, 5.3f, 17.1f, 8.1f)
        curveTo(19.1f, 8.9f, 20.1f, 10.9f, 19.5f, 12.9f)
        curveTo(18.9f, 15f, 17f, 16f, 15f, 16f)
        lineTo(9f, 16f)
        curveTo(7f, 16f, 5.1f, 15f, 4.5f, 12.9f)
        curveTo(3.9f, 10.9f, 4.9f, 8.9f, 6.9f, 8.1f)
        curveTo(6.7f, 5.3f, 8.8f, 3f, 12f, 3f)
        close()
        moveTo(12f, 16f); lineTo(12f, 21f)
        moveTo(12f, 18.6f); lineTo(14.4f, 16.2f)
        moveTo(6.5f, 21f); lineTo(17.5f, 21f)
    }

    /** Shop: a shopping bag. */
    val Shop = icon("shop") {
        moveTo(5.4f, 8f); lineTo(18.6f, 8f); lineTo(17.7f, 19f)
        curveTo(17.6f, 20.2f, 16.7f, 21f, 15.5f, 21f)
        lineTo(8.5f, 21f)
        curveTo(7.3f, 21f, 6.4f, 20.2f, 6.3f, 19f)
        close()
        moveTo(9f, 10.5f); lineTo(9f, 7f)
        curveTo(9f, 5.3f, 10.3f, 4f, 12f, 4f)
        curveTo(13.7f, 4f, 15f, 5.3f, 15f, 7f)
        lineTo(15f, 10.5f)
    }

    /** Settings: three sliders with knobs. */
    val Settings = icon("settings") {
        moveTo(4f, 7f); lineTo(6.8f, 7f)
        moveTo(11.2f, 7f); lineTo(20f, 7f)
        moveTo(9f, 7f); moveTo(11f, 7f); arcTo(2f, 2f, 0f, true, true, 10.99f, 6.99f)
        moveTo(4f, 12f); lineTo(12.8f, 12f)
        moveTo(17.2f, 12f); lineTo(20f, 12f)
        moveTo(17f, 12f); arcTo(2f, 2f, 0f, true, true, 16.99f, 11.99f)
        moveTo(4f, 17f); lineTo(5.8f, 17f)
        moveTo(10.2f, 17f); lineTo(20f, 17f)
        moveTo(10f, 17f); arcTo(2f, 2f, 0f, true, true, 9.99f, 16.99f)
    }

    /** Limits: an hourglass with sand at the bottom. */
    val Limits = icon("limits", fill = {
        moveTo(9.2f, 19.6f)
        curveTo(9.9f, 17.6f, 11f, 16.8f, 12f, 16.4f)
        curveTo(13f, 16.8f, 14.1f, 17.6f, 14.8f, 19.6f)
        close()
    }) {
        moveTo(6f, 3f); lineTo(18f, 3f)
        moveTo(6f, 21f); lineTo(18f, 21f)
        moveTo(7.5f, 3f); curveTo(7.5f, 8f, 12f, 9.6f, 12f, 12f); curveTo(12f, 14.4f, 7.5f, 16f, 7.5f, 21f)
        moveTo(16.5f, 3f); curveTo(16.5f, 8f, 12f, 9.6f, 12f, 12f); curveTo(12f, 14.4f, 16.5f, 16f, 16.5f, 21f)
    }

    val ChevronLeft = icon("left") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    val ChevronRight = icon("right") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
}

/** A gold coin. */
@Composable
fun CoinIcon(size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = Offset(r, r)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE08A), Color(0xFFE9A23B), Color(0xFFB8741B)), Offset(r * 0.7f, r * 0.7f), r * 1.4f), r, c)
        drawCircle(Color(0xFFFFF1C4).copy(alpha = 0.7f), r * 0.68f, c, style = Stroke(r * 0.12f))
        drawLine(Color(0xFFB8741B), Offset(r, r * 0.55f), Offset(r, r * 1.45f), r * 0.18f, StrokeCap.Round)
    }
}
