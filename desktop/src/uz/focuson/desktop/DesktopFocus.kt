package uz.focuson.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.focuson.data.ActiveSession
import uz.focuson.data.Catalog
import uz.focuson.data.SessionRecord
import uz.focuson.data.Store
import uz.focuson.data.WeeklyReport
import uz.focuson.focus.weeklyLines
import uz.focuson.ui.FocusActions
import uz.focuson.ui.formatDuration
import uz.focuson.ui.todayStats
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Focus sessions on the computer. Nothing is blocked here (the user asked for that);
 * the timer, plant, coins, garden and statistics work exactly like on the phone.
 */
object DesktopFocus : FocusActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sound: DesktopSound? = null
    private var started = false

    /** Runs for the whole life of the app: ends sessions and breaks on time, sends reminders. */
    fun startLoop() {
        if (started) return
        started = true
        if (Store.active.value != null) startSound()
        scope.launch {
            var minuteTick = 0
            while (true) {
                check()
                val b = Store.breakUntil()
                if (b in 1..System.currentTimeMillis()) breakFinished()
                if (minuteTick++ % 30 == 0) runCatching { schedules() }
                delay(1_000)
            }
        }
    }

    @Synchronized
    override fun start(minutes: Int, speciesId: String, tagId: String, stopwatch: Boolean) {
        if (Store.active.value != null) return
        val s = ActiveSession(
            startAt = System.currentTimeMillis(),
            durationMs = if (stopwatch) ActiveSession.STOPWATCH_MAX_MS else minutes * 60_000L,
            speciesId = speciesId,
            tagId = tagId,
            stopwatch = stopwatch,
        )
        Store.setActive(s)
        Store.lastResult.value = null
        if (Store.breakUntil() > 0) stopBreak()
        Dnd.begin()
        startSound()
    }

    override fun giveUp() = finish(success = false)

    @Synchronized
    override fun stopStopwatch() {
        val s = Store.active.value ?: return
        finish(System.currentTimeMillis() - s.startAt >= ActiveSession.STOPWATCH_MIN_MS)
    }

    @Synchronized
    override fun check() {
        val s = Store.active.value ?: return
        if (System.currentTimeMillis() >= s.endAt) finish(success = true)
    }

    @Synchronized
    private fun finish(success: Boolean) {
        val s = Store.active.value ?: return
        val now = System.currentTimeMillis()
        Store.setActive(null)
        val end = if (success && !s.stopwatch) s.endAt else minOf(now, s.endAt)
        val record = SessionRecord(
            startAt = s.startAt,
            endAt = end,
            plannedMs = s.durationMs,
            speciesId = s.speciesId,
            tagId = s.tagId,
            success = success,
            coins = if (success) ((end - s.startAt) / 60_000).toInt() else 0,
            stopwatch = s.stopwatch,
        )
        Store.addRecord(record)
        Store.lastResult.value = record
        sound?.stop()
        sound = null
        Dnd.end()
        val name = Catalog.byId(s.speciesId).name
        if (success) Notify.show("$name o'sdi!", "${formatDuration(record.focusedMs)} diqqat bilan ishladingiz. +${record.coins} tanga.")
        else Notify.show("$name qurib qoldi", "Hechqisi yo'q. Yangi o'simlik eking va yana urinib ko'ring.")
        val st = Store.settings.value
        if (success && !s.stopwatch && st.autoBreak) startBreak(st.breakMinutes)
    }

    private fun startSound() {
        sound?.stop()
        sound = DesktopSound(Store.settings.value.sound).also { it.start() }
    }

    override fun startBreak(minutes: Int) {
        Store.setBreakUntil(System.currentTimeMillis() + minutes * 60_000L)
        Notify.show("Tanaffus: $minutes daqiqa", "Dam oling: suv iching, cho'zilib oling.")
    }

    override fun stopBreak() = Store.setBreakUntil(0L)

    override fun breakFinished() {
        if (Store.breakUntil() == 0L) return
        Store.setBreakUntil(0L)
        Notify.show("Tanaffus tugadi", "Yana bitta o'simlik ekamizmi?")
    }

    /** Daily reminder, Sunday-evening report and the weekly backup. Checked every 30 seconds. */
    private fun schedules() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()
        val st = Store.settings.value
        val minuteOfDay = now.hour * 60 + now.minute

        if (st.reminder && minuteOfDay >= st.reminderMinute && Store.extraString("lastReminder") != today.toString()) {
            Store.putExtra("lastReminder", today.toString())
            if (Store.active.value == null && todayStats(Store.history.value).first == 0) {
                Notify.show("Bugun hali o'simlik ekmadingiz", "25 daqiqa ajrating. Bog'ingiz sizni kutyapti.")
            }
        }

        val weekStart = today.with(DayOfWeek.MONDAY).toString()
        if (st.weeklyReport && today.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 20 &&
            Store.extraString("lastWeekly") != weekStart
        ) {
            Store.putExtra("lastWeekly", weekStart)
            val r = WeeklyReport.build(Store.history.value, today, zone)
            if (r.focusedMs > 0 || r.withered > 0) {
                Notify.show("Hafta yakuni: ${formatDuration(r.focusedMs)} fokus", weeklyLines(r).joinToString("\n"))
            }
        }

        if (System.currentTimeMillis() - Store.extraLong("lastAutoBackup") > 7 * 24 * 3600 * 1000L) {
            writeBackup(File(Store.dir, "backups"), "focuson-auto-$today.json")
            Store.putExtra("lastAutoBackup", System.currentTimeMillis())
            File(Store.dir, "backups").listFiles { f -> f.name.startsWith("focuson-auto-") }
                ?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
        }
    }

    fun writeBackup(folder: File, name: String): File {
        folder.mkdirs()
        val f = File(folder, name)
        f.writeText(Store.exportJson())
        return f
    }
}
