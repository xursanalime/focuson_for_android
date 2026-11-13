package uz.focuson.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import uz.focuson.data.GardenTheme
import uz.focuson.data.Period
import uz.focuson.data.SessionRecord
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The garden island, drawn in code for each GardenTheme.
 * Grid coordinates: u = row (0 at the back-left edge), v = column (0 at the back-right edge),
 * both 0..k. Iso.p(u, v) turns them into screen points.
 */

class Iso(val cx: Float, val top: Float, val bw: Float, val k: Int) {
    val tw = bw / k
    val th = tw / 2
    fun p(u: Float, v: Float) = Offset(cx + (v - u) * tw / 2, top + (v + u) * th / 2)
    fun center(r: Int, c: Int) = p(r + 0.5f, c + 0.5f)
    val back get() = p(0f, 0f)
    val left get() = p(k.toFloat(), 0f)
    val right get() = p(0f, k.toFloat())
    val front get() = p(k.toFloat(), k.toFloat())
    fun diamond(): Path = Path().apply {
        moveTo(back.x, back.y); lineTo(right.x, right.y); lineTo(front.x, front.y); lineTo(left.x, left.y); close()
    }
}

/** Which squares a theme uses for water, paths, sand… (no plants there). */
object GardenLayout {
    fun blocked(theme: GardenTheme, k: Int): Set<Int> {
        val out = HashSet<Int>()
        fun add(r: Int, c: Int) { if (r in 0 until k && c in 0 until k) out += r * k + c }
        when (theme) {
            GardenTheme.MEADOW -> Unit
            GardenTheme.FLOWERS -> for (r in 0 until k) add(r, k / 2)
            GardenTheme.LAKE -> for (r in 0 until k) for (c in 0 until k) {
                if (inPond(r + 0.5f, c + 0.5f, k, 1.12f)) add(r, c)
            }
            GardenTheme.MOUNTAIN -> {
                for (r in 0 until k) for (c in 0 until k) if (r + c < maxOf(2, (k * 0.32f).roundToInt())) add(r, c)
                val rs = streamRow(k)
                for (c in 0 until k) if (c >= rs) add(rs, c)
            }
            GardenTheme.ZEN -> {
                val s0 = sandStart(k)
                for (r in 0 until k) for (c in s0 until k) add(r, c)
                val rs = (k * 0.62f).toInt()
                for (c in 0 until s0) add(rs, c)
                add(k / 4, s0 - 1)
            }
            GardenTheme.NIGHT -> {
                for (i in 0 until k) add(i, i)
                lanternTiles(k).forEach { (r, c) -> add(r, c) }
            }
            GardenTheme.SKY -> {
                val c0 = k / 2
                for (r in k - 2 until k) for (c in c0 - 1..c0) add(r, c)
            }
            GardenTheme.GOLDEN -> for (r in 0 until k) add(r, k / 2)
        }
        return out
    }

    fun pondCenter(k: Int) = Offset(0.68f * k, 0.3f * k)
    fun inPond(u: Float, v: Float, k: Int, grow: Float = 1f): Boolean {
        val c = pondCenter(k)
        val du = (u - c.x) / (0.27f * k * grow)
        val dv = (v - c.y) / (0.24f * k * grow)
        return du * du + dv * dv <= 1f
    }

    fun streamRow(k: Int) = maxOf(1, (k * 0.28f).roundToInt())

    /** Zen: the raked sand starts at this column. */
    fun sandStart(k: Int) = kotlin.math.ceil(k * 0.62f).toInt()

    fun zenStones(k: Int): List<Offset> {
        val s0 = sandStart(k)
        val w = (k - s0).toFloat()
        return listOf(Offset(0.25f * k, s0 + 0.55f * w), Offset(0.6f * k, s0 + 0.35f * w), Offset(0.84f * k, s0 + 0.7f * w))
    }

    fun lanternTiles(k: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var i = 1
        while (i < k - 1) {
            out += (i to i + 1)
            i += 3
        }
        return out
    }

    /** Grid size: big enough for the period, grown further until the plants fit. */
    fun grid(theme: GardenTheme, p: Period, plants: Int): Int {
        var k = GardenSize.baseGrid(p)
        val max = GardenSize.maxGrid(p)
        while (k < max && k * k - blocked(theme, k).size < plants) k++
        return k
    }
}

/** How deep the island's side (or underside) is, as a part of its width. */
fun sideDepth(theme: GardenTheme) = when (theme) {
    GardenTheme.SKY -> 0.34f
    GardenTheme.MOUNTAIN -> 0.11f
    else -> 0.075f
}

private class Rand(seed: Long) {
    private val r = java.util.Random(seed)
    fun f() = r.nextFloat()
    fun f(a: Float, b: Float) = a + (b - a) * r.nextFloat()
    fun i(n: Int) = r.nextInt(n)
}

private fun hex(v: Long) = Color(v)

/** Draws the full garden: sky, island, features, plants (back to front) and light effects. */
fun DrawScope.drawGarden(
    theme: GardenTheme,
    k: Int,
    seed: Int,
    plants: List<Pair<Int, SessionRecord>>,
    widthFraction: Float,
) {
    val bw = size.width * widthFraction
    val depth = bw * sideDepth(theme)
    val bottomRoom = if (theme == GardenTheme.SKY) depth + size.width * 0.04f else depth + 8f
    val top = size.height - bw / 2 - bottomRoom
    val iso = Iso(size.width / 2, top, bw, k)
    val rnd = Rand(seed.toLong() * 31 + theme.ordinal)

    drawSky(theme, iso, rnd)
    drawSides(theme, iso, depth, rnd)
    drawTop(theme, iso, rnd)
    drawFlatFeatures(theme, iso, rnd)

    // everything that stands up, sorted back to front
    val props = ArrayList<Pair<Float, DrawScope.() -> Unit>>()
    addProps(theme, iso, props, rnd, plants.map { it.first }.toSet())
    val tint = when (theme) {
        GardenTheme.NIGHT -> ColorFilter.tint(hex(0xFF8E9CC8), BlendMode.Modulate)
        GardenTheme.GOLDEN -> ColorFilter.tint(hex(0xFFFFE6BE), BlendMode.Modulate)
        else -> null
    }
    plants.forEach { (t, r) ->
        val row = t / k
        val col = t % k
        props += (row + col + 1f) to {
            val c = iso.center(row, col)
            val side = iso.tw * 1.65f
            drawOval(Color.Black.copy(alpha = 0.16f), Offset(c.x - iso.tw * 0.28f, c.y - iso.th * 0.18f), Size(iso.tw * 0.56f, iso.th * 0.42f))
            drawCachedPlant(
                r.speciesId, if (r.success) 1f else 0.6f, !r.success, false,
                Offset(c.x - side / 2, c.y - side * 0.84f), side, tint,
            )
        }
    }
    props.sortedBy { it.first }.forEach { it.second(this) }

    drawEffects(theme, iso, rnd, depth)
}

// ------------------------------------------------------------------ sky

private fun DrawScope.cloud(at: Offset, w: Float, alpha: Float = 0.85f, shade: Color = hex(0xFFDCE8F2)) {
    val h = w * 0.32f
    drawOval(shade.copy(alpha = alpha * 0.8f), Offset(at.x - w / 2, at.y - h * 0.1f), Size(w, h * 0.55f))
    listOf(-0.3f to 0.5f, -0.05f to 0.75f, 0.22f to 0.6f, 0.38f to 0.42f).forEach { (dx, r) ->
        drawCircle(Color.White.copy(alpha = alpha), h * r, Offset(at.x + dx * w, at.y - h * r * 0.35f))
    }
}

private fun DrawScope.mountains(baseY: Float, height: Float, color: Color, rnd: Rand, snow: Boolean) {
    val path = Path().apply {
        moveTo(0f, baseY)
        var x = 0f
        var up = true
        val peaks = ArrayList<Offset>()
        while (x < size.width) {
            val step = size.width * rnd.f(0.1f, 0.2f)
            x += step
            val y = if (up) baseY - height * rnd.f(0.55f, 1f) else baseY - height * rnd.f(0.1f, 0.35f)
            lineTo(x, y)
            if (up) peaks += Offset(x, y)
            up = !up
        }
        lineTo(size.width, baseY)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
        if (snow) {
            peaks.forEach { pk ->
                val cap = Path().apply {
                    moveTo(pk.x, pk.y)
                    lineTo(pk.x - height * 0.16f, pk.y + height * 0.16f)
                    lineTo(pk.x - height * 0.05f, pk.y + height * 0.12f)
                    lineTo(pk.x + height * 0.02f, pk.y + height * 0.18f)
                    lineTo(pk.x + height * 0.14f, pk.y + height * 0.14f)
                    close()
                }
                drawPath(cap, Color.White.copy(alpha = 0.85f))
            }
        }
    }
    drawPath(path, color)
}

private fun DrawScope.drawSky(theme: GardenTheme, iso: Iso, rnd: Rand) {
    val w = size.width
    val h = size.height
    when (theme) {
        GardenTheme.MEADOW -> {
            drawRect(Brush.verticalGradient(listOf(hex(0xFFD6EEF0), hex(0xFFEAF4E6), hex(0xFFF5F1E4))))
            cloud(Offset(w * 0.22f, h * 0.14f), w * 0.22f)
            cloud(Offset(w * 0.78f, h * 0.09f), w * 0.16f)
        }
        GardenTheme.FLOWERS -> {
            drawRect(Brush.verticalGradient(listOf(hex(0xFFFDE3CF), hex(0xFFF9EEDC), hex(0xFFEFF5E4))))
            drawCircle(Brush.radialGradient(listOf(hex(0xFFFFE9A8), Color.Transparent), Offset(w * 0.82f, h * 0.12f), w * 0.35f), w * 0.35f, Offset(w * 0.82f, h * 0.12f))
            drawCircle(hex(0xFFFFF3C9), w * 0.05f, Offset(w * 0.82f, h * 0.12f))
            cloud(Offset(w * 0.25f, h * 0.15f), w * 0.2f)
        }
        GardenTheme.LAKE -> {
            drawRect(Brush.verticalGradient(listOf(hex(0xFFBFE2F2), hex(0xFFDDF0F3), hex(0xFFEFF6EE))))
            mountains(h * 0.42f, h * 0.14f, hex(0xFFB9D6CF).copy(alpha = 0.7f), rnd, false)
            cloud(Offset(w * 0.2f, h * 0.12f), w * 0.24f)
            cloud(Offset(w * 0.7f, h * 0.2f), w * 0.18f)
        }
        GardenTheme.MOUNTAIN -> {
            drawRect(Brush.verticalGradient(listOf(hex(0xFFCBD9E4), hex(0xFFE4EAEC), hex(0xFFEEF0EA))))
            mountains(h * 0.36f, h * 0.24f, hex(0xFFA9B9C9), rnd, true)
            mountains(h * 0.44f, h * 0.16f, hex(0xFF8FA4B6), rnd, false)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), h * 0.34f, h * 0.52f))
        }
        GardenTheme.ZEN -> {
            drawRect(Brush.verticalGradient(listOf(hex(0xFFF6CDB8), hex(0xFFF8E2D0), hex(0xFFF5EEE2))))
            drawCircle(hex(0xFFEE8C6E).copy(alpha = 0.75f), w * 0.12f, Offset(w * 0.72f, h * 0.17f))
            repeat(3) { i ->
                val y = h * (0.12f + i * 0.05f)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(w * (0.08f + i * 0.1f), y), Offset(w * (0.42f + i * 0.08f), y), h * 0.008f, StrokeCap.Round)
            }
            mountains(h * 0.44f, h * 0.12f, hex(0xFFE3B9A7).copy(alpha = 0.6f), rnd, false)
        }
        GardenTheme.NIGHT -> {
            drawRect(Brush.verticalGradient(listOf(hex(0xFF0D1730), hex(0xFF1B2B4D), hex(0xFF2B3E63))))
            repeat(70) {
                val p = Offset(rnd.f() * w, rnd.f() * h * 0.55f)
                drawCircle(Color.White.copy(alpha = rnd.f(0.3f, 0.95f)), rnd.f(0.6f, 1.8f) * w / 400f, p)
            }
            val moon = Offset(w * 0.8f, h * 0.13f)
            drawCircle(Brush.radialGradient(listOf(hex(0xFFFFF4D6).copy(alpha = 0.35f), Color.Transparent), moon, w * 0.18f), w * 0.18f, moon)
            drawCircle(hex(0xFFFFF6DE), w * 0.055f, moon)
            drawCircle(hex(0xFFE9DFC0), w * 0.012f, Offset(moon.x - w * 0.015f, moon.y - w * 0.01f))
            drawCircle(hex(0xFFE9DFC0), w * 0.008f, Offset(moon.x + w * 0.02f, moon.y + w * 0.015f))
            mountains(h * 0.46f, h * 0.12f, hex(0xFF16233F), rnd, false)
        }
        GardenTheme.SKY -> {
            drawRect(Brush.verticalGradient(listOf(hex(0xFF7FC1EC), hex(0xFFB5DCF5), hex(0xFFE3F2FB))))
            cloud(Offset(w * 0.15f, h * 0.12f), w * 0.26f, 0.9f)
            cloud(Offset(w * 0.75f, h * 0.08f), w * 0.2f, 0.8f)
            cloud(Offset(w * 0.9f, h * 0.45f), w * 0.22f, 0.7f)
            cloud(Offset(w * 0.08f, h * 0.55f), w * 0.2f, 0.7f)
            repeat(4) {
                val b = Offset(w * rnd.f(0.3f, 0.7f), h * rnd.f(0.08f, 0.25f))
                val s = w * 0.014f
                drawPath(Path().apply {
                    moveTo(b.x - s, b.y - s * 0.3f); quadraticTo(b.x - s * 0.5f, b.y - s * 0.6f, b.x, b.y)
                    quadraticTo(b.x + s * 0.5f, b.y - s * 0.6f, b.x + s, b.y - s * 0.3f)
                }, hex(0xFF39566E), style = Stroke(w * 0.003f, cap = StrokeCap.Round))
            }
        }
        GardenTheme.GOLDEN -> {
            drawRect(Brush.verticalGradient(listOf(hex(0xFFF29B5C), hex(0xFFF8C783), hex(0xFFFBE6BE), hex(0xFFF7EBD3))))
            val sun = Offset(w * 0.3f, h * 0.3f)
            drawCircle(Brush.radialGradient(listOf(hex(0xFFFFF0B8).copy(alpha = 0.9f), Color.Transparent), sun, w * 0.45f), w * 0.45f, sun)
            drawCircle(hex(0xFFFFF4C8), w * 0.07f, sun)
            mountains(h * 0.42f, h * 0.2f, hex(0xFFC8826A).copy(alpha = 0.75f), rnd, false)
            mountains(h * 0.48f, h * 0.12f, hex(0xFFA9665A).copy(alpha = 0.8f), rnd, false)
            repeat(6) { i ->
                val ang = (10f + i * 16f) * PI.toFloat() / 180f
                val len = w * 1.3f
                val path = Path().apply {
                    moveTo(sun.x, sun.y)
                    lineTo(sun.x + cos(ang) * len, sun.y + sin(ang) * len)
                    lineTo(sun.x + cos(ang + 0.06f) * len, sun.y + sin(ang + 0.06f) * len)
                    close()
                }
                drawPath(path, hex(0xFFFFF1C2).copy(alpha = 0.1f))
            }
        }
    }
}

// ------------------------------------------------------------------ sides

private fun DrawScope.drawSides(theme: GardenTheme, iso: Iso, depth: Float, rnd: Rand) {
    val l = iso.left
    val f = iso.front
    val r = iso.right
    if (theme == GardenTheme.SKY) {
        drawUnderside(iso, depth, rnd)
        return
    }
    // ground shadow
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), Offset(f.x, f.y + depth * 0.7f), iso.bw * 0.32f),
        Offset(f.x - iso.bw * 0.34f, f.y + depth * 0.7f - iso.bw * 0.05f), Size(iso.bw * 0.68f, iso.bw * 0.1f),
    )
    val leftFace = Path().apply { moveTo(l.x, l.y); lineTo(f.x, f.y); lineTo(f.x, f.y + depth); lineTo(l.x, l.y + depth); close() }
    val rightFace = Path().apply { moveTo(f.x, f.y); lineTo(r.x, r.y); lineTo(r.x, r.y + depth); lineTo(f.x, f.y + depth); close() }
    when (theme) {
        GardenTheme.MOUNTAIN -> {
            drawPath(leftFace, Brush.verticalGradient(listOf(hex(0xFF9A9C9F), hex(0xFF6C6F74)), l.y, f.y + depth))
            drawPath(rightFace, Brush.verticalGradient(listOf(hex(0xFF7A7D82), hex(0xFF4F5257)), r.y, f.y + depth))
            // cracks and ledges
            clipPath(leftFace) { rockCracks(l, f, depth, rnd, hex(0xFF56595E)) }
            clipPath(rightFace) { rockCracks(f, r, depth, rnd, hex(0xFF3E4146)) }
        }
        GardenTheme.ZEN -> {
            drawPath(leftFace, hex(0xFFC9C0AF))
            drawPath(rightFace, hex(0xFFA79E8E))
            stoneBlocks(l, f, depth, hex(0xFF8F8676))
            stoneBlocks(f, r, depth, hex(0xFF7A7263))
        }
        else -> {
            val warm = theme == GardenTheme.GOLDEN
            val night = theme == GardenTheme.NIGHT
            val lt = if (night) listOf(hex(0xFF4A3B33), hex(0xFF2E241F)) else if (warm) listOf(hex(0xFF9A6A40), hex(0xFF6A4528)) else listOf(hex(0xFF8A6240), hex(0xFF5E4029))
            val rt = if (night) listOf(hex(0xFF3A2E28), hex(0xFF221A16)) else if (warm) listOf(hex(0xFF7A5232), hex(0xFF4E321D)) else listOf(hex(0xFF6E4C31), hex(0xFF47301E))
            drawPath(leftFace, Brush.verticalGradient(lt, l.y, f.y + depth))
            drawPath(rightFace, Brush.verticalGradient(rt, r.y, f.y + depth))
            for (layer in 1..2) {
                val t = layer / 3f
                drawLine(Color.Black.copy(alpha = 0.18f), Offset(l.x, l.y + depth * t), Offset(f.x, f.y + depth * t), 2f)
                drawLine(Color.Black.copy(alpha = 0.22f), Offset(f.x, f.y + depth * t), Offset(r.x, r.y + depth * t), 2f)
            }
            // pebbles and roots in the soil
            clipPath(leftFace) { soilBits(l, f, depth, rnd, if (warm) hex(0xFFE7C15A) else hex(0xFFB89E80)) }
            clipPath(rightFace) { soilBits(f, r, depth, rnd, if (warm) hex(0xFFD9AE45) else hex(0xFF9C8468)) }
        }
    }
    // grass lip hanging over the edge
    if (theme != GardenTheme.ZEN) {
        val lip = grassColors(theme).last()
        listOf(l to f, f to r).forEach { (a, b) ->
            val n = (iso.k * 6)
            val path = Path().apply {
                moveTo(a.x, a.y)
                for (i in 0..n) {
                    val t = i / n.toFloat()
                    val x = a.x + (b.x - a.x) * t
                    val y = a.y + (b.y - a.y) * t
                    lineTo(x, y + depth * (if (i % 2 == 0) 0.12f else rnd.f(0.18f, 0.3f)))
                }
                lineTo(b.x, b.y)
                close()
            }
            drawPath(path, lip)
        }
    }
}

private fun DrawScope.rockCracks(a: Offset, b: Offset, depth: Float, rnd: Rand, col: Color) {
    repeat(9) {
        val t = rnd.f()
        val x = a.x + (b.x - a.x) * t
        val y = a.y + (b.y - a.y) * t + depth * rnd.f(0.15f, 0.85f)
        val len = depth * rnd.f(0.2f, 0.5f)
        drawLine(col, Offset(x, y), Offset(x + rnd.f(-len, len) * 0.4f, y + len * 0.5f), 2.2f)
        drawLine(Color.White.copy(alpha = 0.15f), Offset(x + 2f, y), Offset(x + len * 0.4f, y - len * 0.05f), 1.5f)
    }
}

private fun DrawScope.stoneBlocks(a: Offset, b: Offset, depth: Float, mortar: Color) {
    val rows = 3
    for (row in 0..rows) {
        val y = depth * row / rows
        drawLine(mortar, Offset(a.x, a.y + y), Offset(b.x, b.y + y), 2f)
    }
    val cols = 8
    for (row in 0 until rows) {
        for (c in 0..cols) {
            val t = (c + if (row % 2 == 0) 0f else 0.5f) / cols
            if (t > 1f) continue
            val x = a.x + (b.x - a.x) * t
            val y = a.y + (b.y - a.y) * t
            drawLine(mortar, Offset(x, y + depth * row / rows), Offset(x, y + depth * (row + 1) / rows), 2f)
        }
    }
}

private fun DrawScope.soilBits(a: Offset, b: Offset, depth: Float, rnd: Rand, col: Color) {
    repeat(14) {
        val t = rnd.f()
        val x = a.x + (b.x - a.x) * t
        val y = a.y + (b.y - a.y) * t + depth * rnd.f(0.35f, 0.9f)
        val s = depth * rnd.f(0.05f, 0.11f)
        drawOval(col.copy(alpha = 0.75f), Offset(x - s, y - s * 0.5f), Size(s * 2, s))
    }
}

private fun DrawScope.drawUnderside(iso: Iso, depth: Float, rnd: Rand) {
    val l = iso.left
    val f = iso.front
    val r = iso.right
    val tip = Offset(f.x - iso.bw * 0.04f, f.y + depth)
    val leftFace = Path().apply {
        moveTo(l.x, l.y)
        lineTo(f.x, f.y)
        lineTo(tip.x, tip.y)
        quadraticTo(l.x + iso.bw * 0.12f, l.y + depth * 0.55f, l.x, l.y)
        close()
    }
    val rightFace = Path().apply {
        moveTo(f.x, f.y)
        lineTo(r.x, r.y)
        quadraticTo(r.x - iso.bw * 0.1f, r.y + depth * 0.6f, tip.x, tip.y)
        close()
    }
    drawPath(leftFace, Brush.verticalGradient(listOf(hex(0xFF9C7550), hex(0xFF7A6250), hex(0xFF5B5550)), l.y, tip.y))
    drawPath(rightFace, Brush.verticalGradient(listOf(hex(0xFF77563A), hex(0xFF5B4A3E), hex(0xFF45403C)), r.y, tip.y))
    clipPath(leftFace) { rockCracks(l, f, depth * 0.7f, rnd, hex(0xFF4E4033)) }
    clipPath(rightFace) { rockCracks(f, r, depth * 0.7f, rnd, hex(0xFF3A3029)) }
    // hanging roots and vines
    repeat(16) {
        val t = rnd.f(0.05f, 0.95f)
        val onLeft = rnd.f() < 0.5f
        val a = if (onLeft) Offset(l.x + (f.x - l.x) * t, l.y + (f.y - l.y) * t) else Offset(f.x + (r.x - f.x) * t, f.y + (r.y - f.y) * t)
        val len = depth * rnd.f(0.25f, 0.8f)
        val path = Path().apply {
            moveTo(a.x, a.y + 2f)
            quadraticTo(a.x + rnd.f(-1f, 1f) * iso.tw * 0.3f, a.y + len * 0.5f, a.x + rnd.f(-1f, 1f) * iso.tw * 0.2f, a.y + len)
        }
        val vine = rnd.f() < 0.5f
        drawPath(path, if (vine) hex(0xFF4E8A3C) else hex(0xFF6B4A30), style = Stroke(if (vine) 2.2f else 3f, cap = StrokeCap.Round))
        if (vine) repeat(4) { j ->
            val yy = a.y + len * (0.25f + j * 0.2f)
            drawOval(hex(0xFF5FA24A), Offset(a.x - 3f + j % 2 * 4f, yy), Size(iso.tw * 0.09f, iso.tw * 0.05f))
        }
    }
}

// ------------------------------------------------------------------ top

private fun grassColors(theme: GardenTheme): List<Color> = when (theme) {
    GardenTheme.NIGHT -> listOf(hex(0xFF4C6E5E), hex(0xFF365647), hex(0xFF24403A))
    GardenTheme.GOLDEN -> listOf(hex(0xFFE2CF73), hex(0xFFB9B556), hex(0xFF8E9A44))
    GardenTheme.MOUNTAIN -> listOf(hex(0xFFA6CF86), hex(0xFF7FB063), hex(0xFF5C8F4C))
    GardenTheme.ZEN -> listOf(hex(0xFF9CC07A), hex(0xFF79A35C), hex(0xFF587F45))
    GardenTheme.SKY -> listOf(hex(0xFFB8E28A), hex(0xFF8DCB66), hex(0xFF67A84D))
    else -> listOf(hex(0xFFAED97F), hex(0xFF85BE5D), hex(0xFF5E9C45))
}

private fun DrawScope.drawTop(theme: GardenTheme, iso: Iso, rnd: Rand) {
    val g = grassColors(theme)
    val d = iso.diamond()
    drawPath(d, Brush.linearGradient(g, Offset(iso.left.x, iso.back.y), Offset(iso.right.x, iso.front.y)))
    clipPath(d) {
        // checker of squares, very light
        for (r in 0 until iso.k) for (c in 0 until iso.k) if ((r + c) % 2 == 0) {
            val a = iso.p(r.toFloat(), c.toFloat())
            val b = iso.p(r.toFloat(), c + 1f)
            val cc = iso.p(r + 1f, c + 1f)
            val dd = iso.p(r + 1f, c.toFloat())
            drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(cc.x, cc.y); lineTo(dd.x, dd.y); close() }, Color.White.copy(alpha = 0.05f))
        }
        // grass texture
        val n = (iso.k * iso.k * 14).coerceAtMost(2600)
        repeat(n) {
            val u = rnd.f() * iso.k
            val v = rnd.f() * iso.k
            val p = iso.p(u, v)
            val len = iso.tw * rnd.f(0.04f, 0.09f)
            val col = if (rnd.f() < 0.5f) g[0] else g[2]
            drawLine(col.copy(alpha = 0.55f), p, Offset(p.x + rnd.f(-0.3f, 0.3f) * len, p.y - len), 1.4f, StrokeCap.Round)
        }
    }
}

// ------------------------------------------------------------------ flat features

private fun DrawScope.drawFlatFeatures(theme: GardenTheme, iso: Iso, rnd: Rand) {
    val k = iso.k
    when (theme) {
        GardenTheme.FLOWERS -> for (r in 0 until k) pavers(iso, r, k / 2, rnd, hex(0xFFE3D8C3), hex(0xFFB9AC92))
        GardenTheme.NIGHT -> for (i in 0 until k) pavers(iso, i, i, rnd, hex(0xFF8E8C98), hex(0xFF5E5C68))
        GardenTheme.LAKE -> pond(iso, rnd)
        GardenTheme.MOUNTAIN -> {
            val rs = GardenLayout.streamRow(k)
            stream(iso, rs + 0.5f, rs.toFloat(), k.toFloat(), 0.32f, rnd)
        }
        GardenTheme.ZEN -> {
            zenSand(iso, rnd)
            val rs = (k * 0.62f).toInt()
            stream(iso, rs + 0.5f, 0f, GardenLayout.sandStart(k).toFloat(), 0.34f, rnd, pebbles = true)
        }
        GardenTheme.SKY -> {
            val c0 = k / 2
            val blob = organic(iso, Offset(k - 1f, c0.toFloat()), 0.95f, 0.85f, rnd)
            drawPath(blob, hex(0xFFE2D6AE))
            val water = organic(iso, Offset(k - 1f, c0.toFloat()), 0.78f, 0.7f, rnd)
            drawPath(water, Brush.linearGradient(listOf(hex(0xFF4FA3C7), hex(0xFF8FD3E6)), iso.p(k - 2f, c0 - 1f), iso.p(k.toFloat(), c0 + 1f)))
        }
        GardenTheme.GOLDEN -> {
            stream(iso, 0f, k / 2f + 0.5f, 0f, 0.48f, rnd, alongV = false, end = k.toFloat(), gold = true)
        }
        GardenTheme.MEADOW -> Unit
    }
}

private fun DrawScope.pavers(iso: Iso, r: Int, c: Int, rnd: Rand, light: Color, dark: Color) {
    for (i in 0..1) for (j in 0..1) {
        val u0 = r + 0.08f + i * 0.46f
        val v0 = c + 0.08f + j * 0.46f
        val s = 0.38f + rnd.f(-0.04f, 0.04f)
        val a = iso.p(u0, v0); val b = iso.p(u0, v0 + s); val cc = iso.p(u0 + s, v0 + s); val d = iso.p(u0 + s, v0)
        val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(cc.x, cc.y); lineTo(d.x, d.y); close() }
        drawPath(path, Brush.linearGradient(listOf(light, dark), a, cc))
        drawPath(path, dark.copy(alpha = 0.5f), style = Stroke(1.2f))
    }
}

/** A wobbly blob around a grid point, as an iso path. */
private fun organic(iso: Iso, c: Offset, ru: Float, rv: Float, rnd: Rand, wobble: Float = 0.1f): Path {
    val s1 = rnd.f(0f, 6f)
    val s2 = rnd.f(0f, 6f)
    return Path().apply {
        for (i in 0..48) {
            val th = i / 48f * 2 * PI.toFloat()
            val k = 1f + wobble * sin(3 * th + s1) + wobble * 0.6f * sin(5 * th + s2)
            val p = iso.p(c.x + cos(th) * ru * k, c.y + sin(th) * rv * k)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
}

private fun DrawScope.pond(iso: Iso, rnd: Rand) {
    val k = iso.k
    val c = GardenLayout.pondCenter(k)
    val ru = 0.27f * k
    val rv = 0.24f * k
    drawPath(organic(iso, c, ru * 1.14f, rv * 1.14f, Rand(7)), hex(0xFFE6D6A8))
    drawPath(organic(iso, c, ru * 1.06f, rv * 1.06f, Rand(7)), hex(0xFFB9A57A))
    val water = organic(iso, c, ru, rv, Rand(7))
    drawPath(water, Brush.linearGradient(listOf(hex(0xFF2F7FA3), hex(0xFF5FB3CE), hex(0xFF9AD8E6)), iso.p(c.x - ru, c.y - rv), iso.p(c.x + ru, c.y + rv)))
    clipPath(water) {
        // deeper middle
        drawPath(organic(iso, c, ru * 0.55f, rv * 0.55f, Rand(9)), hex(0xFF26698C).copy(alpha = 0.35f))
        // ripples
        repeat(5) {
            val p = iso.p(c.x + rnd.f(-0.6f, 0.6f) * ru, c.y + rnd.f(-0.6f, 0.6f) * rv)
            val w = iso.tw * rnd.f(0.3f, 0.6f)
            drawOval(Color.White.copy(alpha = 0.35f), Offset(p.x - w / 2, p.y - w / 4), Size(w, w / 2), style = Stroke(1.5f))
        }
        // sky reflection
        val hl = iso.p(c.x - ru * 0.35f, c.y + rv * 0.2f)
        drawLine(Color.White.copy(alpha = 0.45f), hl, Offset(hl.x + iso.tw * 0.7f, hl.y - iso.th * 0.2f), 2.5f, StrokeCap.Round)
    }
    // lily pads
    repeat((k * 0.9f).toInt().coerceAtLeast(3)) {
        val u = c.x + rnd.f(-0.65f, 0.65f) * ru
        val v = c.y + rnd.f(-0.65f, 0.65f) * rv
        if (!GardenLayout.inPond(u, v, k, 0.8f)) return@repeat
        val p = iso.p(u, v)
        val w = iso.tw * rnd.f(0.22f, 0.34f)
        withTransform({ scale(1f, 0.5f, p) }) {
            drawArc(hex(0xFF3F8A3C), rnd.f(0f, 360f), 320f, true, Offset(p.x - w / 2, p.y - w / 2), Size(w, w))
            drawArc(hex(0xFF5FAE4E), 0f, 360f, false, Offset(p.x - w * 0.35f, p.y - w * 0.35f), Size(w * 0.7f, w * 0.7f), style = Stroke(1f))
        }
        if (rnd.f() < 0.45f) {
            drawCircle(hex(0xFFF7B6CC), w * 0.13f, Offset(p.x, p.y - w * 0.08f))
            drawCircle(hex(0xFFFFE27A), w * 0.05f, Offset(p.x, p.y - w * 0.1f))
        }
    }
}

/**
 * A stream band. alongV = true: runs along columns at row [line] from [from] to [to] (v).
 * alongV = false: runs along rows at column [from] … here [line]=start row, [from]=column, [end]=end row.
 */
private fun DrawScope.stream(
    iso: Iso, line: Float, from: Float, to: Float, half: Float, rnd: Rand,
    pebbles: Boolean = false, alongV: Boolean = true, end: Float = 0f, gold: Boolean = false,
) {
    val pts = 30
    val left = ArrayList<Offset>()
    val right = ArrayList<Offset>()
    for (i in 0..pts) {
        val t = i / pts.toFloat()
        val wob = sin(t * 9f + line) * 0.06f
        if (alongV) {
            val v = from + (to - from) * t
            left += iso.p(line - half + wob, v)
            right += iso.p(line + half + wob, v)
        } else {
            val u = line + (end - line) * t
            left += iso.p(u, from - half + wob)
            right += iso.p(u, from + half + wob)
        }
    }
    fun band(extra: Float): Path = Path().apply {
        val l2 = left.mapIndexed { i, p -> Offset(p.x + (p.x - right[i].x) * extra, p.y + (p.y - right[i].y) * extra) }
        val r2 = right.mapIndexed { i, p -> Offset(p.x + (p.x - left[i].x) * extra, p.y + (p.y - left[i].y) * extra) }
        moveTo(l2[0].x, l2[0].y)
        l2.drop(1).forEach { lineTo(it.x, it.y) }
        r2.asReversed().forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(band(0.22f), if (gold) hex(0xFFD8C08A) else hex(0xFFCBBE98))
    val water = band(0f)
    drawPath(
        water,
        Brush.linearGradient(
            if (gold) listOf(hex(0xFF3C8FB0), hex(0xFF8FCFD8), hex(0xFFF3D9A0)) else listOf(hex(0xFF3B8DB0), hex(0xFF79C4DA)),
            left.first(), right.last(),
        ),
    )
    clipPath(water) {
        repeat(18) {
            val i = rnd.i(pts)
            val a = left[i]
            val b = right[i]
            val m = Offset(a.x + (b.x - a.x) * rnd.f(0.2f, 0.8f), a.y + (b.y - a.y) * rnd.f(0.2f, 0.8f))
            drawLine(Color.White.copy(alpha = 0.55f), m, Offset(m.x + iso.tw * 0.18f, m.y + iso.th * 0.18f), 1.6f, StrokeCap.Round)
        }
        if (pebbles) repeat(30) {
            val i = rnd.i(pts)
            val a = left[i]
            val b = right[i]
            val m = Offset(a.x + (b.x - a.x) * rnd.f(), a.y + (b.y - a.y) * rnd.f())
            drawOval(hex(0xFFD9D2C2).copy(alpha = 0.7f), Offset(m.x - 3f, m.y - 1.5f), Size(6f, 3f))
        }
    }
}

private fun DrawScope.zenSand(iso: Iso, rnd: Rand) {
    val k = iso.k
    val h = GardenLayout.sandStart(k).toFloat()
    val a = iso.p(0f, h); val b = iso.p(0f, k.toFloat()); val c = iso.p(k.toFloat(), k.toFloat()); val d = iso.p(k.toFloat(), h)
    val sand = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
    drawPath(sand, Brush.linearGradient(listOf(hex(0xFFF3EBDA), hex(0xFFE1D5BC)), a, c))
    // stone border
    drawLine(hex(0xFF8F8676), a, d, 3f)
    val stones = GardenLayout.zenStones(k)
    clipPath(sand) {
        // raked lines along the rows
        var v = h + 0.12f
        while (v < k) {
            drawLine(hex(0xFFCBBDA0), iso.p(0f, v), iso.p(k.toFloat(), v), 1.6f)
            drawLine(Color.White.copy(alpha = 0.7f), iso.p(0f, v + 0.05f), iso.p(k.toFloat(), v + 0.05f), 1.2f)
            v += 0.2f
        }
        // rings around the stones
        stones.forEach { s ->
            val ctr = iso.p(s.x, s.y)
            val maxR = iso.tw * 1.25f
            drawOval(hex(0xFFEFE6D2), Offset(ctr.x - maxR, ctr.y - maxR / 2), Size(maxR * 2, maxR))
            var rr = iso.tw * 0.45f
            while (rr <= maxR) {
                drawOval(hex(0xFFCBBDA0), Offset(ctr.x - rr, ctr.y - rr / 2), Size(rr * 2, rr), style = Stroke(1.6f))
                rr += iso.tw * 0.17f
            }
        }
    }
}

// ------------------------------------------------------------------ props (stand up, depth-sorted)

private fun DrawScope.rock(at: Offset, w: Float, rnd: Rand, moss: Boolean, base: Color = hex(0xFF8E9196)) {
    val h = w * rnd.f(0.55f, 0.85f)
    val path = Path().apply {
        moveTo(at.x - w / 2, at.y)
        lineTo(at.x - w * 0.42f, at.y - h * 0.6f)
        lineTo(at.x - w * 0.15f, at.y - h)
        lineTo(at.x + w * 0.22f, at.y - h * 0.9f)
        lineTo(at.x + w * 0.48f, at.y - h * 0.45f)
        lineTo(at.x + w / 2, at.y)
        close()
    }
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(at.x - w * 0.55f, at.y - w * 0.1f), Size(w * 1.1f, w * 0.25f))
    drawPath(path, Brush.linearGradient(listOf(lerp(base, Color.White, 0.35f), base, lerp(base, Color.Black, 0.35f)), Offset(at.x - w / 2, at.y - h), Offset(at.x + w / 2, at.y)))
    drawLine(lerp(base, Color.Black, 0.4f), Offset(at.x - w * 0.05f, at.y - h * 0.95f), Offset(at.x + w * 0.05f, at.y - h * 0.2f), 1.5f)
    if (moss) repeat(4) {
        val mx = at.x + rnd.f(-0.3f, 0.1f) * w
        val my = at.y - h * rnd.f(0.72f, 0.95f)
        val mw = w * rnd.f(0.1f, 0.2f)
        drawOval(hex(if (it % 2 == 0) 0xFF6E9E4C else 0xFF86B45C).copy(alpha = 0.9f), Offset(mx, my), Size(mw, mw * 0.55f))
    }
}

private fun DrawScope.reeds(at: Offset, s: Float, rnd: Rand) {
    repeat(6) {
        val dx = rnd.f(-0.5f, 0.5f) * s
        val h = s * rnd.f(1.2f, 2.2f)
        val tip = Offset(at.x + dx + rnd.f(-0.2f, 0.2f) * s, at.y - h)
        drawLine(hex(0xFF5E8F3E), Offset(at.x + dx, at.y), tip, 2f, StrokeCap.Round)
        if (rnd.f() < 0.5f) drawOval(hex(0xFF7A4E2C), Offset(tip.x - s * 0.06f, tip.y), Size(s * 0.12f, s * 0.35f))
    }
}

private fun DrawScope.fencePost(at: Offset, h: Float) {
    val w = h * 0.16f
    val path = Path().apply {
        moveTo(at.x - w / 2, at.y); lineTo(at.x - w / 2, at.y - h); lineTo(at.x, at.y - h - w * 0.8f)
        lineTo(at.x + w / 2, at.y - h); lineTo(at.x + w / 2, at.y); close()
    }
    drawPath(path, Brush.horizontalGradient(listOf(hex(0xFFFFFFFF), hex(0xFFD9D4CB)), at.x - w / 2, at.x + w / 2))
}

private fun DrawScope.stoneLantern(at: Offset, s: Float) {
    val stone = hex(0xFFB7B1A6)
    val dark = hex(0xFF8A8478)
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(at.x - s * 0.4f, at.y - s * 0.08f), Size(s * 0.8f, s * 0.2f))
    drawRect(Brush.horizontalGradient(listOf(stone, dark), at.x - s * 0.08f, at.x + s * 0.08f), Offset(at.x - s * 0.08f, at.y - s * 0.6f), Size(s * 0.16f, s * 0.6f))
    drawRect(stone, Offset(at.x - s * 0.22f, at.y - s * 0.66f), Size(s * 0.44f, s * 0.08f))
    drawRect(Brush.horizontalGradient(listOf(stone, dark), at.x - s * 0.17f, at.x + s * 0.17f), Offset(at.x - s * 0.17f, at.y - s * 0.92f), Size(s * 0.34f, s * 0.26f))
    drawRect(hex(0xFFFFD98A), Offset(at.x - s * 0.08f, at.y - s * 0.86f), Size(s * 0.16f, s * 0.13f))
    val roof = Path().apply {
        moveTo(at.x - s * 0.36f, at.y - s * 0.9f); lineTo(at.x, at.y - s * 1.12f); lineTo(at.x + s * 0.36f, at.y - s * 0.9f); close()
    }
    drawPath(roof, Brush.verticalGradient(listOf(stone, dark), at.y - s * 1.12f, at.y - s * 0.9f))
    drawCircle(dark, s * 0.05f, Offset(at.x, at.y - s * 1.14f))
}

private fun DrawScope.glowLantern(at: Offset, s: Float) {
    drawCircle(Brush.radialGradient(listOf(hex(0xFFFFD27A).copy(alpha = 0.45f), Color.Transparent), Offset(at.x, at.y), s * 1.3f), s * 1.3f, Offset(at.x, at.y))
    drawLine(hex(0xFF2B2B33), at, Offset(at.x, at.y - s * 0.9f), s * 0.07f)
    val lamp = Offset(at.x, at.y - s * 0.98f)
    drawCircle(Brush.radialGradient(listOf(hex(0xFFFFE9A8), hex(0xFFFFB84A).copy(alpha = 0.6f), Color.Transparent), lamp, s * 0.5f), s * 0.5f, lamp)
    drawRect(hex(0xFFFFE7A0), Offset(lamp.x - s * 0.08f, lamp.y - s * 0.1f), Size(s * 0.16f, s * 0.2f))
    drawRect(hex(0xFF2B2B33), Offset(lamp.x - s * 0.12f, lamp.y - s * 0.16f), Size(s * 0.24f, s * 0.06f))
}

private fun DrawScope.bridge(iso: Iso, rowFrom: Float, rowTo: Float, v: Float, half: Float, red: Boolean, acrossV: Boolean = false) {
    val n = 16
    val l = ArrayList<Offset>()
    val r = ArrayList<Offset>()
    val lift = iso.tw * 0.35f
    for (i in 0..n) {
        val t = i / n.toFloat()
        val u = rowFrom + (rowTo - rowFrom) * t
        val up = sin(t * PI.toFloat()) * lift
        val a = if (acrossV) iso.p(v - half, u) else iso.p(u, v - half)
        val b = if (acrossV) iso.p(v + half, u) else iso.p(u, v + half)
        l += Offset(a.x, a.y - up)
        r += Offset(b.x, b.y - up)
    }
    val deck = Path().apply {
        moveTo(l[0].x, l[0].y)
        l.drop(1).forEach { lineTo(it.x, it.y) }
        r.asReversed().forEach { lineTo(it.x, it.y) }
        close()
    }
    val wood = if (red) hex(0xFFB8432F) else hex(0xFF9A6B44)
    drawPath(deck, Brush.linearGradient(listOf(lerp(wood, Color.White, 0.2f), wood), l.first(), r.last()))
    for (i in 1 until n) drawLine(lerp(wood, Color.Black, 0.3f), l[i], r[i], 1.2f)
    // rails
    listOf(l, r).forEach { side ->
        val railH = iso.tw * 0.28f
        val rail = Path().apply {
            moveTo(side[0].x, side[0].y - railH)
            side.drop(1).forEach { lineTo(it.x, it.y - railH) }
        }
        drawPath(rail, if (red) hex(0xFFD2492F) else hex(0xFF7A5232), style = Stroke(iso.tw * 0.05f, cap = StrokeCap.Round))
        for (i in 0..n step 4) {
            drawLine(if (red) hex(0xFFA83A26) else hex(0xFF6A4528), side[i], Offset(side[i].x, side[i].y - railH), iso.tw * 0.04f)
        }
    }
}

private fun DrawScope.flowers(at: Offset, s: Float, rnd: Rand, palette: List<Long>) {
    repeat(5) {
        val p = Offset(at.x + rnd.f(-0.5f, 0.5f) * s, at.y + rnd.f(-0.25f, 0.25f) * s)
        drawLine(hex(0xFF4E8A3C), p, Offset(p.x, p.y - s * 0.25f), 1.4f)
        drawCircle(hex(palette[rnd.i(palette.size)]), s * 0.09f, Offset(p.x, p.y - s * 0.28f))
        drawCircle(hex(0xFFFFE27A), s * 0.03f, Offset(p.x, p.y - s * 0.28f))
    }
}

private fun addProps(
    theme: GardenTheme, iso: Iso, props: MutableList<Pair<Float, DrawScope.() -> Unit>>, rnd: Rand, used: Set<Int>,
) {
    val k = iso.k
    val blocked = GardenLayout.blocked(theme, k)
    val free = (0 until k * k).filter { it !in used && it !in blocked }
    fun freeDecor(chance: Float, draw: DrawScope.(Offset, Rand) -> Unit) {
        free.forEach { t ->
            if (rnd.f() > chance) return@forEach
            val r = t / k
            val c = t % k
            val p = iso.p(r + rnd.f(0.25f, 0.75f), c + rnd.f(0.25f, 0.75f))
            val rr = Rand(t.toLong() * 13 + theme.ordinal)
            props += (r + c + 0.5f) to { draw(p, rr) }
        }
    }
    when (theme) {
        GardenTheme.MEADOW -> freeDecor(0.35f) { p, rr ->
            if (rr.f() < 0.6f) flowers(p, iso.tw * 0.5f, rr, listOf(0xFFFFFFFF, 0xFFF7D35A, 0xFFE88BB0))
            else rock(p, iso.tw * 0.18f, rr, false, hex(0xFFB9B3A7))
        }
        GardenTheme.FLOWERS -> {
            // white picket fence on the two back edges
            val n = k * 2
            for (i in 0..n) {
                val u = i * k / n.toFloat()
                props += (u * 0.999f) to { fencePost(iso.p(u, 0.05f), iso.tw * 0.42f) }
                props += (u * 0.999f) to { fencePost(iso.p(0.05f, u), iso.tw * 0.42f) }
            }
            props += (-0.5f) to {
                listOf(0.18f, 0.32f).forEach { hh ->
                    drawLine(hex(0xFFEDE8DF), Offset(iso.p(0f, 0.05f).x, iso.p(0f, 0.05f).y - iso.tw * hh), Offset(iso.p(k.toFloat(), 0.05f).x, iso.p(k.toFloat(), 0.05f).y - iso.tw * hh), iso.tw * 0.05f)
                    drawLine(hex(0xFFEDE8DF), Offset(iso.p(0.05f, 0f).x, iso.p(0.05f, 0f).y - iso.tw * hh), Offset(iso.p(0.05f, k.toFloat()).x, iso.p(0.05f, k.toFloat()).y - iso.tw * hh), iso.tw * 0.05f)
                }
            }
            freeDecor(0.7f) { p, rr -> flowers(p, iso.tw * 0.6f, rr, listOf(0xFFE8456B, 0xFFF59E3B, 0xFFB46BD6, 0xFFFFFFFF, 0xFFF7D35A)) }
        }
        GardenTheme.LAKE -> {
            val c = GardenLayout.pondCenter(k)
            repeat(10) { i ->
                val th = i / 10f * 2 * PI.toFloat() + 0.3f
                val u = c.x + cos(th) * 0.27f * k * 1.08f
                val v = c.y + sin(th) * 0.24f * k * 1.08f
                val p = iso.p(u, v)
                val rr = Rand(i.toLong() + 40)
                props += (u + v) to { if (i % 3 == 0) rock(p, iso.tw * 0.3f, rr, true) else reeds(p, iso.tw * 0.35f, rr) }
            }
            freeDecor(0.2f) { p, rr -> flowers(p, iso.tw * 0.45f, rr, listOf(0xFFFFFFFF, 0xFFF7D35A)) }
        }
        GardenTheme.MOUNTAIN -> {
            val lim = maxOf(2, (k * 0.32f).roundToInt())
            for (r in 0 until k) for (c in 0 until k) if (r + c < lim) {
                val rr = Rand((r * k + c).toLong() + 77)
                val p = iso.p(r + 0.5f, c + 0.5f)
                props += (r + c + 0.6f) to { rock(p, iso.tw * rr.f(0.7f, 1.1f), rr, true) }
            }
            freeDecor(0.15f) { p, rr -> rock(p, iso.tw * 0.2f, rr, rr.f() < 0.5f) }
        }
        GardenTheme.ZEN -> {
            val stones = GardenLayout.zenStones(k)
            stones.forEachIndexed { i, s ->
                props += (s.x + s.y) to { rock(iso.p(s.x, s.y), iso.tw * (0.55f - i * 0.1f), Rand(i + 5L), true, hex(0xFF7D7A74)) }
            }
            val s0 = GardenLayout.sandStart(k)
            val lt = iso.center(k / 4, s0 - 1)
            props += (k / 4 + s0 - 0.5f) to { stoneLantern(lt, iso.tw * 1.1f) }
            val rs = (k * 0.62f).toInt()
            props += (rs + k / 4f + 1f) to { bridge(iso, rs - 0.15f, rs + 1.15f, k / 4f + 0.5f, 0.42f, red = true) }
            freeDecor(0.15f) { p, rr -> rock(p, iso.tw * 0.16f, rr, true, hex(0xFF8A877F)) }
        }
        GardenTheme.NIGHT -> {
            GardenLayout.lanternTiles(k).forEach { (r, c) ->
                val p = iso.center(r, c)
                props += (r + c + 1f) to { glowLantern(p, iso.tw * 0.9f) }
            }
            freeDecor(0.25f) { p, rr -> flowers(p, iso.tw * 0.45f, rr, listOf(0xFFBFD3FF, 0xFFFFFFFF)) }
        }
        GardenTheme.SKY -> {
            freeDecor(0.3f) { p, rr ->
                if (rr.f() < 0.6f) flowers(p, iso.tw * 0.5f, rr, listOf(0xFFFFFFFF, 0xFFF7D35A, 0xFF8EC9F0))
                else rock(p, iso.tw * 0.2f, rr, true)
            }
        }
        GardenTheme.GOLDEN -> {
            val br = k / 2
            props += (br + k / 2f + 1f) to { bridge(iso, k / 2f - 0.2f, k / 2f + 1.2f, br + 0.5f, 0.42f, red = false, acrossV = true) }
            freeDecor(0.3f) { p, rr -> flowers(p, iso.tw * 0.45f, rr, listOf(0xFFFFE08A, 0xFFF59E3B, 0xFFFFFFFF)) }
        }
    }
}

// ------------------------------------------------------------------ effects

private fun DrawScope.waterfall(top: Offset, width: Float, height: Float, rnd: Rand) {
    val body = Path().apply {
        moveTo(top.x - width / 2, top.y)
        quadraticTo(top.x, top.y - width * 0.18f, top.x + width / 2, top.y)
        lineTo(top.x + width * 0.56f, top.y + height)
        lineTo(top.x - width * 0.56f, top.y + height)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(hex(0xFF6FBFDA), hex(0xFFAEDDEB), hex(0xFFE4F5FA).copy(alpha = 0.85f)), top.y, top.y + height))
    repeat(10) {
        val x = top.x - width / 2 + rnd.f() * width
        drawLine(Color.White.copy(alpha = 0.7f), Offset(x, top.y + rnd.f() * height * 0.3f), Offset(x, top.y + height * rnd.f(0.5f, 1f)), 1.4f)
    }
    repeat(8) {
        drawCircle(Color.White.copy(alpha = 0.75f), width * rnd.f(0.15f, 0.3f), Offset(top.x + rnd.f(-0.6f, 0.6f) * width, top.y + height))
    }
}

private fun DrawScope.drawEffects(theme: GardenTheme, iso: Iso, rnd: Rand, depth: Float) {
    val k = iso.k
    when (theme) {
        GardenTheme.MOUNTAIN -> {
            val rs = GardenLayout.streamRow(k)
            val a = iso.p(rs + 0.5f, k.toFloat())
            waterfall(a, iso.tw * 0.55f, depth * 1.05f, rnd)
        }
        GardenTheme.SKY -> {
            val c0 = k / 2
            val a = iso.p(k.toFloat(), c0.toFloat())
            waterfall(a, iso.tw * 0.5f, depth * 1.4f, rnd)
            val f = iso.front
            cloud(Offset(f.x - iso.bw * 0.25f, f.y + depth * 0.95f), iso.bw * 0.45f, 0.95f)
            cloud(Offset(f.x + iso.bw * 0.22f, f.y + depth * 1.05f), iso.bw * 0.4f, 0.9f)
        }
        GardenTheme.GOLDEN -> {
            val a = iso.p(k.toFloat(), k / 2f + 0.5f)
            waterfall(a, iso.tw * 0.8f, depth * 0.9f, rnd)
            // floating dust
            repeat(40) {
                val p = Offset(rnd.f() * size.width, rnd.f() * size.height * 0.8f)
                drawCircle(hex(0xFFFFE9A8).copy(alpha = rnd.f(0.3f, 0.8f)), rnd.f(1f, 2.4f), p)
            }
        }
        GardenTheme.NIGHT -> repeat(45) {
            val p = iso.p(rnd.f() * k, rnd.f() * k)
            val q = Offset(p.x + rnd.f(-0.5f, 0.5f) * iso.tw, p.y - rnd.f(0.2f, 1.6f) * iso.tw)
            val r = iso.tw * rnd.f(0.12f, 0.22f)
            drawCircle(Brush.radialGradient(listOf(hex(0xFFE9FF9A).copy(alpha = 0.9f), Color.Transparent), q, r), r, q)
            drawCircle(hex(0xFFF6FFC8), r * 0.18f, q)
        }
        GardenTheme.FLOWERS -> repeat(14) {
            val p = Offset(rnd.f() * size.width, rnd.f() * size.height * 0.75f)
            withTransform({ rotate(rnd.f(0f, 180f), p) }) {
                drawOval(hex(0xFFF7B6CC).copy(alpha = 0.8f), p, Size(size.width * 0.012f, size.width * 0.007f))
            }
        }
        else -> Unit
    }
}

/** Tiles a garden can use for plants, shuffled the same way every time for this seed. */
fun gardenTiles(theme: GardenTheme, k: Int, seed: Int): List<Int> {
    val blocked = GardenLayout.blocked(theme, k)
    return (0 until k * k).filter { it !in blocked }.shuffled(java.util.Random(seed.toLong()))
}
