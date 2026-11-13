package uz.focuson.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uz.focuson.block.Blocking
import uz.focuson.data.Store
import uz.focuson.limits.AppLimits
import uz.focuson.limits.UsageTracker

private val limitChoices = listOf(15, 30, 45, 60, 90, 120, 180, 240)

/** Daily time limits for apps. When an app's time is used up, it is closed until tomorrow. */
@Composable
fun LimitsScreen(onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    val limits by Store.limits.collectAsStateWithLifecycle()
    val pending by Store.pendingLimits.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    val settings by Store.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Store.applyDueLimits(); tick++ }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    val access = remember(tick) { UsageTracker.hasAccess(context) }
    val a11y = remember(tick) { Blocking.isServiceEnabled(context) }
    val usage by produceState(emptyMap<String, Long>(), tick, access) {
        value = withContext(Dispatchers.IO) { UsageTracker.usedToday(context) }
    }
    val apps by produceState<List<AppInfo>>(emptyList()) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }
    val byPkg = apps.associateBy { it.pkg }
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pickPasses by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            "Limitlar", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp),
        )
        Text(
            "Ilovaga kunlik vaqt qo'ying. Vaqt tugasa, ilova ertagacha yopiladi. Kerak bo'lsa 5 daqiqaga ochsa bo'ladi. " +
                "Limitni kamaytirish darhol ishlaydi, oshirish yoki olib tashlash esa ertadan.",
            color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        if (!access) Warn(
            "Limitlar ishlashi uchun \"Foydalanish ma'lumotlariga kirish\" ruxsati kerak. " +
                "Bosing, ro'yxatdan Focus On ni toping va yoqing.",
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        if (!a11y) Warn("Ilovalarni yopish uchun bloklash ruxsati ham kerak. Shu yerni bosing.", onOpenPermissions)

        Header("Limit qo'yilgan ilovalar")
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card)) {
            if (limits.isEmpty()) {
                Text("Hali limit yo'q.", color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            }
            limits.entries.sortedByDescending { usage[it.key] ?: 0L }.forEach { (pkg, min) ->
                val used = usage[pkg] ?: 0L
                val limitMs = min * 60_000L
                val part = (used.toFloat() / limitMs).coerceIn(0f, 1f)
                val over = used >= limitMs
                Row(
                    Modifier.fillMaxWidth().clickable { editing = pkg }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(byPkg[pkg])
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(byPkg[pkg]?.label ?: AppLimits.label(context, pkg), color = C.ink, fontSize = 16.sp)
                        Text(
                            if (over) "Bugun tugadi · ${formatShort(used)} / ${formatShort(limitMs)}"
                            else "Bugun: ${formatShort(used)} / ${formatShort(limitMs)} · ${formatShort(limitMs - used)} qoldi",
                            color = if (over) C.danger else C.muted, fontSize = 13.sp,
                        )
                        pending[pkg]?.let { p ->
                            Text(
                                if (p.minutes == null) "Ertadan limit olib tashlanadi" else "Ertadan: ${formatShort(p.minutes * 60_000L)}",
                                color = C.accent, fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { part },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (over) C.danger else if (part > 0.8f) C.accent else C.primary,
                            trackColor = C.line,
                            drawStopIndicator = {},
                        )
                    }
                }
            }
        }
        Button(
            onClick = { adding = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        ) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Ilova qo'shish")
        }

        Header("5 daqiqalik ruxsat")
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(C.card)
                .clickable { pickPasses = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Limit tugagach, kuniga necha marta", color = C.ink, fontSize = 16.sp)
                Text("Har bir ilova uchun ${settings.graceCount} marta, har safar 5 daqiqa", color = C.muted, fontSize = 13.sp)
            }
            Text("›", color = C.muted, fontSize = 22.sp)
        }

        val top = usage.entries
            .filter { it.key != context.packageName && it.key in byPkg && it.key !in Blocking.launchers(context) }
            .sortedByDescending { it.value }
            .take(8)
        if (top.isNotEmpty()) {
            Header("Bugun eng ko'p ishlatilganlar")
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card)) {
                top.forEach { (pkg, ms) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = pkg }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(byPkg[pkg])
                        Spacer(Modifier.width(12.dp))
                        Text(byPkg[pkg]?.label ?: pkg, color = C.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(formatShort(ms), color = C.ink, fontSize = 14.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (pkg in limits) "limit bor" else "limit qo'yish", color = C.primary, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    editing?.let { pkg ->
        LimitDialog(
            title = byPkg[pkg]?.label ?: AppLimits.label(context, pkg),
            current = limits[pkg],
            pending = pending[pkg],
            onPick = { m ->
                val now = Store.requestLimit(pkg, m)
                editing = null
                if (!now) notice = if (m == null) "Limit ertadan olib tashlanadi. Bugun hozirgi limit ishlayveradi."
                else "Yangi limit (${formatShort(m * 60_000L)}) ertadan boshlanadi. Bugun hozirgi limit ishlayveradi."
            },
            onCancelPending = { Store.cancelPending(pkg); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (adding) AppChooser(apps.filter { it.pkg !in Blocking.alwaysOpen(context) }, usage, onPick = { adding = false; editing = it }, onDismiss = { adding = false })
    notice?.let { n ->
        AlertDialog(
            onDismissRequest = { notice = null },
            text = { Text(n) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("Tushunarli") } },
        )
    }
    if (pickPasses) AlertDialog(
        onDismissRequest = { pickPasses = false },
        title = { Text("Kuniga necha marta 5 daqiqa?") },
        text = {
            Column {
                listOf(0 to "Hech qachon (qattiq)", 1 to "1 marta", 2 to "2 marta", 3 to "3 marta").forEach { (n, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            Store.updateSettings { it.copy(graceCount = n) }; pickPasses = false
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.graceCount == n, onClick = null, modifier = Modifier.padding(10.dp))
                        Text(label, color = C.ink, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pickPasses = false }) { Text("Yopish") } },
    )
}

@Composable
private fun Header(text: String) = Text(
    text, color = C.primaryDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
)

@Composable
private fun Warn(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.warnBg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        color = C.ink, fontSize = 14.sp,
    )
}

@Composable
private fun AppIcon(app: AppInfo?) {
    if (app?.icon != null) Image(app.icon, null, Modifier.size(40.dp))
    else Box(Modifier.size(40.dp).clip(CircleShape).background(C.line))
}

@Composable
private fun LimitDialog(
    title: String, current: Int?, pending: Store.PendingLimit?,
    onPick: (Int?) -> Unit, onCancelPending: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Kuniga qancha vaqt ochiq bo'lsin?", color = C.muted, fontSize = 14.sp)
                if (current != null) Text(
                    "Kamroq vaqt darhol ishlaydi. Ko'proq vaqt yoki olib tashlash ertadan.",
                    color = C.muted, fontSize = 12.sp,
                )
                if (pending != null) TextButton(onClick = onCancelPending) {
                    Text("Ertangi o'zgarishni bekor qilish", color = C.primary)
                }
                Spacer(Modifier.height(6.dp))
                limitChoices.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(m) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == m, onClick = null, modifier = Modifier.padding(8.dp))
                        Text(formatShort(m * 60_000L), color = C.ink, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Yopish") } },
        dismissButton = {
            if (current != null) TextButton(onClick = { onPick(null) }) { Text("Limitni olib tashlash", color = C.danger) }
        },
    )
}

@Composable
private fun AppChooser(apps: List<AppInfo>, usage: Map<String, Long>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(C.card).padding(16.dp),
        ) {
            Text("Ilovani tanlang", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            OutlinedTextField(
                value = query, onValueChange = { query = it }, placeholder = { Text("Qidirish") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(14.dp),
            )
            val shown = apps
                .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                .sortedByDescending { usage[it.pkg] ?: 0L }
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.pkg }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(app.pkg) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, color = C.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        usage[app.pkg]?.let { Text(formatShort(it), color = C.muted, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}
