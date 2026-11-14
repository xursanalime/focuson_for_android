package uz.focuson.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.focuson.data.Catalog
import uz.focuson.data.GardenTheme
import uz.focuson.data.DateRange
import uz.focuson.data.Period
import uz.focuson.data.Periods
import uz.focuson.data.SessionRecord
import uz.focuson.data.Store
import uz.focuson.data.Streaks
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
fun GardenScreen() {
    val history by Store.history.collectAsStateWithLifecycle()
    val tags by Store.tags.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var period by rememberSaveable { mutableStateOf(Period.DAY) }
    androidx.compose.runtime.LaunchedEffect(NavRequest.gardenWeek) {
        if (NavRequest.gardenWeek) {
            period = Period.WEEK
            NavRequest.gardenWeek = false
        }
    }
    var anchorDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorDay)
    val range = Periods.range(period, anchor)
    val records = remember(history, range) { Periods.inRange(history, range, zone) }
    val grown = records.filter { it.success }
    val focused = grown.sumOf { it.focusedMs }
    val canGoNext = range.end.isBefore(today.plusDays(1))
    val withered = records.filter { !it.success }
    val coins by Store.coins.collectAsStateWithLifecycle()
    var clearTarget by remember { mutableStateOf<List<SessionRecord>?>(null) }
    val settings by Store.settings.collectAsStateWithLifecycle()
    val best by Store.bestStreak.collectAsStateWithLifecycle()
    val current = remember(history) { Streaks.current(Streaks.days(history, zone), today) }
    val theme = resolveTheme(settings.gardenTheme, best)
    var pickTheme by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    var tagStats by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Bog'im", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text(
                    "Seriya: $current kun ketma-ket · eng uzuni: $best",
                    color = C.primaryDark, fontSize = 13.sp,
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(C.chip)
                    .clickable { pickTheme = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(theme.title, color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("  ▾", color = C.muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(C.chip)
                .padding(4.dp),
        ) {
            Period.entries.forEach { p ->
                val sel = p == period
                Text(
                    p.title,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) C.primary else Color.Transparent)
                        .clickable { period = p; anchorDay = today.toEpochDay() }
                        .padding(vertical = 8.dp),
                    color = if (sel) C.onPrimary else C.ink, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { anchorDay = Periods.shift(period, anchor, -1).toEpochDay() }) {
                Icon(AppIcons.ChevronLeft, "Oldingi", tint = C.ink)
            }
            Text(
                Periods.label(period, range, today), Modifier.weight(1f),
                color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(
                onClick = { anchorDay = Periods.shift(period, anchor, 1).toEpochDay() },
                enabled = canGoNext,
            ) { Icon(AppIcons.ChevronRight, "Keyingi", tint = if (canGoNext) C.ink else C.line) }
        }

        val boardSeed = (range.start.toEpochDay() * 7 + period.ordinal).toInt()
        Box(Modifier.clickable { zoom = true }) {
            GardenBoard(records, seed = boardSeed, period = period, theme = theme)
        }
        Text(
            "Kattalashtirib ko'rish uchun bog'ni bosing",
            color = C.muted, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (zoom) GardenZoom(records, boardSeed, period, theme) { zoom = false }

        if (period == Period.WEEK) {
            val report = remember(history, range) { uz.focuson.data.WeeklyReport.build(history, range.start, zone) }
            if (report.focusedMs > 0 || report.withered > 0) Card("Haftalik hisobot") {
                Text("${formatDuration(report.focusedMs)} fokus", color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                uz.focuson.focus.weeklyLines(report).forEach { Text(it, color = C.muted, fontSize = 14.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Fokus vaqti", formatHours(focused), Modifier.weight(1f))
            StatTile("O'sgan", "${grown.size}", Modifier.weight(1f))
            StatTile("Qurigan", "${withered.size}", Modifier.weight(1f))
        }

        if (withered.isNotEmpty()) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.warnBg)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Bog'da ${withered.size} ta qurigan o'simlik bor",
                    color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { clearTarget = withered },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Tozalash · ")
                    CoinIcon(15.dp)
                    Text(" ${withered.size * Store.CLEAR_PRICE}")
                }
            }
        }

        Card("Teglar bo'yicha", action = "Batafsil" to { tagStats = true }) {
            val byTag = grown.groupBy { it.tagId }
                .map { (id, list) -> Store.tag(id) to list.sumOf { it.focusedMs } }
                .sortedByDescending { it.second }
            if (byTag.isEmpty()) Empty() else Row(verticalAlignment = Alignment.CenterVertically) {
                Donut(byTag.map { Color(it.first.color) to it.second.toFloat() }, Modifier.size(130.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    byTag.forEach { (tag, ms) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(tag.color)))
                            Spacer(Modifier.width(8.dp))
                            Text(tag.name, color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("${(ms * 100 / focused.coerceAtLeast(1))}%", color = C.muted, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(formatShort(ms), color = C.ink, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Card(
            when (period) {
                Period.DAY -> "Soatlar bo'yicha"
                Period.WEEK -> "Kunlar bo'yicha"
                Period.MONTH -> "Kunlar bo'yicha"
                Period.YEAR -> "Oylar bo'yicha"
            }
        ) {
            val buckets = remember(records, period) { Periods.buckets(period, range, records, zone) }
            if (buckets.all { it.second == 0L }) Empty() else Bars(buckets)
        }

        Card("Tarix") {
            if (records.isEmpty()) Empty() else {
                val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
                val dayFmt = remember { DateTimeFormatter.ofPattern("dd.MM") }
                records.asReversed().take(60).forEach { r ->
                    val start = java.time.Instant.ofEpochMilli(r.startAt).atZone(zone)
                    val end = java.time.Instant.ofEpochMilli(r.endAt).atZone(zone)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !r.success) { clearTarget = listOf(r) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlantView(r.speciesId, if (r.success) 1f else 0.6f, withered = !r.success, showGround = false, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (period == Period.DAY) "" else start.format(dayFmt) + "  ") +
                                    "${start.format(fmt)}–${end.format(fmt)}",
                                color = C.ink, fontSize = 14.sp,
                            )
                            val tag = Store.tag(r.tagId)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(tag.color)))
                                Spacer(Modifier.width(6.dp))
                                Text("${tag.name} · ${Catalog.byId(r.speciesId).name}", color = C.muted, fontSize = 12.sp)
                            }
                        }
                        Text(
                            if (r.success) formatShort(r.focusedMs) else "qurigan",
                            color = if (r.success) C.ink else C.danger, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        if (tags.isEmpty()) Unit
    }

    if (tagStats) TagStatsDialog(history, zone, today) { tagStats = false }

    if (pickTheme) ThemePicker(
        selected = theme, best = best, current = current,
        onPick = { t -> Store.updateSettings { it.copy(gardenTheme = t.name) }; pickTheme = false },
        onDismiss = { pickTheme = false },
    )

    clearTarget?.let { list ->
        val cost = list.size * Store.CLEAR_PRICE
        val enough = coins >= cost
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text(if (list.size == 1) "Qurigan o'simlikni olib tashlash" else "Qurigan o'simliklarni tozalash") },
            text = {
                Text(
                    (if (list.size == 1) "Bu o'simlik bog'dan va tarixdan olib tashlanadi."
                    else "${list.size} ta qurigan o'simlik bog'dan va tarixdan olib tashlanadi.") +
                        "\n\nNarxi: $cost tanga. Sizda: $coins." +
                        (if (enough) "" else "\nYana ${cost - coins} tanga kerak. Fokus qilib tanga yig'ing.")
                )
            },
            confirmButton = {
                if (enough) TextButton(onClick = { Store.clearWithered(list); clearTarget = null }) {
                    Text("Tozalash")
                } else TextButton(onClick = { clearTarget = null }) { Text("Yaxshi") }
            },
            dismissButton = {
                if (enough) TextButton(onClick = { clearTarget = null }) { Text("Bekor qilish") }
            },
        )
    }
}

@Composable
private fun StatTile(title: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(C.card).padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Text(title, color = C.muted, fontSize = 12.sp)
        Text(value, color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Card(title: String, action: Pair<String, () -> Unit>? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = C.primaryDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            action?.let { (label, go) ->
                Text(label, color = C.primary, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = go).padding(4.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Empty() = Text("Bu davrda hali ma'lumot yo'q.", color = C.muted, fontSize = 14.sp)

@Composable
private fun Donut(parts: List<Pair<Color, Float>>, modifier: Modifier) {
    Canvas(modifier) {
        val total = parts.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
        val stroke = size.minDimension * 0.2f
        val d = size.minDimension - stroke
        var startA = -90f
        parts.forEach { (col, v) ->
            val sweep = v / total * 360f
            drawArc(
                col, startA, (sweep - 1.2f).coerceAtLeast(0.5f), false,
                Offset(stroke / 2, stroke / 2), Size(d, d), style = Stroke(stroke),
            )
            startA += sweep
        }
    }
}

@Composable
private fun Bars(buckets: List<Pair<String, Long>>) {
    val max = buckets.maxOf { it.second }.coerceAtLeast(1)
    Column {
        Text("eng ko'pi: ${formatShort(max)}", color = C.muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = buckets.size
            val slot = size.width / n
            val bw = slot * 0.62f
            drawLine(C.line, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            buckets.forEachIndexed { i, (_, v) ->
                val h = (v.toFloat() / max) * (size.height - 6f)
                if (v > 0) drawRoundRect(
                    C.primary, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h),
                    androidx.compose.ui.geometry.CornerRadius(bw / 3),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            buckets.forEach { (label, _) ->
                Text(
                    label, Modifier.weight(1f), color = C.muted, fontSize = 10.sp, maxLines = 1,
                    softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/**
 * How big the garden is. A longer period gets a wider island with more squares,
 * so a day is a small patch and a year is a large field. If more plants were grown
 * than there are squares, the island grows further (up to a limit).
 */
object GardenSize {
    fun baseGrid(p: Period) = when (p) {
        Period.DAY -> 4
        Period.WEEK -> 6
        Period.MONTH -> 9
        Period.YEAR -> 13
    }

    fun maxGrid(p: Period) = when (p) {
        Period.DAY -> 8
        Period.WEEK -> 11
        Period.MONTH -> 15
        Period.YEAR -> 22
    }

    fun grid(p: Period, plants: Int): Int =
        maxOf(baseGrid(p), ceil(sqrt(plants.toFloat())).toInt()).coerceAtMost(maxGrid(p))

    /** Island width as a part of the screen width. */
    fun widthFraction(p: Period) = when (p) {
        Period.DAY -> 0.62f
        Period.WEEK -> 0.76f
        Period.MONTH -> 0.88f
        Period.YEAR -> 0.94f
    }

    /** Canvas height / width: room for the island, its soil side and the tallest plants. */
    fun heightRatio(p: Period, k: Int, theme: GardenTheme = GardenTheme.MEADOW): Float {
        val wf = widthFraction(p)
        val below = if (theme == GardenTheme.SKY) sideDepth(theme) + 0.05f else sideDepth(theme) + 0.02f
        return wf * (1.45f / k + 0.5f + below) + 0.12f
    }
}

/** The garden: an island with this period's plants on it, drawn in the chosen look. */
@Composable
fun GardenBoard(records: List<SessionRecord>, seed: Int, period: Period, theme: GardenTheme = GardenTheme.MEADOW) {
    val k = GardenLayout.grid(theme, period, records.size)
    val wf = GardenSize.widthFraction(period)
    val tiles = remember(k, seed, theme) { gardenTiles(theme, k, seed) }
    val shownRecords = records.take(tiles.size)
    val extra = records.size - shownRecords.size
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f / GardenSize.heightRatio(period, k, theme))) {
            drawGarden(theme, k, seed, shownRecords.mapIndexed { i, r -> tiles[i] to r }, wf)
        }
        if (records.isEmpty()) {
            Text(
                "Bu yerda hali o'simlik yo'q",
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                color = if (theme == GardenTheme.NIGHT) C.chip else C.muted, fontSize = 15.sp,
            )
        }
        if (extra > 0) {
            Text(
                "+$extra ta o'simlik joy bo'lmagani uchun ko'rinmayapti",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), color = C.muted, fontSize = 12.sp,
            )
        }
    }
}

/** The chosen look if it is open, otherwise the newest open one. */
fun resolveTheme(chosen: String, best: Int): GardenTheme {
    val open = GardenTheme.unlocked(best)
    return GardenTheme.byName(chosen)?.takeIf { it in open } ?: open.last()
}

private val previewPlants = listOf("eman", "olcha", "archa", "kungaboqar", "qayin", "lola", "olma")

@Composable
private fun ThemePicker(
    selected: GardenTheme, best: Int, current: Int,
    onPick: (GardenTheme) -> Unit, onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(C.card)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Bog' ko'rinishi", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Text(
                "Har kuni kamida bitta o'simlik o'stiring. Ketma-ket kunlar ko'paygan sari yangi bog'lar ochiladi.",
                color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            val sample = remember {
                previewPlants.mapIndexed { i, id -> SessionRecord(i * 1000L, i * 1000L + 1, 1, id, "oqish", true) }
            }
            GardenTheme.entries.forEach { t ->
                val open = t.days <= best
                val sel = t == selected
                Column(
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (sel) C.selected else C.cardAlt)
                        .clickable(enabled = open) { onPick(t) }
                        .padding(10.dp),
                ) {
                    Box {
                        GardenBoard(sample, seed = t.ordinal + 11, period = Period.DAY, theme = t)
                        if (!open) Box(
                            Modifier
                                .matchParentSize()
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "🔒  ${t.days} kun ketma-ket",
                                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(t.about, color = C.muted, fontSize = 13.sp)
                        }
                        Text(
                            when {
                                sel -> "Tanlangan"
                                open -> "Ochiq"
                                else -> "yana ${t.days - current} kun"
                            },
                            color = if (open) C.primary else C.muted, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Full-screen garden: zoom with + / − (or pinch), move around with a finger. */
@Composable
private fun GardenZoom(records: List<SessionRecord>, seed: Int, period: Period, theme: GardenTheme, onClose: () -> Unit) {
    var level by remember { mutableStateOf(1) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize().background(Color(0xF0101814)),
        ) {
            val w = maxWidth
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .pointerInput(Unit) {
                        // Two fingers: pinch to change the zoom step. One finger is left to scrolling.
                        awaitEachGesture {
                            var acc = 1f
                            do {
                                val ev = awaitPointerEvent()
                                if (ev.changes.count { it.pressed } >= 2) {
                                    acc *= ev.calculateZoom()
                                    ev.changes.forEach { it.consume() }
                                    if (acc > 1.25f) { level = (level + 1).coerceAtMost(4); acc = 1f }
                                    if (acc < 0.8f) { level = (level - 1).coerceAtLeast(1); acc = 1f }
                                }
                            } while (ev.changes.any { it.pressed })
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.requiredWidth(w * level).padding(vertical = 60.dp)) {
                    GardenBoard(records, seed, period, theme)
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ZoomButton("−", level > 1) { level-- }
                ZoomButton("${level}×", false) {}
                ZoomButton("+", level < 4) { level++ }
                ZoomButton("Yopish", true, onClose)
            }
        }
    }
}

@Composable
private fun ZoomButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (enabled) 0.92f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        color = Color(0xFF22332B), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
    )
}

/** Per tag: this week, this month, all time, number of plants, average length, last 8 weeks. */
@Composable
private fun TagStatsDialog(history: List<SessionRecord>, zone: ZoneId, today: LocalDate, onClose: () -> Unit) {
    val tags by Store.tags.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<String?>(null) }
    val grown = remember(history) { history.filter { it.success } }
    val week = Periods.range(Period.WEEK, today)
    val month = Periods.range(Period.MONTH, today)
    fun sum(list: List<SessionRecord>, r: DateRange) = Periods.inRange(list, r, zone).sumOf { it.focusedMs }
    val ids = (tags.map { it.id } + grown.map { it.tagId }).distinct()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(C.card)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Teglar statistikasi", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Text("Faqat o'sgan o'simliklar hisoblanadi. Tegni bosing: oxirgi 8 hafta.", color = C.muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            ids.forEach { id ->
                val tag = Store.tag(id)
                val mine = grown.filter { it.tagId == id }
                if (mine.isEmpty() && tag === uz.focuson.data.Tag.removed) return@forEach
                val total = mine.sumOf { it.focusedMs }
                Column(
                    Modifier
                        .padding(vertical = 5.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.cardAlt)
                        .clickable { open = if (open == id) null else id }
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(tag.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(tag.name, color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(formatHours(total), color = C.ink, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        MiniStat("Bu hafta", formatShort(sum(mine, week)), Modifier.weight(1f))
                        MiniStat("Bu oy", formatShort(sum(mine, month)), Modifier.weight(1f))
                        MiniStat("O'simlik", "${mine.size}", Modifier.weight(1f))
                        MiniStat("O'rtacha", if (mine.isEmpty()) "–" else formatShort(total / mine.size), Modifier.weight(1f))
                    }
                    if (open == id) {
                        Spacer(Modifier.height(10.dp))
                        val bars = (7 downTo 0).map { i ->
                            val r = Periods.range(Period.WEEK, today.minusWeeks(i.toLong()))
                            (if (i == 0) "bu" else "-$i") to sum(mine, r)
                        }
                        if (bars.all { it.second == 0L }) Text("Oxirgi 8 haftada yo'q.", color = C.muted, fontSize = 13.sp)
                        else Bars(bars)
                    }
                }
            }
            androidx.compose.material3.TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Yopish") }
        }
    }
}

@Composable
private fun MiniStat(title: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, color = C.muted, fontSize = 11.sp)
        Text(value, color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Asks the garden to open on this week (from the weekly report notification). */
object NavRequest {
    var gardenWeek by mutableStateOf(false)
}
