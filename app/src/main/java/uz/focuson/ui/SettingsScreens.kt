package uz.focuson.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.focuson.block.Blocking
import uz.focuson.data.BlockMode
import uz.focuson.data.Store
import uz.focuson.focus.Reminder

@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(C.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga", tint = C.ink)
            }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
        }
        content()
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title, color = C.primaryDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
    )
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.card)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.ink, fontSize = 16.sp)
            if (sub != null) Text(sub, color = C.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun NavRow(title: String, sub: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) C.ink else C.muted, fontSize = 16.sp)
            if (sub != null) Text(sub, color = C.muted, fontSize = 13.sp)
        }
        Text("›", color = C.muted, fontSize = 22.sp)
    }
}

@Composable
fun SettingsScreen(onPickApps: (BlockMode) -> Unit, onPermissions: () -> Unit, onTags: () -> Unit) {
    val context = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()
    val active by Store.active.collectAsStateWithLifecycle()
    val locked = active != null
    var pickTime by remember { mutableStateOf(false) }
    var pickGoal by remember { mutableStateOf(false) }
    var pickBreak by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val dndAccess = remember(resumeTick) { uz.focuson.focus.Dnd.hasAccess(context) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            message = runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { it.write(Store.exportJson().toByteArray()) }
                "Saqlandi. Faylni xavfsiz joyda asrang."
            }.getOrElse { "Saqlab bo'lmadi: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            "Sozlamalar", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp),
        )
        if (locked) {
            Text(
                "Fokus davom etyapti. Bloklash va qattiq rejimni fokus tugagandan keyin o'zgartirasiz.",
                color = C.danger, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Section("Bloklash") {
            SwitchRow(
                "Fokus vaqtida ilovalarni bloklash",
                "Bloklangan ilova ochilsa, uning o'rniga to'xtatish ekrani chiqadi.",
                s.blockingEnabled, !locked,
            ) { v -> Store.updateSettings { it.copy(blockingEnabled = v) } }
            ModeRow(
                "Hammasini bloklash",
                "Faqat siz ruxsat bergan ilovalar ochiladi.",
                s.mode == BlockMode.ALLOWLIST, !locked && s.blockingEnabled,
            ) { Store.updateSettings { it.copy(mode = BlockMode.ALLOWLIST) } }
            ModeRow(
                "Faqat tanlanganlarni bloklash",
                "Siz belgilagan ilovalar yopiladi, qolganlari ochiq.",
                s.mode == BlockMode.BLOCKLIST, !locked && s.blockingEnabled,
            ) { Store.updateSettings { it.copy(mode = BlockMode.BLOCKLIST) } }
            if (s.mode == BlockMode.ALLOWLIST) {
                NavRow(
                    "Ruxsat berilgan ilovalar",
                    "${s.allowed.size} ta tanlangan. Telefon, klaviatura doim ochiq.",
                    enabled = !locked && s.blockingEnabled,
                ) { onPickApps(BlockMode.ALLOWLIST) }
            } else {
                NavRow(
                    "Bloklanadigan ilovalar",
                    "${s.blocked.size} ta tanlangan",
                    enabled = !locked && s.blockingEnabled,
                ) { onPickApps(BlockMode.BLOCKLIST) }
            }
        }
        Section("Deep Focus") {
            SwitchRow(
                "Qattiq rejim",
                "Fokus vaqtida Focus On'dan chiqsangiz (bosh ekranga yoki bloklangan ilovaga), 10 soniya ichida qaytmasangiz o'simlik quriydi. Ruxsat berilgan ilovalar va ekranni o'chirish mumkin.",
                s.strict, !locked,
            ) { v -> Store.updateSettings { it.copy(strict = v) } }
        }
        Section("Fokus") {
            SwitchRow(
                "Fokusda bildirishnomalarni o'chirish",
                if (dndAccess) "Fokus vaqtida \"Bezovta qilinmasin\" yoqiladi, tugagach avvalgi holatiga qaytadi."
                else "Avval ruxsat kerak: yoqsangiz sozlamalar ochiladi, Focus On ga ruxsat bering.",
                s.dnd, !locked,
            ) { v ->
                if (v && !uz.focuson.focus.Dnd.hasAccess(context)) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Store.updateSettings { it.copy(dnd = v) }
            }
            NavRow("Kunlik maqsad", formatShort(s.dailyGoalMinutes * 60_000L)) { pickGoal = true }
            NavRow("Tanaffus uzunligi", "${s.breakMinutes} daqiqa") { pickBreak = true }
            SwitchRow(
                "Tanaffusni o'zi boshlash",
                "O'simlik o'sgach, dam olish taymeri avtomatik boshlanadi.",
                s.autoBreak, true,
            ) { v -> Store.updateSettings { it.copy(autoBreak = v) } }
        }
        Section("Ko'rinish") {
            listOf("system" to "Telefon bilan bir xil", "light" to "Yorug'", "dark" to "Qorong'i (tungi)").forEach { (m, label) ->
                ModeRow(label, if (m == "system") "Telefon tungi rejimga o'tsa, ilova ham o'tadi." else "", s.themeMode == m, true) {
                    Store.updateSettings { it.copy(themeMode = m) }
                }
            }
        }
        Section("Eslatma") {
            SwitchRow(
                "Kunlik eslatma",
                "Shu vaqtgacha o'simlik ekmagan bo'lsangiz eslatadi.",
                s.reminder, true,
            ) { v -> Store.updateSettings { it.copy(reminder = v) }; Reminder.schedule(context) }
            if (s.reminder) NavRow("Vaqti", "%02d:%02d".format(s.reminderMinute / 60, s.reminderMinute % 60)) { pickTime = true }
        }
        Section("Boshqa") {
            NavRow("Teglar", "Nomi va rangini o'zgartirish", onClick = onTags)
            SwitchRow("Tugaganda tebranish", null, s.vibrate, true) { v ->
                Store.updateSettings { it.copy(vibrate = v) }
            }
            NavRow("Ruxsatlar", "Bloklash, bildirishnoma va batareya", onClick = onPermissions)
        }
        Section("Zaxira nusxa") {
            NavRow("Faylga saqlash", "Tarix, teglar, tangalar va o'simliklar") {
                exportLauncher.launch("focuson-${java.time.LocalDate.now()}.json")
            }
            NavRow("Fayldan tiklash", "Oldin saqlangan fayldan qaytarish") {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            }
            val last = Store.lastAutoBackup()
            NavRow(
                "Hozir avtomatik zaxiraga yozish",
                "Har hafta o'zi yoziladi: Download/FocusOn papkasiga. Oxirgisi: " +
                    (if (last == 0L) "hali yo'q" else java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()),
            ) {
                message = runCatching {
                    val name = uz.focuson.data.AutoBackup.write(context)
                    if (name == null) "Bu telefonda avtomatik zaxira ishlamaydi. \"Faylga saqlash\" dan foydalaning."
                    else "Saqlandi: Download/FocusOn/$name"
                }.getOrElse { "Saqlab bo'lmadi: ${it.message}" }
            }
        }
    }

    if (pickGoal) ChoiceDialog(
        "Kunlik maqsad", listOf(30, 60, 90, 120, 150, 180, 240, 300), s.dailyGoalMinutes,
        label = { formatShort(it * 60_000L) },
        onPick = { v -> Store.updateSettings { it.copy(dailyGoalMinutes = v) }; pickGoal = false },
        onDismiss = { pickGoal = false },
    )
    if (pickBreak) ChoiceDialog(
        "Tanaffus uzunligi", listOf(3, 5, 10, 15, 20), s.breakMinutes,
        label = { "$it daqiqa" },
        onPick = { v -> Store.updateSettings { it.copy(breakMinutes = v) }; pickBreak = false },
        onDismiss = { pickBreak = false },
    )

    if (pickTime) TimeDialog(
        minuteOfDay = s.reminderMinute,
        onPick = { m -> Store.updateSettings { it.copy(reminderMinute = m) }; Reminder.schedule(context); pickTime = false },
        onDismiss = { pickTime = false },
    )
    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Fayldan tiklansinmi?") },
            text = { Text("Hozirgi tarix, teglar va tangalar fayldagisi bilan almashtiriladi.") },
            confirmButton = {
                TextButton(onClick = {
                    message = runCatching {
                        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                        Store.importJson(text)
                        "Tiklandi."
                    }.getOrElse { "Tiklab bo'lmadi: ${it.message}" }
                    confirmImport = null
                }) { Text("Tiklash", color = C.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Bekor qilish") } },
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("Yaxshi") } },
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String, options: List<Int>, selected: Int, label: (Int) -> String,
    onPick: (Int) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(o) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == selected, onClick = { onPick(o) })
                        Text(label(o), color = C.ink, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Yopish") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minuteOfDay: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eslatma vaqti") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Saqlash") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } },
    )
}

@Composable
private fun ModeRow(title: String, sub: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(8.dp))
        Column {
            Text(title, color = if (enabled) C.ink else C.muted, fontSize = 16.sp)
            if (sub.isNotEmpty()) Text(sub, color = C.muted, fontSize = 13.sp)
        }
    }
}

internal class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

internal fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val px = (40 * context.resources.displayMetrics.density).toInt()
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { ai ->
            AppInfo(
                ai.packageName,
                pm.getApplicationLabel(ai).toString(),
                runCatching { pm.getApplicationIcon(ai).toBitmap(px, px).asImageBitmap() }.getOrNull(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun AppPickerScreen(mode: BlockMode, onBack: () -> Unit) {
    val context = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()
    val apps by produceState<List<AppInfo>?>(null) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }
    val always = remember { Blocking.alwaysOpen(context) }
    var query by remember { mutableStateOf("") }
    val chosen = if (mode == BlockMode.ALLOWLIST) s.allowed else s.blocked

    fun toggle(pkg: String) = Store.updateSettings {
        if (mode == BlockMode.ALLOWLIST) {
            it.copy(allowed = if (pkg in it.allowed) it.allowed - pkg else it.allowed + pkg)
        } else {
            it.copy(blocked = if (pkg in it.blocked) it.blocked - pkg else it.blocked + pkg)
        }
    }

    ScreenScaffold(
        if (mode == BlockMode.ALLOWLIST) "Ruxsat berilgan ilovalar" else "Bloklanadigan ilovalar",
        onBack,
    ) {
        Text(
            if (mode == BlockMode.ALLOWLIST) "Belgilangan ilovalar fokus vaqtida ochiladi. Qolganlari yopiladi."
            else "Belgilangan ilovalar fokus vaqtida yopiladi.",
            color = C.muted, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Qidirish") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(14.dp),
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        val shown = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            .sortedByDescending { it.pkg in chosen }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { app ->
                val fixed = app.pkg in always
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !fixed) { toggle(app.pkg) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app.icon != null) Image(app.icon, null, Modifier.size(40.dp))
                    else Box(Modifier.size(40.dp).clip(CircleShape).background(C.line))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = C.ink, fontSize = 16.sp)
                        if (fixed) Text("Doim ochiq", color = C.muted, fontSize = 12.sp)
                    }
                    Checkbox(
                        checked = if (fixed) mode == BlockMode.ALLOWLIST else app.pkg in chosen,
                        onCheckedChange = { toggle(app.pkg) },
                        enabled = !fixed,
                    )
                }
            }
        }
    }
}

fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

fun batteryFree(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val a11y = remember(tick) { Blocking.isServiceEnabled(context) }
    val notif = remember(tick) { notificationsGranted(context) }
    val battery = remember(tick) { batteryFree(context) }
    val dnd = remember(tick) { uz.focuson.focus.Dnd.hasAccess(context) }

    ScreenScaffold("Ruxsatlar", onBack) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermCard(
                n = 1, title = "Ilovalarni bloklash", done = a11y,
                text = "Focus On qaysi ilova ochilganini bilishi uchun kerak. Ekrandagi narsalarni o'qimaydi.\n\n" +
                    "1) \"Ochish\" ni bosing.\n" +
                    "2) \"O'rnatilgan ilovalar\" (yoki \"Yuklab olingan ilovalar\") ichidan Focus On ni toping.\n" +
                    "3) Yoqing va \"Ruxsat berish\" ni bosing.",
                button = "Ochish",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                extra = if (!a11y) {
                    {
                        Text(
                            "Tugma kulrang bo'lib yoqilmasa: pastdagi tugmani bosing, yuqori o'ngdagi ⋮ ni bosing va " +
                                "\"Cheklangan sozlamalarga ruxsat berish\" ni tanlang. Keyin qaytadan urinib ko'ring.",
                            color = C.muted, fontSize = 13.sp,
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("Ilova ma'lumotlari") }
                    }
                } else null,
            )
            PermCard(
                n = 2, title = "Bildirishnomalar", done = notif,
                text = "Qolgan vaqtni va o'simlik o'sib bo'lganini bildirish uchun.",
                button = "Ruxsat berish",
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
            PermCard(
                n = 3, title = "Batareya cheklovisiz ishlash", done = battery,
                text = "Telefon Focus On'ni fonda o'chirib qo'ymasligi uchun. \"Ruxsat berish\" ni bosing.",
                button = "Ruxsat berish",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
            PermCard(
                n = 4, title = "Bildirishnomalarni o'chirish (ixtiyoriy)", done = dnd,
                text = "Fokus vaqtida \"Bezovta qilinmasin\" rejimini yoqish uchun. Ochilgan ro'yxatdan Focus On ni toping va ruxsat bering.",
                button = "Ochish",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
            PermCard(
                n = 5, title = "Samsung: uxlatmaslik", done = false,
                text = "Samsung fondagi ilovalarni o'zi \"uxlatib\" qo'yadi, shunda bloklash to'xtab qolishi mumkin.\n\n" +
                    "1) \"Ochish\" ni bosing (ilova ma'lumotlari ochiladi).\n" +
                    "2) \"Batareya\" ni bosing.\n" +
                    "3) \"Cheklanmagan\" ni tanlang.\n\n" +
                    "Yana: Sozlamalar → Batareya → Fon cheklovlari → \"Hech qachon uxlamaydigan ilovalar\" → + → Focus On.",
                button = "Ochish",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
            )
        }
    }
}

@Composable
private fun PermCard(
    n: Int,
    title: String,
    done: Boolean,
    text: String,
    button: String,
    onClick: () -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(if (done) C.primary else C.cardAlt),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                else Text("$n", color = C.primaryDark, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink, modifier = Modifier.weight(1f))
            if (done) Text("Tayyor", color = C.primary, fontSize = 14.sp)
        }
        if (!done) {
            Spacer(Modifier.height(8.dp))
            Text(text, color = C.ink, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onClick, shape = RoundedCornerShape(14.dp)) { Text(button) }
            extra?.let {
                Spacer(Modifier.height(8.dp))
                it()
            }
        }
    }
}
