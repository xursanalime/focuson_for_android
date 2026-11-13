package uz.focuson.ui

import android.util.LruCache
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** Plants are costly to draw, so each (species, growth step, size) is drawn once and kept. */
object PlantCache {
    private const val STEPS = 40
    private val cache = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(id: String, growth: Float, withered: Boolean, ground: Boolean, sizePx: Int, density: Density): ImageBitmap {
        val step = (growth.coerceIn(0f, 1f) * STEPS).roundToInt()
        val px = sizePx.coerceAtLeast(8)
        val key = "$id/$step/$withered/$ground/$px"
        cache.get(key)?.let { return it }
        val bmp = ImageBitmap(px, px)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bmp), Size(px.toFloat(), px.toFloat())) {
            drawPlant(id, step / STEPS.toFloat(), withered, ground)
        }
        cache.put(key, bmp)
        return bmp
    }
}

/** Draws a cached plant picture into a square of side [side] px whose top-left is [topLeft]. */
fun DrawScope.drawCachedPlant(
    id: String, growth: Float, withered: Boolean, ground: Boolean, topLeft: Offset, side: Float,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
) {
    val px = side.roundToInt()
    val img = PlantCache.get(id, growth, withered, ground, px, this)
    drawImage(
        img,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(px, px),
        colorFilter = colorFilter,
    )
}

/** A plant. [growth] goes from 0 (a seed) to 1 (fully grown). */
@Composable
fun PlantView(
    speciesId: String,
    growth: Float,
    modifier: Modifier = Modifier,
    withered: Boolean = false,
    sway: Boolean = false,
    showGround: Boolean = true,
) {
    val angle = if (sway) {
        val t = rememberInfiniteTransition(label = "sway")
        val a by t.animateFloat(
            initialValue = -1.4f, targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
            label = "angle",
        )
        a
    } else 0f
    Canvas(modifier) {
        val side = size.minDimension
        val tl = Offset((size.width - side) / 2, (size.height - side) / 2)
        rotate(angle, Offset(size.width / 2, tl.y + side * 0.84f)) {
            drawCachedPlant(speciesId, growth, withered, showGround, tl, side)
        }
    }
}
