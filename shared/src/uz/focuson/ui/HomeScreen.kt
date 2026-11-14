package uz.focuson.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import uz.focuson.data.ActiveSession
import uz.focuson.data.Catalog
import uz.focuson.data.SessionRecord
import uz.focuson.data.Sound
import uz.focuson.data.Store
import java.util.Calendar

private val idlePhrases = listOf(
    "Telefonni chetga qo'ying. O'simlik o'ssin.",
    "Bitta vazifa tanlang va boshlang.",
    "Bugungi bog'ingizga yana bitta o'simlik.",
)
private val runPhrases = listOf(
    "Siz ishlayapsiz, o'simlik o'smoqda.",
    "Bir narsaga e'tibor. Shuning o'zi yetarli.",
    "Chalg'ish o'tib ketadi, natija qoladi.",
    "Kichik qadamlar katta bog' bo'ladi.",
    "Hozir faqat shu vazifa.",
)

@Composable
fun HomeScreen(
    needsSetup: Boolean,
    onOpenPermissions: () -> Unit,
    onOpenShop: () -> Unit,
    /** The computer has no app blocking, so it hides the blocking line. */
    showBlocking: Boolean = true,
) {
    val actions = LocalFocusActions.current
    val active by Store.active.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    val history by Store.history.collectAsStateWithLifecycle()
    val result by Store.lastResult.collectAsStateWithLifecycle()
    val coins by Store.coins.collectAsStateWithLifecycle()
    val tags by Store.tags.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active != null) {
            now = System.currentTimeMillis()
            actions.check()
            delay(250)
        }
    }

    var showSpecies by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showSound by remember { mutableStateOf(false) }
    var confirmGiveUp by remember { mutableStateOf(false) }

    val session = active
    val running = session != null
    val stopwatch = session?.stopwatch ?: settings.stopwatch
    val speciesId = session?.speciesId ?: settings.speciesId
    val tag = tags.firstOrNull { it.id == (session?.tagId ?: settings.tagId) } ?: Store.tag(settings.tagId)
    val growth = session?.growth(now) ?: 0f
    // `now` can lag a frame behind a session that just started: never let time go negative.
    val elapsed = session?.let { (now - it.startAt).coerceAtLeast(0L) } ?: 0L
    val clock = when {
        session == null && stopwatch -> 0L
        session == null -> settings.minutes * 60_000L
        stopwatch -> elapsed
        else -> session.endAt - now
    }
    val today = remember(history) { todayStats(history) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Focus On", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text(
                    if (today.first == 0) "Bugun hali o'simlik yo'q"
                    else "Bugun: ${today.first} ta o'simlik · ${formatShort(today.second)}",
                    color = C.primaryDark, fontSize = 13.sp,
                )
                val goalMs = settings.dailyGoalMinutes * 60_000L
                val goalPart = (today.second.toFloat() / goalMs).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { goalPart },
                        modifier = Modifier.width(120.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (goalPart >= 1f) C.accent else C.primary,
                        trackColor = C.line,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (goalPart >= 1f) "Maqsad bajarildi!" else "Maqsad: ${formatShort(goalMs)}",
                        color = C.muted, fontSize = 12.sp,
                    )
                }
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(C.chip)
                    .clickable(onClick = onOpenShop)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoinIcon(18.dp)
                Spacer(Modifier.width(6.dp))
                Text("$coins", color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }

        if (needsSetup && !running) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.warnBg)
                    .clickable(onClick = onOpenPermissions)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, null, tint = C.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bloklash uchun ruxsat kerak. Shu yerni bosing.", color = C.ink, fontSize = 13.sp)
            }
        }

        val breakEnd by Store.breakEnd.collectAsStateWithLifecycle()
        var breakNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(breakEnd) {
            while (breakEnd > System.currentTimeMillis()) {
                breakNow = System.currentTimeMillis()
                delay(500)
            }
            breakNow = System.currentTimeMillis()
            if (breakEnd in 1..breakNow) actions.breakFinished()
        }
        if (!running && breakEnd > breakNow) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.selected)
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tanaffus: ${formatClock(breakEnd - breakNow)} qoldi. Dam oling.",
                    color = C.ink, fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { actions.stopBreak() }) { Text("Tugatish") }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (!running) {
            ModeSwitch(stopwatch) { sw -> Store.updateSettings { it.copy(stopwatch = sw) } }
            Spacer(Modifier.height(8.dp))
        }
        val phrase = if (running) {
            runPhrases[Math.floorMod(elapsed / 60_000, runPhrases.size.toLong()).toInt()]
        } else if (stopwatch) "Xohlagancha ishlang. Kamida 10 daqiqa."
        else idlePhrases[Math.floorMod(today.first, idlePhrases.size)]
        Text(phrase, color = C.muted, fontSize = 14.sp, textAlign = TextAlign.Center)

        // The dial takes whatever height is left, so the buttons below always fit on screen.
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val d = minOf(maxWidth, maxHeight, 300.dp)
            FocusDial(
                minutes = settings.minutes,
                onMinutesChange = { m -> Store.updateSettings { it.copy(minutes = m) } },
                running = running || stopwatch,
                progress = if (running) growth else 0f,
                diameter = d,
            ) {
                Box(
                    Modifier.size(d * 0.7f).clip(CircleShape).clickable(enabled = !running) { showSpecies = true },
                    contentAlignment = Alignment.Center,
                ) {
                    PlantView(
                        speciesId = speciesId,
                        growth = if (running) growth else 1f,
                        sway = running,
                        modifier = Modifier.size(d * 0.66f),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(enabled = !running, onClick = { showTags = true }) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(tag.color)))
                Spacer(Modifier.width(7.dp))
                Text(tag.name, color = C.ink, fontSize = 14.sp)
            }
            if (!running) Chip(onClick = { showSpecies = true }) {
                Text(Catalog.byId(speciesId).name, color = C.ink, fontSize = 14.sp)
            }
            Chip(enabled = !running, onClick = { showSound = true }) {
                Text("♪ " + settings.sound.title, color = C.ink, fontSize = 14.sp)
            }
        }

        Text(formatClock(clock), fontSize = 46.sp, fontWeight = FontWeight.Light, color = C.ink)
        Spacer(Modifier.height(6.dp))

        when {
            !running -> Button(
                onClick = {
                    actions.start(settings.minutes, settings.speciesId, settings.tagId, settings.stopwatch)
                },
                modifier = Modifier.width(200.dp).height(50.dp),
                shape = RoundedCornerShape(27.dp),
            ) { Text("Ekish", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            stopwatch -> Button(
                onClick = {
                    if (elapsed >= ActiveSession.STOPWATCH_MIN_MS) actions.stopStopwatch()
                    else confirmGiveUp = true
                },
                modifier = Modifier.width(200.dp).height(50.dp),
                shape = RoundedCornerShape(27.dp),
            ) { Text("Tugatish", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            else -> OutlinedButton(
                onClick = { confirmGiveUp = true },
                modifier = Modifier.width(200.dp).height(50.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = C.danger),
            ) { Text("Taslim bo'lish", fontSize = 17.sp) }
        }

        Spacer(Modifier.height(8.dp))
        if (showBlocking) Text(
            blockingLine(settings.blockingEnabled, settings.strict, settings.mode.name),
            color = C.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }

    if (showSpecies) SpeciesDialog(
        selected = settings.speciesId,
        onPick = { id -> Store.updateSettings { it.copy(speciesId = id) }; showSpecies = false },
        onShop = { showSpecies = false; onOpenShop() },
        onDismiss = { showSpecies = false },
    )
    if (showTags) TagDialog(
        selected = settings.tagId,
        onPick = { id -> Store.updateSettings { it.copy(tagId = id) }; showTags = false },
        onDismiss = { showTags = false },
    )
    if (showSound) SoundDialog(
        selected = settings.sound,
        onPick = { snd -> Store.updateSettings { it.copy(sound = snd) }; showSound = false },
        onDismiss = { showSound = false },
    )
    if (confirmGiveUp) GiveUpDialog(
        stopwatch = stopwatch,
        onConfirm = { confirmGiveUp = false; actions.giveUp() },
        onDismiss = { confirmGiveUp = false },
    )
    result?.let { r -> ResultDialog(r, onDismiss = { Store.lastResult.value = null; Store.newThemes.value = emptyList() }) }
}

@Composable
private fun ModeSwitch(stopwatch: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(C.chip)
            .padding(4.dp),
    ) {
        listOf(false to "Taymer", true to "Sekundomer").forEach { (v, label) ->
            val sel = v == stopwatch
            Text(
                label,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) C.primary else Color.Transparent)
                    .clickable { onChange(v) }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                color = if (sel) C.onPrimary else C.ink, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun Chip(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(C.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

private fun blockingLine(enabled: Boolean, strict: Boolean, mode: String): String = buildString {
    append(
        when {
            !enabled -> "Bloklash o'chirilgan"
            mode == "ALLOWLIST" -> "Ruxsatlilardan boshqasi bloklanadi"
            else -> "Tanlangan ilovalar bloklanadi"
        }
    )
    if (strict) append(" · Qattiq rejim")
}

/** Count of grown plants today and their focused time. */
fun todayStats(history: List<SessionRecord>): Pair<Int, Long> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    val ok = history.filter { it.success && it.endAt >= start }
    return ok.size to ok.sumOf { it.focusedMs }
}

@Composable
fun Pill(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(C.chip)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = C.primaryDark, fontSize = 14.sp,
    )
}

@Composable
fun DialogCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(C.card)
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun SpeciesDialog(selected: String, onPick: (String) -> Unit, onShop: () -> Unit, onDismiss: () -> Unit) {
    val owned by Store.owned.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Text("O'simlik tanlang", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(Catalog.species.filter { it.id in owned }) { sp ->
                    val sel = sp.id == selected
                    Column(
                        Modifier
                            .padding(3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) C.selected else Color.Transparent)
                            .border(if (sel) 2.dp else 0.dp, if (sel) C.primary else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { onPick(sp.id) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlantView(sp.id, 1f, modifier = Modifier.size(78.dp))
                        Text(sp.name, color = C.ink, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            if (owned.size < Catalog.species.size) {
                TextButton(onClick = onShop, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Do'kondan yangi o'simliklar")
                }
            }
        }
    }
}

@Composable
private fun TagDialog(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val tags by Store.tags.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Text("Nima ustida ishlaysiz?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Spacer(Modifier.height(8.dp))
            tags.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (t.id == selected) C.selected else Color.Transparent)
                        .clickable { onPick(t.id) }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(t.color)))
                    Spacer(Modifier.width(12.dp))
                    Text(t.name, color = C.ink, fontSize = 17.sp)
                }
            }
            Text(
                "Teglarni Sozlamalar → Teglar bo'limida o'zgartirasiz.",
                color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun SoundDialog(selected: Sound, onPick: (Sound) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Text("Fon tovushi", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Text("Fokus vaqtida chalinadi.", color = C.muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Sound.entries.forEach { s ->
                Text(
                    s.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (s == selected) C.selected else Color.Transparent)
                        .clickable { onPick(s) }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    color = C.ink, fontSize = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun GiveUpDialog(stopwatch: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (stopwatch) "Hali 10 daqiqa bo'lmadi" else "Taslim bo'lasizmi?") },
        text = {
            Text(
                if (stopwatch) "Hozir to'xtatsangiz o'simlik qurib qoladi. Kamida 10 daqiqa ishlang."
                else "O'simlik qurib qoladi. Bu vaqt fokus sifatida hisoblanmaydi."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Ha, to'xtatish", color = C.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Davom etaman") } },
    )
}

@Composable
private fun ResultDialog(r: SessionRecord, onDismiss: () -> Unit) {
    val name = Catalog.byId(r.speciesId).name
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(C.card).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlantView(r.speciesId, if (r.success) 1f else 0.6f, withered = !r.success, modifier = Modifier.size(190.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                if (r.success) "$name o'sdi!" else "$name qurib qoldi",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (r.success) "${formatDuration(r.focusedMs)} diqqat bilan ishladingiz. Barakalla."
                else "Hechqisi yo'q. Keyingi safar albatta o'stirasiz.",
                color = C.muted, textAlign = TextAlign.Center, fontSize = 15.sp,
            )
            val opened by Store.newThemes.collectAsStateWithLifecycle()
            if (opened.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Yangi bog' ochildi: " + opened.joinToString { it.title } + "!",
                    color = C.primaryDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Text("Bog' bo'limida tanlashingiz mumkin.", color = C.muted, fontSize = 13.sp)
            }
            if (r.coins > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(20.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("+${r.coins} tanga", color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            val actions = LocalFocusActions.current
            val st by Store.settings.collectAsStateWithLifecycle()
            val onBreak by Store.breakEnd.collectAsStateWithLifecycle()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.success && onBreak == 0L) OutlinedButton(
                    onClick = { actions.startBreak(st.breakMinutes); onDismiss() },
                    shape = RoundedCornerShape(22.dp),
                ) { Text("Tanaffus ${st.breakMinutes} daq") }
                Button(onClick = onDismiss, shape = RoundedCornerShape(22.dp)) {
                    Text(if (r.success) "Yana ekish" else "Yangidan boshlash")
                }
            }
        }
    }
}
