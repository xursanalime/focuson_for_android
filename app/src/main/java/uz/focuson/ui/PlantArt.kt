package uz.focuson.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Hand-made plant art, drawn in code. Each species has a fixed random seed,
 * so a plant always looks the same. Light comes from the upper left.
 * Coordinates: u = size, x to the right from the centre, y UP from the ground line.
 */

private class Art(val scope: DrawScope, val withered: Boolean, seed: Int) {
    val u = scope.size.minDimension
    val cx = scope.size.width / 2
    val gy = scope.size.height * 0.84f
    val rnd = java.util.Random(seed.toLong())
    private val dead = Color(0xFF8A6A48)
    private val deadDark = Color(0xFF5E4630)

    fun x(v: Float) = cx + v * u
    fun y(v: Float) = gy - v * u
    fun p(vx: Float, vy: Float) = Offset(x(vx), y(vy))
    fun c(hex: Long): Color {
        val col = Color(hex)
        if (!withered) return col
        val lum = 0.3f * col.red + 0.59f * col.green + 0.11f * col.blue
        return lerp(col, lerp(deadDark, dead, lum), 0.75f)
    }
    fun r() = rnd.nextFloat()
    fun r(a: Float, b: Float) = a + (b - a) * rnd.nextFloat()
    fun gauss() = rnd.nextGaussian().toFloat()
}

fun DrawScope.drawPlant(speciesId: String, growth: Float, withered: Boolean, showGround: Boolean = true) {
    val a = Art(this, withered, speciesId.hashCode())
    if (showGround) drawGround(a)
    val g = growth.coerceIn(0f, 1f)
    if (g < 0.1f) {
        val s = 0.55f + 0.45f * (g / 0.1f)
        scale(s, s, Offset(a.cx, a.gy)) { drawSeedling(Art(this, withered, 99)) }
        return
    }
    val t = (g - 0.1f) / 0.9f
    val s = 0.34f + 0.66f * t
    val density = 0.4f + 0.6f * t
    val lean = if (withered) -7f else 0f
    withTransform({
        scale(s, s, Offset(a.cx, a.gy))
        rotate(lean, Offset(a.cx, a.gy))
    }) {
        val b = Art(this, withered, speciesId.hashCode())
        val dens = if (withered) density * 0.45f else density
        when (speciesId) {
            "archa" -> drawSpruce(b)
            "kaktus" -> drawCactus(b)
            "lola" -> drawTulips(b, t)
            "kungaboqar" -> drawSunflower(b)
            "paxta" -> drawCotton(b, dens)
            "palma" -> drawPalm(b)
            else -> drawTree(b, treeSpec(speciesId), dens)
        }
    }
}

// ------------------------------------------------------------------ ground

private fun DrawScope.drawGround(a: Art) {
    // soft shadow
    withTransform({ scale(1f, 0.22f, Offset(a.cx, a.gy + a.u * 0.02f)) }) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(a.cx, a.gy + a.u * 0.02f), radius = a.u * 0.36f,
            ),
            radius = a.u * 0.36f, center = Offset(a.cx, a.gy + a.u * 0.02f),
        )
    }
    val w = a.u * 0.5f
    val h = a.u * 0.11f
    // soil side
    drawOval(
        Brush.verticalGradient(listOf(a.c(0xFF7A5534), a.c(0xFF4A321E)), a.gy - h * 0.2f, a.gy + h * 0.75f),
        Offset(a.cx - w / 2, a.gy - h / 2 + a.u * 0.028f), Size(w, h),
    )
    // grass top
    drawOval(
        Brush.radialGradient(
            listOf(a.c(0xFFB3DB86), a.c(0xFF86BE5D), a.c(0xFF5E9A43)),
            center = Offset(a.cx - w * 0.18f, a.gy - h * 0.35f), radius = w * 0.6f,
        ),
        Offset(a.cx - w / 2, a.gy - h / 2), Size(w, h),
    )
    // grass blades around the rim
    val blade = listOf(0xFF5E9A43, 0xFF79B055, 0xFF94C76A, 0xFF4E8838)
    repeat(46) {
        val th = a.r(0f, (2 * PI).toFloat())
        val rf = a.r(0.72f, 1f)
        val bx = a.cx + cos(th) * w / 2 * rf
        val by = a.gy + sin(th) * h / 2 * rf
        val len = a.u * a.r(0.014f, 0.036f)
        val bend = a.u * a.r(-0.012f, 0.012f)
        val path = Path().apply {
            moveTo(bx - a.u * 0.003f, by)
            quadraticTo(bx + bend * 0.4f, by - len * 0.6f, bx + bend, by - len)
            quadraticTo(bx + bend * 0.4f + a.u * 0.002f, by - len * 0.5f, bx + a.u * 0.003f, by)
            close()
        }
        drawPath(path, a.c(blade[it % blade.size]))
    }
    // pebbles
    repeat(2) {
        val px = a.cx + a.r(-0.17f, 0.17f) * a.u
        val py = a.gy + a.r(0.005f, 0.03f) * a.u
        val pw = a.u * a.r(0.018f, 0.03f)
        drawOval(
            Brush.radialGradient(listOf(Color(0xFFE2DED6), Color(0xFF9C978E)), Offset(px - pw * 0.2f, py - pw * 0.2f), pw),
            Offset(px - pw / 2, py - pw * 0.3f), Size(pw, pw * 0.6f),
        )
    }
}

private fun DrawScope.drawSeedling(a: Art) {
    drawOval(
        Brush.verticalGradient(listOf(a.c(0xFF8B6440), a.c(0xFF5A3E26))),
        Offset(a.x(-0.06f), a.y(0.025f)), Size(a.u * 0.12f, a.u * 0.045f),
    )
    val stem = Path().apply {
        moveTo(a.x(-0.004f), a.y(0.01f))
        quadraticTo(a.x(0.01f), a.y(0.08f), a.x(0f), a.y(0.14f))
        lineTo(a.x(0.006f), a.y(0.14f))
        quadraticTo(a.x(0.018f), a.y(0.08f), a.x(0.006f), a.y(0.01f))
        close()
    }
    drawPath(stem, a.c(0xFF6FA84E))
    leaf(a, a.p(0f, 0.135f), 0.09f, 0.045f, -150f, 0xFF5E9E44, 0xFF9BD26E)
    leaf(a, a.p(0.004f, 0.14f), 0.08f, 0.04f, -25f, 0xFF6BAE4C, 0xFFB2DE83)
}

/** A pointed leaf from [base], [len] long, rotated [deg] (0 = pointing right). */
private fun DrawScope.leaf(a: Art, base: Offset, len: Float, wid: Float, deg: Float, dark: Long, light: Long) {
    val l = len * a.u
    val w = wid * a.u
    rotate(deg, base) {
        val path = Path().apply {
            moveTo(base.x, base.y)
            cubicTo(base.x + l * 0.3f, base.y - w * 0.9f, base.x + l * 0.75f, base.y - w * 0.7f, base.x + l, base.y)
            cubicTo(base.x + l * 0.75f, base.y + w * 0.7f, base.x + l * 0.3f, base.y + w * 0.9f, base.x, base.y)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(a.c(light), a.c(dark)), Offset(base.x, base.y - w), Offset(base.x + l, base.y + w)))
        drawLine(a.c(dark).copy(alpha = 0.5f), base, Offset(base.x + l * 0.92f, base.y), a.u * 0.003f)
    }
}

// ------------------------------------------------------------------ broadleaf trees

private enum class Crown { ROUND, TALL, WIDE, WEEPING, PADS, BUSH }

private class TreeSpec(
    val trunkH: Float,
    val trunkW: Float,
    val bark: List<Long>,
    val crownY: Float,
    val crownRx: Float,
    val crownRy: Float,
    val leaves: List<Long>,
    val blob: Float,
    val blobs: Int,
    val crown: Crown = Crown.ROUND,
    val fruit: List<Long>? = null,
    val fruits: Int = 0,
    val fruitR: Float = 0.018f,
    val flowers: Long? = null,
    val fallen: List<Long>? = null,
    val birch: Boolean = false,
    val plane: Boolean = false,
    val stems: Int = 1,
    val showBranches: Boolean = false,
)

private fun treeSpec(id: String): TreeSpec = when (id) {
    "olcha" -> TreeSpec(
        0.30f, 0.05f, listOf(0xFF4E3328, 0xFF7A5446), 0.47f, 0.27f, 0.2f,
        listOf(0xFFC85E86, 0xFFE07FA3, 0xFFEE9DB9, 0xFFF6BCD0, 0xFFFCDCE7), 0.03f, 230,
        fallen = listOf(0xFFF4A7C0, 0xFFF9CADA), showBranches = true,
    )
    "orik" -> TreeSpec(
        0.29f, 0.05f, listOf(0xFF3E2C22, 0xFF6A4A3A), 0.46f, 0.27f, 0.19f,
        listOf(0xFFD9B8BE, 0xFFE9CDD0, 0xFFF4E2E2, 0xFFFFF4F2, 0xFFFFFFFF), 0.026f, 240,
        flowers = 0xFFE58FA6, fallen = listOf(0xFFFFFFFF, 0xFFF3D6DA), showBranches = true,
    )
    "qayin" -> TreeSpec(
        0.42f, 0.04f, listOf(0xFFBFB9AE, 0xFFF4F1EA), 0.55f, 0.17f, 0.24f,
        listOf(0xFF4D7A2E, 0xFF6E9B3B, 0xFF93BA4E, 0xFFB7D16A, 0xFFD6E58E), 0.024f, 230,
        crown = Crown.TALL, birch = true,
    )
    "zarang" -> TreeSpec(
        0.30f, 0.055f, listOf(0xFF3E2A1E, 0xFF6E4A33), 0.48f, 0.28f, 0.21f,
        listOf(0xFF7E2016, 0xFFA83A1C, 0xFFCF5A22, 0xFFE58A2E, 0xFFF5BC4A), 0.03f, 230,
        fallen = listOf(0xFFCF5A22, 0xFFE58A2E, 0xFFA83A1C),
    )
    "olma" -> TreeSpec(
        0.27f, 0.055f, listOf(0xFF4A3426, 0xFF7A5A43), 0.45f, 0.28f, 0.2f,
        listOf(0xFF2F6B2A, 0xFF3F8534, 0xFF56A043, 0xFF79B95A, 0xFFA2D27D), 0.03f, 220,
        fruit = listOf(0xFF8E1B16, 0xFFD3362A, 0xFFF27C5E), fruits = 16,
    )
    "anor" -> TreeSpec(
        0.22f, 0.03f, listOf(0xFF4C3A2A, 0xFF7C6448), 0.40f, 0.27f, 0.2f,
        listOf(0xFF24502A, 0xFF326A36, 0xFF468545, 0xFF65A05A, 0xFF8CBC73), 0.024f, 240,
        crown = Crown.BUSH, fruit = listOf(0xFF6E0F14, 0xFFB3202A, 0xFFE0545A), fruits = 11, fruitR = 0.022f,
        flowers = 0xFFF0592E, stems = 3,
    )
    "majnuntol" -> TreeSpec(
        0.33f, 0.06f, listOf(0xFF3B2E22, 0xFF6B5843), 0.55f, 0.27f, 0.15f,
        listOf(0xFF3E6B2C, 0xFF56853A, 0xFF73A04B, 0xFF94BA62, 0xFFB9D484), 0.024f, 150,
        crown = Crown.WEEPING,
    )
    "qaragay" -> TreeSpec(
        0.5f, 0.045f, listOf(0xFF6E3A22, 0xFFB86B3E), 0.6f, 0.26f, 0.17f,
        listOf(0xFF173A2C, 0xFF21503A, 0xFF2E6748, 0xFF447F57, 0xFF62996A), 0.024f, 230,
        crown = Crown.PADS, showBranches = true,
    )
    "chinor" -> TreeSpec(
        0.32f, 0.085f, listOf(0xFF8C8471, 0xFFC9C2AC), 0.52f, 0.36f, 0.22f,
        listOf(0xFF2E5F25, 0xFF3F7A2F, 0xFF56963C, 0xFF77B04F, 0xFF9DC86B), 0.032f, 300,
        crown = Crown.WIDE, plane = true,
    )
    else -> TreeSpec( // eman
        0.28f, 0.065f, listOf(0xFF43301F, 0xFF7A5A3C), 0.47f, 0.3f, 0.21f,
        listOf(0xFF1F4D24, 0xFF2D6A30, 0xFF428A3E, 0xFF62A651, 0xFF8EC56C), 0.032f, 250,
    )
}

private class Blob(val x: Float, val y: Float, val r: Float, val shade: Float, val order: Float)

private fun DrawScope.drawTree(a: Art, s: TreeSpec, density: Float) {
    val crownC = Offset(0f, s.crownY)

    // cluster centres inside the crown
    val clusters = ArrayList<Offset>()
    val nClusters = when (s.crown) { Crown.WIDE -> 13; Crown.PADS -> 6; Crown.BUSH -> 10; else -> 10 }
    repeat(nClusters) { i ->
        when (s.crown) {
            Crown.PADS -> {
                val level = i.toFloat() / (nClusters - 1)
                val side = if (i % 2 == 0) -1f else 1f
                clusters += Offset(
                    side * a.r(0.04f, s.crownRx * (1.05f - level * 0.6f)),
                    s.crownY - s.crownRy + level * s.crownRy * 2f,
                )
            }
            else -> {
                val ang = a.r(0f, (2 * PI).toFloat())
                val rad = sqrt(a.r()) * 0.6f
                clusters += Offset(cos(ang) * s.crownRx * rad, s.crownY + sin(ang) * s.crownRy * rad)
            }
        }
    }

    // trunk(s) and branches
    val barkDark = a.c(s.bark[0])
    val barkLight = a.c(s.bark[1])
    repeat(s.stems) { k ->
        val off = if (s.stems == 1) 0f else (k - (s.stems - 1) / 2f) * 0.035f
        val tilt = off * 1.6f
        drawTrunk(a, off, s.trunkW / s.stems.coerceAtMost(2), s.trunkH + (if (k == 1) 0.04f else 0f), tilt, barkDark, barkLight, s)
    }
    val topY = s.trunkH
    val branchW = s.trunkW * 0.32f
    clusters.forEachIndexed { i, cl ->
        if (s.crown == Crown.PADS || s.showBranches || i % 2 == 0) {
            val start = Offset(0f, topY * a.r(0.65f, 1f))
            val mid = Offset(cl.x * 0.5f + a.r(-0.02f, 0.02f), (start.y + cl.y) / 2 + 0.02f)
            val path = Path().apply {
                moveTo(a.x(start.x - branchW / 2), a.y(start.y))
                quadraticTo(a.x(mid.x), a.y(mid.y), a.x(cl.x), a.y(cl.y))
                quadraticTo(a.x(mid.x + branchW * 0.3f), a.y(mid.y - branchW * 0.4f), a.x(start.x + branchW / 2), a.y(start.y))
                close()
            }
            drawPath(path, barkDark)
        }
    }

    // foliage blobs (kept inside the crown, so nothing floats in the air)
    val blobs = ArrayList<Blob>()
    val n = (s.blobs * 1.6f).toInt()
    val spread = if (s.crown == Crown.PADS) Offset(0.075f, 0.018f) else Offset(0.07f, 0.055f)
    var tries = 0
    while (blobs.size < n && tries < n * 6) {
        tries++
        val cl = clusters[blobs.size % clusters.size]
        val bx = cl.x + a.gauss() * spread.x
        val by = cl.y + a.gauss() * spread.y
        if (s.crown != Crown.PADS) {
            val nx = (bx - crownC.x) / s.crownRx
            val ny = (by - crownC.y) / s.crownRy
            if (nx * nx + ny * ny > 0.92f) continue
        } else if (kotlin.math.abs(by - cl.y) > 0.045f || kotlin.math.abs(bx - cl.x) > 0.15f) continue
        val lx = (bx - crownC.x) / s.crownRx
        val ly = (by - crownC.y) / s.crownRy
        val shade = (0.5f - 0.32f * lx + 0.45f * ly + a.gauss() * 0.12f).coerceIn(0f, 0.999f)
        blobs += Blob(bx, by, s.blob * a.r(0.55f, 1.05f), shade, a.r())
    }
    val shown = blobs.filter { it.order < density }.sortedBy { it.shade }

    // dark mass behind the leaves, so the crown has no holes
    if (density > 0.55f && s.crown != Crown.PADS) {
        clusters.forEach { cl ->
            val nx = (cl.x - crownC.x) / s.crownRx
            val ny = (cl.y - crownC.y) / s.crownRy
            if (nx * nx + ny * ny < 0.3f) drawCircle(a.c(s.leaves[0]), a.u * s.blob * 1.7f, a.p(cl.x, cl.y))
        }
    }
    val pal = s.leaves
    shown.forEach { b ->
        val idx = (b.shade * pal.size).toInt().coerceIn(0, pal.size - 1)
        val center = a.p(b.x, b.y)
        val rad = a.u * b.r
        drawCircle(a.c(pal[idx]), rad, center)
        if (idx >= pal.size - 2) {
            drawCircle(a.c(pal[pal.size - 1]).copy(alpha = 0.55f), rad * 0.45f, Offset(center.x - rad * 0.3f, center.y - rad * 0.3f))
        }
    }
    // leaves on the edge, pointing outward, break up the round outline
    if (s.crown != Crown.WEEPING) {
        shown.forEach { b ->
            val nx = (b.x - crownC.x) / s.crownRx
            val ny = (b.y - crownC.y) / s.crownRy
            val d2 = nx * nx + ny * ny
            if (d2 < 0.45f && s.crown != Crown.PADS) return@forEach
            if (b.order > density * 0.7f) return@forEach
            val idx = (b.shade * pal.size).toInt().coerceIn(0, pal.size - 1)
            val out = Math.toDegrees(kotlin.math.atan2(-(b.y - crownC.y).toDouble(), (b.x - crownC.x).toDouble())).toFloat()
            leaf(a, a.p(b.x, b.y), s.blob * 1.5f, s.blob * 0.55f, out + a.r(-40f, 40f), pal[maxOf(0, idx - 1)], pal[idx])
        }
    }

    if (s.crown == Crown.WEEPING) drawWeeping(a, s, density)
    s.flowers?.let { fl ->
        shown.filter { it.order < density * 0.18f }.forEach { b ->
            drawCircle(a.c(fl), a.u * 0.007f, a.p(b.x + 0.01f, b.y + 0.01f))
        }
    }
    s.fruit?.let { fr ->
        val count = (s.fruits * density).toInt()
        shown.filter { it.shade > 0.25f }.take(count * 3).filterIndexed { i, _ -> i % 3 == 0 }.forEach { b ->
            drawFruit(a, a.p(b.x, b.y - 0.01f), s.fruitR, fr, s.crown == Crown.BUSH)
        }
    }
    s.fallen?.let { cols ->
        if (!a.withered) repeat((10 * density).toInt()) { i ->
            val px = a.cx + a.r(-0.2f, 0.2f) * a.u
            val py = a.gy + a.r(-0.03f, 0.03f) * a.u
            rotate(a.r(0f, 180f), Offset(px, py)) {
                drawOval(Color(cols[i % cols.size]), Offset(px, py), Size(a.u * 0.018f, a.u * 0.009f))
            }
        }
    }
}

private fun DrawScope.drawTrunk(a: Art, off: Float, w: Float, h: Float, tilt: Float, dark: Color, light: Color, s: TreeSpec) {
    val path = Path().apply {
        moveTo(a.x(off - w * 0.95f), a.y(0f))
        cubicTo(a.x(off - w * 0.45f), a.y(0.02f), a.x(off - w * 0.42f), a.y(h * 0.25f), a.x(off + tilt - w * 0.3f), a.y(h))
        lineTo(a.x(off + tilt + w * 0.3f), a.y(h))
        cubicTo(a.x(off + w * 0.42f), a.y(h * 0.25f), a.x(off + w * 0.45f), a.y(0.02f), a.x(off + w * 0.95f), a.y(0f))
        close()
    }
    val mid = lerp(dark, light, 0.55f)
    drawPath(
        path,
        Brush.horizontalGradient(listOf(dark, light, mid, dark), a.x(off - w * 0.6f), a.x(off + w * 0.6f)),
    )
    when {
        s.birch -> repeat(9) {
            val yy = a.r(0.03f, h * 0.95f)
            val ww = a.r(0.012f, 0.03f)
            val xx = off + tilt * (yy / h) + a.r(-w * 0.3f, w * 0.1f)
            drawLine(Color(0xFF2A2622), a.p(xx, yy), a.p(xx + ww, yy + a.r(-0.004f, 0.004f)), a.u * a.r(0.004f, 0.008f), StrokeCap.Round)
        }
        s.plane -> repeat(10) {
            val yy = a.r(0.03f, h * 0.9f)
            val xx = off + tilt * (yy / h) + a.r(-w * 0.35f, w * 0.25f)
            val col = listOf(0xFFE4DCC2, 0xFFA2A07A, 0xFF777A5C)[it % 3]
            drawOval(a.c(col).copy(alpha = 0.85f), a.p(xx, yy), Size(a.u * a.r(0.015f, 0.03f), a.u * a.r(0.01f, 0.02f)))
        }
        else -> repeat(5) {
            val xx = off + a.r(-w * 0.35f, w * 0.35f)
            val y0 = a.r(0.01f, h * 0.4f)
            drawLine(dark.copy(alpha = 0.45f), a.p(xx, y0), a.p(xx + tilt * 0.3f, y0 + a.r(0.05f, 0.14f)), a.u * 0.004f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawWeeping(a: Art, s: TreeSpec, density: Float) {
    val count = (70 * density).toInt()
    repeat(count) { i ->
        val th = a.r(0.05f, 0.95f) * PI.toFloat()
        val sx = -cos(th) * s.crownRx * a.r(0.6f, 1.05f)
        val sy = s.crownY + sin(th) * s.crownRy * 0.2f - a.r(0f, 0.05f)
        val endY = a.r(0.04f, 0.2f)
        val endX = sx * a.r(1.05f, 1.25f)
        val col = a.c(s.leaves[(i % (s.leaves.size - 1)) + 1])
        val path = Path().apply {
            moveTo(a.x(sx), a.y(sy))
            quadraticTo(a.x(sx * 1.25f), a.y(sy - 0.05f), a.x(endX), a.y(endY))
        }
        drawPath(path, col, style = Stroke(width = a.u * 0.0055f, cap = StrokeCap.Round))
        // leaves along the strand
        var tt = 0.15f
        while (tt < 1f) {
            val px = (1 - tt) * (1 - tt) * sx + 2 * (1 - tt) * tt * sx * 1.25f + tt * tt * endX
            val py = (1 - tt) * (1 - tt) * sy + 2 * (1 - tt) * tt * (sy - 0.05f) + tt * tt * endY
            drawOval(col, Offset(a.x(px) - a.u * 0.006f, a.y(py)), Size(a.u * 0.012f, a.u * 0.02f))
            tt += 0.14f
        }
    }
}

private fun DrawScope.drawFruit(a: Art, at: Offset, r: Float, cols: List<Long>, crowned: Boolean) {
    val rad = a.u * r
    drawCircle(
        Brush.radialGradient(
            listOf(a.c(cols[2]), a.c(cols[1]), a.c(cols[0])),
            center = Offset(at.x - rad * 0.35f, at.y - rad * 0.35f), radius = rad * 1.3f,
        ),
        rad, at,
    )
    drawCircle(Color.White.copy(alpha = if (a.withered) 0f else 0.35f), rad * 0.22f, Offset(at.x - rad * 0.4f, at.y - rad * 0.4f))
    if (crowned) {
        val path = Path().apply {
            moveTo(at.x - rad * 0.3f, at.y - rad * 0.85f)
            lineTo(at.x - rad * 0.2f, at.y - rad * 1.25f)
            lineTo(at.x, at.y - rad * 1.0f)
            lineTo(at.x + rad * 0.2f, at.y - rad * 1.25f)
            lineTo(at.x + rad * 0.3f, at.y - rad * 0.85f)
            close()
        }
        drawPath(path, a.c(cols[0]))
    } else {
        drawLine(a.c(0xFF4A3426), Offset(at.x, at.y - rad * 0.8f), Offset(at.x + rad * 0.2f, at.y - rad * 1.3f), a.u * 0.004f, StrokeCap.Round)
    }
}

// ------------------------------------------------------------------ conifer

private fun DrawScope.drawSpruce(a: Art) {
    drawTrunk(a, 0f, 0.035f, 0.2f, 0f, a.c(0xFF3E2718), a.c(0xFF7A5034), treeSpec("eman"))
    val tiers = 9
    for (i in 0 until tiers) {
        val f = i.toFloat() / tiers
        val base = 0.08f + f * 0.58f
        val top = base + 0.2f - f * 0.06f
        val half = 0.25f * (1f - f * 0.88f)
        val teeth = (9 - i / 2).coerceAtLeast(4)
        val path = Path().apply {
            moveTo(a.x(0f), a.y(top))
            quadraticTo(a.x(-half * 0.45f), a.y(base + (top - base) * 0.45f), a.x(-half), a.y(base - 0.01f))
            for (k in 1..teeth) {
                val tx = -half + 2 * half * k / teeth
                val ux = -half + 2 * half * (k - 0.5f) / teeth
                quadraticTo(a.x(ux), a.y(base + 0.03f), a.x(tx), a.y(base - 0.012f - a.r(0f, 0.012f)))
            }
            quadraticTo(a.x(half * 0.45f), a.y(base + (top - base) * 0.45f), a.x(0f), a.y(top))
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(a.c(0xFF5C9A63), a.c(0xFF2F6B45), a.c(0xFF173F2A)),
                startY = a.y(top), endY = a.y(base - 0.02f),
            ),
        )
        // shadow side
        clipRect(left = a.cx, top = 0f, right = size.width, bottom = size.height) {
            drawPath(path, Color.Black.copy(alpha = 0.2f))
        }
        // needle texture
        repeat(10) {
            val nx = a.r(-half * 0.8f, half * 0.8f)
            val ny = a.r(base, base + (top - base) * (1f - kotlin.math.abs(nx) / half) * 0.8f)
            drawLine(a.c(0xFF7DB680).copy(alpha = 0.55f), a.p(nx, ny), a.p(nx + a.r(-0.02f, 0.02f), ny - 0.018f), a.u * 0.003f, StrokeCap.Round)
        }
    }
    if (!a.withered) {
        listOf(Offset(-0.12f, 0.15f), Offset(0.09f, 0.3f), Offset(-0.06f, 0.42f)).forEach {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF9C6A3C), Color(0xFF5B3A1E)), a.p(it.x - 0.004f, it.y + 0.004f), a.u * 0.015f),
                a.u * 0.011f, a.p(it.x, it.y),
            )
        }
    }
}

// ------------------------------------------------------------------ small plants

private fun DrawScope.drawCactus(a: Art) {
    fun column(x0: Float, y0: Float, w: Float, h: Float) {
        val left = a.x(x0 - w / 2)
        val right = a.x(x0 + w / 2)
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(a.c(0xFF2F6B3A), a.c(0xFF6DB06B), a.c(0xFF4C9152), a.c(0xFF27582F)), left, right,
            ),
            Offset(left, a.y(y0 + h)), Size(right - left, a.u * h),
            androidx.compose.ui.geometry.CornerRadius(a.u * w / 2),
        )
        for (k in 1..3) {
            val rx = x0 - w / 2 + w * k / 4
            drawLine(a.c(0xFF2A5E33).copy(alpha = 0.6f), a.p(rx, y0 + w * 0.4f), a.p(rx, y0 + h - w * 0.4f), a.u * 0.004f)
            var yy = y0 + w * 0.5f
            while (yy < y0 + h - w * 0.4f) {
                drawLine(Color(0xFFF3EFD8).copy(alpha = if (a.withered) 0.3f else 0.9f), a.p(rx, yy), a.p(rx + 0.008f, yy + 0.006f), a.u * 0.0025f)
                yy += 0.03f
            }
        }
    }
    // arms
    column(-0.13f, 0.17f, 0.075f, 0.19f)
    drawRoundRect(a.c(0xFF3F834A), Offset(a.x(-0.14f), a.y(0.215f)), Size(a.u * 0.12f, a.u * 0.06f), androidx.compose.ui.geometry.CornerRadius(a.u * 0.03f))
    column(0.13f, 0.27f, 0.07f, 0.17f)
    drawRoundRect(a.c(0xFF2E6A39), Offset(a.x(0.03f), a.y(0.31f)), Size(a.u * 0.12f, a.u * 0.055f), androidx.compose.ui.geometry.CornerRadius(a.u * 0.027f))
    column(0f, 0f, 0.13f, 0.55f)
    if (!a.withered) {
        for (i in 0 until 7) {
            rotate(i * 360f / 7, a.p(0f, 0.56f)) {
                drawOval(
                    Brush.verticalGradient(listOf(Color(0xFFFFB3C8), Color(0xFFE2486F)), a.y(0.62f), a.y(0.56f)),
                    Offset(a.x(-0.012f), a.y(0.615f)), Size(a.u * 0.024f, a.u * 0.055f),
                )
            }
        }
        drawCircle(Color(0xFFF7D35A), a.u * 0.013f, a.p(0f, 0.56f))
    }
}

private fun DrawScope.drawTulips(a: Art, t: Float) {
    val leafCol = listOf(0xFF3E7D3E, 0xFF77B465)
    // broad leaves
    listOf(-35f to 0.2f, -145f to 0.22f, -60f to 0.26f, -118f to 0.24f).forEach { (deg, len) ->
        leaf(a, a.p(0f, 0.01f), len, 0.045f, deg, leafCol[0], leafCol[1])
    }
    val heads = listOf(Triple(-0.1f, 0.33f, 0), Triple(0.01f, 0.45f, 1), Triple(0.11f, 0.37f, 2))
    val palettes = listOf(
        listOf(0xFF8E1424, 0xFFD62F3A, 0xFFF46A62),
        listOf(0xFFB5641A, 0xFFEFA92C, 0xFFFFD86B),
        listOf(0xFF7A1F5A, 0xFFC23D8A, 0xFFF08BC0),
    )
    heads.forEach { (hx, hy, pi) ->
        val stem = Path().apply {
            moveTo(a.x(hx * 0.2f - 0.004f), a.y(0f))
            quadraticTo(a.x(hx * 0.6f), a.y(hy * 0.5f), a.x(hx - 0.004f), a.y(hy - 0.04f))
            lineTo(a.x(hx + 0.004f), a.y(hy - 0.04f))
            quadraticTo(a.x(hx * 0.6f + 0.008f), a.y(hy * 0.5f), a.x(hx * 0.2f + 0.004f), a.y(0f))
            close()
        }
        drawPath(stem, a.c(0xFF4E8F45))
        val pal = palettes[pi]
        val open = 0.5f + 0.5f * t
        val bx = a.x(hx)
        val by = a.y(hy - 0.04f)
        val pw = a.u * 0.05f * open.coerceAtLeast(0.6f)
        val ph = a.u * 0.1f
        fun petal(dx: Float, tilt: Float, c0: Long, c1: Long) {
            rotate(tilt, Offset(bx + dx, by)) {
                val p = Path().apply {
                    moveTo(bx + dx - pw / 2, by)
                    cubicTo(bx + dx - pw * 0.7f, by - ph * 0.5f, bx + dx - pw * 0.35f, by - ph * 0.95f, bx + dx, by - ph)
                    cubicTo(bx + dx + pw * 0.35f, by - ph * 0.95f, bx + dx + pw * 0.7f, by - ph * 0.5f, bx + dx + pw / 2, by)
                    close()
                }
                drawPath(p, Brush.verticalGradient(listOf(a.c(c1), a.c(c0)), by - ph, by))
            }
        }
        petal(-pw * 0.45f, -14f * open, pal[0], pal[1])
        petal(pw * 0.45f, 14f * open, pal[0], pal[1])
        petal(0f, 0f, pal[1], pal[2])
        drawLine(Color.White.copy(alpha = if (a.withered) 0f else 0.35f), Offset(bx - pw * 0.1f, by - ph * 0.25f), Offset(bx - pw * 0.05f, by - ph * 0.8f), a.u * 0.004f, StrokeCap.Round)
    }
}

private fun DrawScope.drawSunflower(a: Art) {
    fun flower(hx: Float, hy: Float, r: Float, tilt: Float) {
        val stem = Path().apply {
            moveTo(a.x(hx * 0.3f - 0.008f), a.y(0f))
            quadraticTo(a.x(hx * 0.9f), a.y(hy * 0.5f), a.x(hx - 0.006f), a.y(hy))
            lineTo(a.x(hx + 0.006f), a.y(hy))
            quadraticTo(a.x(hx * 0.9f + 0.014f), a.y(hy * 0.5f), a.x(hx * 0.3f + 0.008f), a.y(0f))
            close()
        }
        drawPath(stem, Brush.horizontalGradient(listOf(a.c(0xFF3E7A35), a.c(0xFF79B058)), a.x(hx - 0.02f), a.x(hx + 0.02f)))
        leaf(a, a.p(hx * 0.55f, hy * 0.35f), 0.15f, 0.07f, -20f, 0xFF2F6B2E, 0xFF7DB75C)
        leaf(a, a.p(hx * 0.65f, hy * 0.55f), 0.13f, 0.06f, -160f, 0xFF2F6B2E, 0xFF7DB75C)
        val c = a.p(hx, hy)
        val rad = a.u * r
        rotate(tilt, c) {
            for (ring in 0..1) {
                val n = 22
                for (i in 0 until n) {
                    val ang = i * 360f / n + ring * 8f
                    rotate(ang, c) {
                        drawOval(
                            Brush.verticalGradient(
                                if (ring == 0) listOf(a.c(0xFFD98A12), a.c(0xFFF2B21E)) else listOf(a.c(0xFFF7C631), a.c(0xFFFFE27A)),
                                c.y - rad * 2.0f, c.y - rad * 0.7f,
                            ),
                            Offset(c.x - rad * 0.2f, c.y - rad * (if (ring == 0) 2.05f else 1.85f)),
                            Size(rad * 0.4f, rad * (if (ring == 0) 1.35f else 1.15f)),
                        )
                    }
                }
            }
            drawCircle(Brush.radialGradient(listOf(a.c(0xFF7A4A1E), a.c(0xFF3A220E)), c, rad), rad, c)
            // seeds in a sunflower spiral
            val golden = 137.508f * PI.toFloat() / 180f
            for (i in 0 until 90) {
                val rr = rad * 0.92f * sqrt(i / 90f)
                val th = i * golden
                drawCircle(a.c(0xFF9C6B33), rad * 0.035f, Offset(c.x + cos(th) * rr, c.y + sin(th) * rr))
            }
        }
    }
    flower(-0.08f, 0.38f, 0.045f, -10f)
    flower(0.06f, 0.55f, 0.06f, 8f)
}

private fun DrawScope.drawCotton(a: Art, density: Float) {
    val stem = a.c(0xFF6B3F2A)
    val tips = listOf(Offset(-0.16f, 0.3f), Offset(-0.06f, 0.42f), Offset(0.07f, 0.45f), Offset(0.17f, 0.32f), Offset(0f, 0.27f), Offset(-0.1f, 0.2f), Offset(0.11f, 0.22f))
    tips.forEach { tp ->
        drawLine(stem, a.p(0f, 0f), a.p(tp.x * 0.5f, tp.y * 0.5f), a.u * 0.012f, StrokeCap.Round)
        drawLine(stem, a.p(tp.x * 0.5f, tp.y * 0.5f), a.p(tp.x, tp.y), a.u * 0.007f, StrokeCap.Round)
    }
    // three-lobed leaves
    repeat((26 * density).toInt().coerceAtLeast(8)) {
        val tp = tips[it % tips.size]
        val at = a.p(tp.x * a.r(0.4f, 1f) + a.r(-0.04f, 0.04f), tp.y * a.r(0.5f, 1f) + a.r(-0.03f, 0.03f))
        val rot = a.r(0f, 360f)
        for (k in -1..1) {
            leaf(a, at, 0.055f, 0.03f, rot + k * 38f, 0xFF1F5126, 0xFF5C9A47)
        }
    }
    // white bolls
    tips.forEachIndexed { i, tp ->
        if (i.toFloat() / tips.size > density + 0.15f) return@forEachIndexed
        val c = a.p(tp.x, tp.y + 0.02f)
        val rad = a.u * 0.024f
        for (k in 0 until 5) {
            val th = k * 72f * PI.toFloat() / 180f - PI.toFloat() / 2
            val bc = Offset(c.x + cos(th) * rad * 0.65f, c.y + sin(th) * rad * 0.55f)
            drawCircle(
                Brush.radialGradient(listOf(Color.White, a.c(0xFFE6E2D8), a.c(0xFFBDB7AA)), Offset(bc.x - rad * 0.3f, bc.y - rad * 0.3f), rad * 1.1f),
                rad * 0.75f, bc,
            )
        }
        for (k in 0 until 4) {
            rotate(k * 90f + 45f, Offset(c.x, c.y + rad * 0.7f)) {
                drawOval(a.c(0xFF5A3420), Offset(c.x - rad * 0.12f, c.y + rad * 0.55f), Size(rad * 0.24f, rad * 0.6f))
            }
        }
    }
}

private fun DrawScope.drawPalm(a: Art) {
    // segmented curved trunk
    val segs = 15
    val topX = 0.07f
    val topY = 0.56f
    for (i in 0 until segs) {
        val t0 = i.toFloat() / segs
        val t1 = (i + 1).toFloat() / segs
        fun px(t: Float) = topX * t * t
        val w0 = 0.05f * (1 - t0 * 0.35f)
        val w1 = 0.05f * (1 - t1 * 0.35f)
        val path = Path().apply {
            moveTo(a.x(px(t0) - w0 / 2), a.y(topY * t0))
            lineTo(a.x(px(t1) - w1 / 2 - 0.004f), a.y(topY * t1))
            lineTo(a.x(px(t1) + w1 / 2 + 0.004f), a.y(topY * t1))
            lineTo(a.x(px(t0) + w0 / 2), a.y(topY * t0))
            close()
        }
        drawPath(
            path,
            Brush.horizontalGradient(
                listOf(a.c(0xFF5E4630), a.c(0xFFB8956A), a.c(0xFF8A6C4A), a.c(0xFF4E3A26)),
                a.x(px(t0) - w0 / 2), a.x(px(t0) + w0 / 2),
            ),
        )
        drawLine(a.c(0xFF4A3622), a.p(px(t1) - w1 / 2, topY * t1), a.p(px(t1) + w1 / 2, topY * t1), a.u * 0.004f)
    }
    val crown = Offset(topX, topY)
    if (!a.withered) {
        listOf(Offset(-0.022f, -0.025f), Offset(0.018f, -0.03f), Offset(0f, -0.012f)).forEach {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF8C6A3A), Color(0xFF4A3218)), a.p(crown.x + it.x - 0.006f, crown.y + it.y + 0.006f), a.u * 0.025f),
                a.u * 0.02f, a.p(crown.x + it.x, crown.y + it.y),
            )
        }
    }
    // fronds: arching midrib, leaflets hanging down from it
    val fronds = listOf(-0.95f, -0.75f, -0.5f, -0.25f, 0.25f, 0.5f, 0.75f, 0.95f, -0.1f, 0.1f, -0.6f, 0.6f)
    fronds.forEachIndexed { i, dir ->
        val back = i >= 8
        val len = if (back) 0.2f else 0.26f
        val ex = crown.x + dir * len
        val ey = crown.y - 0.05f - (1f - kotlin.math.abs(dir)) * 0.1f + (if (back) 0.04f else 0f)
        val mx = crown.x + dir * len * 0.45f
        val my = crown.y + 0.07f - (if (back) 0f else 0.01f)
        val dark = if (back) 0xFF1D5229 else 0xFF2A6E33
        val light = if (back) 0xFF377F3F else 0xFF5FA650
        var t = 0.1f
        while (t < 0.98f) {
            val bx = (1 - t) * (1 - t) * crown.x + 2 * (1 - t) * t * mx + t * t * ex
            val by = (1 - t) * (1 - t) * crown.y + 2 * (1 - t) * t * my + t * t * ey
            val l = 0.085f * (1f - t * 0.7f)
            for (side in listOf(-1f, 1f)) {
                val tipX = bx + dir * l * 0.45f + side * l * 0.25f
                val tipY = by - l * (0.75f + 0.2f * side)
                val pth = Path().apply {
                    moveTo(a.x(bx), a.y(by))
                    quadraticTo(a.x((bx + tipX) / 2 + side * 0.006f), a.y(by - l * 0.15f), a.x(tipX), a.y(tipY))
                }
                drawPath(pth, a.c(if (side < 0) dark else light), style = Stroke(a.u * 0.0075f, cap = StrokeCap.Round))
            }
            t += 0.055f
        }
        val mid = Path().apply {
            moveTo(a.x(crown.x), a.y(crown.y))
            quadraticTo(a.x(mx), a.y(my), a.x(ex), a.y(ey))
        }
        drawPath(mid, a.c(0xFF3F6E2C), style = Stroke(a.u * 0.006f, cap = StrokeCap.Round))
    }
}

