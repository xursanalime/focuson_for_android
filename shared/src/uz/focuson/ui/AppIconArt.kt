package uz.focuson.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

/**
 * The Focus On app icon, drawn in code so every size is sharp:
 * a green tile, a timer ring with an amber arc, and the app's own oak inside.
 * [small] = for 16–32 px (tray, tiny menus): a simple sprout instead of the tree.
 * [tile] = false draws only the ring and plant (Android adaptive-icon foreground).
 */
fun DrawScope.drawAppIcon(small: Boolean = false, tile: Boolean = true) {
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    if (tile) {
        val pad = s * 0.04f
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF58A877), Color(0xFF2F6D4C), Color(0xFF1F4E37))),
            Offset(pad, pad), Size(s - 2 * pad, s - 2 * pad), CornerRadius(s * 0.23f),
        )
        // soft light from the top
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), pad, s * 0.55f),
            Offset(pad, pad), Size(s - 2 * pad, s - 2 * pad), CornerRadius(s * 0.23f),
        )
    }
    val k = if (tile) 1f else 0.62f // adaptive foreground must stay in the middle
    val ringR = s * 0.33f * k
    val w = s * (if (small) 0.085f else 0.06f) * k
    val tl = Offset(c.x - ringR, c.y - ringR)
    val box = Size(ringR * 2, ringR * 2)
    drawCircle(Color(0xFFF7F2E6).copy(alpha = 0.95f), ringR, c, style = Stroke(w))
    drawArc(Color(0xFFF2B04A), -90f, 125f, false, tl, box, style = Stroke(w, cap = StrokeCap.Round))

    if (small) {
        // simple white sprout, readable at 16 px
        val stem = s * 0.2f * k
        drawLine(Color(0xFFF7F2E6), Offset(c.x, c.y + stem * 0.7f), Offset(c.x, c.y - stem * 0.1f), w * 0.9f, StrokeCap.Round)
        val l = Path().apply {
            moveTo(c.x, c.y)
            cubicTo(c.x - stem * 0.2f, c.y - stem * 0.75f, c.x - stem * 0.85f, c.y - stem * 0.8f, c.x - stem * 0.95f, c.y - stem * 0.55f)
            cubicTo(c.x - stem * 0.75f, c.y - stem * 0.05f, c.x - stem * 0.3f, c.y + stem * 0.05f, c.x, c.y)
            close()
        }
        val r = Path().apply {
            moveTo(c.x, c.y - stem * 0.15f)
            cubicTo(c.x + stem * 0.15f, c.y - stem * 0.95f, c.x + stem * 0.85f, c.y - stem * 1.05f, c.x + stem * 1.0f, c.y - stem * 0.8f)
            cubicTo(c.x + stem * 0.85f, c.y - stem * 0.2f, c.x + stem * 0.35f, c.y - stem * 0.05f, c.x, c.y - stem * 0.15f)
            close()
        }
        drawPath(l, Color(0xFFB9E6A7))
        drawPath(r, Color(0xFFDDF5CF))
        return
    }
    // the app's own oak, on its little island, inside the ring
    val inner = ringR * 1.95f
    translate(c.x - inner / 2, c.y - inner / 2 - ringR * 0.08f) { drawPlantInBox(inner) }
}

private fun DrawScope.drawPlantInBox(side: Float) {
    val img = PlantCache.get("eman", 1f, false, true, side.toInt().coerceAtLeast(8), this)
    drawImage(img, topLeft = Offset.Zero)
}
