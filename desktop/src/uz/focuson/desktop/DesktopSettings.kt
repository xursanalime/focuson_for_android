package uz.focuson.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.focuson.data.Store
import uz.focuson.ui.C
import uz.focuson.ui.ChoiceDialog
import uz.focuson.ui.ModeRow
import uz.focuson.ui.NavRow
import uz.focuson.ui.Section
import uz.focuson.ui.SwitchRow
import uz.focuson.ui.TimeDialog
import uz.focuson.ui.formatShort
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDate

/** Settings on the computer: the phone's settings minus blocking and limits. */
@Composable
fun DesktopSettings(onTags: () -> Unit) {
    val s by Store.settings.collectAsStateWithLifecycle()
    val active by Store.active.collectAsStateWithLifecycle()
    var pickTime by remember { mutableStateOf(false) }
    var pickGoal by remember { mutableStateOf(false) }
    var pickBreak by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var importFile by remember { mutableStateOf<File?>(null) }
    val dndOk = remember { Dnd.available() }
    var autostart by remember { mutableStateOf(Autostart.enabled()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            "Sozlamalar", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp),
        )
        Section("Fokus") {
            SwitchRow(
                "Fokusda bildirishnomalarni o'chirish",
                if (dndOk) "Fokus vaqtida GNOME \"Bezovta qilinmasin\" yoqiladi, tugagach qaytadi."
                else "Bu kompyuterda ishlamaydi.",
                s.dnd, dndOk && active == null,
            ) { v -> Store.updateSettings { it.copy(dnd = v) } }
            NavRow("Kunlik maqsad", formatShort(s.dailyGoalMinutes * 60_000L)) { pickGoal = true }
            NavRow("Tanaffus uzunligi", "${s.breakMinutes} daqiqa") { pickBreak = true }
            SwitchRow(
                "Tanaffusni o'zi boshlash",
                "O'simlik o'sgach, dam olish taymeri avtomatik boshlanadi.",
                s.autoBreak, true,
            ) { v -> Store.updateSettings { it.copy(autoBreak = v) } }
        }
        Section("Ko'rinish") {
            listOf("system" to "Kompyuter bilan bir xil", "light" to "Yorug'", "dark" to "Qorong'i (tungi)").forEach { (m, label) ->
                ModeRow(label, "", s.themeMode == m, true) { Store.updateSettings { it.copy(themeMode = m) } }
            }
        }
        Section("Eslatma") {
            SwitchRow("Kunlik eslatma", "Shu vaqtgacha o'simlik ekmagan bo'lsangiz eslatadi.", s.reminder, true) { v ->
                Store.updateSettings { it.copy(reminder = v) }
            }
            if (s.reminder) NavRow("Vaqti", "%02d:%02d".format(s.reminderMinute / 60, s.reminderMinute % 60)) { pickTime = true }
            SwitchRow("Haftalik hisobot", "Har yakshanba soat 20:00 da hafta yakuni haqida bildirishnoma.", s.weeklyReport, true) { v ->
                Store.updateSettings { it.copy(weeklyReport = v) }
            }
        }
        Section("Boshqa") {
            NavRow("Teglar", "Nomi va rangini o'zgartirish", onClick = onTags)
            SwitchRow(
                "Kompyuter yoqilganda ochilsin",
                if (Autostart.possible()) "Focus On panelda (tray) jim turadi, eslatmalar o'z vaqtida keladi."
                else "Avval Focus On'ni o'rnating (install.sh).",
                autostart, Autostart.possible(),
            ) { v -> Autostart.set(v); autostart = Autostart.enabled() }
        }
        Section("Telefon bilan birlashtirish") {
            NavRow(
                "Telefon zaxirasini qo'shish",
                "Telefondagi Download/FocusOn papkasidagi faylni kompyuterga o'tkazing va shu yerda tanlang. Bog' va statistika birlashadi.",
            ) {
                chooseFile("Telefon zaxirasini tanlang", FileDialog.LOAD, null)?.let { importFile = it }
            }
            NavRow(
                "Kompyuter zaxirasini saqlash",
                "Faylni telefonga o'tkazing: Sozlamalar → Zaxira nusxa → Fayldan tiklash → Birlashtirish.",
            ) {
                val name = "focuson-ubuntu-${LocalDate.now()}.json"
                chooseFile("Zaxirani qayerga saqlash", FileDialog.SAVE, name)?.let { f ->
                    message = runCatching { f.writeText(Store.exportJson()); "Saqlandi: ${f.absolutePath}" }
                        .getOrElse { "Saqlab bo'lmadi: ${it.message}" }
                }
            }
            Text(
                "Avtomatik zaxira har hafta: ${File(Store.dir, "backups").absolutePath}",
                color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    importFile?.let { f ->
        AlertDialog(
            onDismissRequest = { importFile = null },
            title = { Text("Zaxirani qanday qo'shamiz?") },
            text = {
                Text(
                    "Birlashtirish: telefondagi o'simliklar kompyuterdagilarga qo'shiladi, tangalar o'zgarmaydi.\n\n" +
                        "Almashtirish: kompyuterdagi hamma narsa fayldagisi bilan almashadi."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    message = runCatching {
                        val n = Store.importJson(f.readText(), merge = true)
                        "Birlashtirildi: $n ta yangi o'simlik qo'shildi."
                    }.getOrElse { "Bo'lmadi: ${it.message}" }
                    importFile = null
                }) { Text("Birlashtirish") }
            },
            dismissButton = {
                TextButton(onClick = {
                    message = runCatching { Store.importJson(f.readText(), merge = false); "Almashtirildi." }
                        .getOrElse { "Bo'lmadi: ${it.message}" }
                    importFile = null
                }) { Text("Almashtirish", color = C.danger) }
            },
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("Yaxshi") } },
        )
    }
    if (pickTime) TimeDialog(
        minuteOfDay = s.reminderMinute,
        onPick = { m -> Store.updateSettings { it.copy(reminderMinute = m) }; pickTime = false },
        onDismiss = { pickTime = false },
    )
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
}

private fun chooseFile(title: String, mode: Int, suggested: String?): File? {
    val d = FileDialog(null as Frame?, title, mode)
    d.directory = System.getProperty("user.home") + (if (mode == FileDialog.LOAD) "/Downloads" else "/Desktop")
    if (suggested != null) d.file = suggested else d.file = "*.json"
    d.isVisible = true
    val name = d.file ?: return null
    return File(d.directory, name)
}

/** "Open when the computer starts": a .desktop file in ~/.config/autostart. */
object Autostart {
    private val file = File(System.getProperty("user.home"), ".config/autostart/focuson.desktop")
    private val launcher: String? get() = System.getProperty("jpackage.app-path")

    fun possible() = launcher != null
    fun enabled() = file.exists()

    fun set(on: Boolean) {
        if (!on) { file.delete(); return }
        val exe = launcher ?: return
        file.parentFile.mkdirs()
        file.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=Focus On
            Exec="$exe" --tray
            Icon=focuson
            X-GNOME-Autostart-enabled=true
            """.trimIndent() + "\n"
        )
    }
}
