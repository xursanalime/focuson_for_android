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
    private fun line(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** Focus: a sprout inside a ring. */
    val Focus = line("focus") {
        moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
        moveTo(12f, 17f); lineTo(12f, 11f)
        moveTo(12f, 13f); curveTo(9f, 13f, 7.5f, 11.5f, 7.5f, 9f); curveTo(10f, 9f, 12f, 10.5f, 12f, 13f)
        moveTo(12f, 11f); curveTo(12f, 8.5f, 13.8f, 7f, 16.5f, 7f); curveTo(16.5f, 9.5f, 14.5f, 11f, 12f, 11f)
    }

    /** Garden: two trees. */
    val Garden = line("garden") {
        moveTo(8f, 21f); lineTo(8f, 14f)
        moveTo(8f, 3f); lineTo(3.5f, 14f); lineTo(12.5f, 14f); close()
        moveTo(17f, 21f); lineTo(17f, 15f)
        moveTo(17f, 15f); arcTo(4f, 4f, 0f, true, true, 17.01f, 15f)
        moveTo(2f, 21f); lineTo(22f, 21f)
    }

    /** Shop: a bag. */
    val Shop = line("shop") {
        moveTo(5f, 8f); lineTo(19f, 8f); lineTo(18f, 21f); lineTo(6f, 21f); close()
        moveTo(9f, 10f); lineTo(9f, 6f); curveTo(9f, 2f, 15f, 2f, 15f, 6f); lineTo(15f, 10f)
    }

    /** Settings: sliders. */
    val Settings = line("settings") {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 18f); lineTo(20f, 18f)
        moveTo(9f, 4f); lineTo(9f, 8f)
        moveTo(15f, 10f); lineTo(15f, 14f)
        moveTo(7f, 16f); lineTo(7f, 20f)
    }

    /** Limits: an hourglass. */
    val Limits = line("limits") {
        moveTo(6f, 3f); lineTo(18f, 3f)
        moveTo(6f, 21f); lineTo(18f, 21f)
        moveTo(7f, 3f); curveTo(7f, 8f, 12f, 10f, 12f, 12f); curveTo(12f, 14f, 7f, 16f, 7f, 21f)
        moveTo(17f, 3f); curveTo(17f, 8f, 12f, 10f, 12f, 12f); curveTo(12f, 14f, 17f, 16f, 17f, 21f)
        moveTo(9.5f, 18.5f); lineTo(14.5f, 18.5f)
    }

    val ChevronLeft = line("left") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    val ChevronRight = line("right") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
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
