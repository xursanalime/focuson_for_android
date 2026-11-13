package uz.focuson.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import uz.focuson.data.ActiveSession
import uz.focuson.data.SessionRecord
import uz.focuson.data.Store

/** Starts, checks and ends focus sessions. Safe to call from the UI, the service and the blocker. */
object FocusController {
    const val STRICT_GRACE_MS = 10_000L

    @Synchronized
    fun start(context: Context, minutes: Int, speciesId: String, tagId: String, stopwatch: Boolean) {
        if (Store.active.value != null) return
        val session = ActiveSession(
            startAt = System.currentTimeMillis(),
            durationMs = if (stopwatch) ActiveSession.STOPWATCH_MAX_MS else minutes * 60_000L,
            speciesId = speciesId,
            tagId = tagId,
            stopwatch = stopwatch,
        )
        Store.setActive(session)
        Store.lastResult.value = null
        if (Store.breakUntil() > 0) BreakTimer.stop(context)
        Dnd.begin(context)
        uz.focuson.widget.FocusWidget.refresh(context)
        scheduleAlarm(context, session.endAt)
        FocusService.start(context)
    }

    fun giveUp(context: Context) = finish(context, success = false)

    /** Stopwatch: stop now. At least 10 minutes grows the plant, less withers it. */
    @Synchronized
    fun stopStopwatch(context: Context) {
        val s = Store.active.value ?: return
        val ok = System.currentTimeMillis() - s.startAt >= ActiveSession.STOPWATCH_MIN_MS
        finish(context, success = ok)
    }

    /** Ends the session if its time is up or the strict-mode warning ran out. */
    @Synchronized
    fun check(context: Context) {
        val s = Store.active.value ?: return
        val now = System.currentTimeMillis()
        when {
            now >= s.endAt -> finish(context, success = true)
            s.warnDeadline in 1..now -> finish(context, success = false)
        }
    }

    /** Strict mode: the user left Focus On. They have a few seconds to come back. */
    @Synchronized
    fun leave(context: Context) {
        val s = Store.active.value ?: return
        if (!Store.settings.value.strict || s.warnDeadline > 0) return
        val deadline = System.currentTimeMillis() + STRICT_GRACE_MS
        Store.setActive(s.copy(warnDeadline = deadline))
        Notifications.showWarning(context, s, deadline)
        buzz(context, longArrayOf(0, 120))
    }

    /** The user is back in Focus On (or in an allowed app, or put the phone down). */
    @Synchronized
    fun clearWarning(context: Context) {
        check(context)
        val s = Store.active.value ?: return
        if (s.warnDeadline > 0) {
            Store.setActive(s.copy(warnDeadline = 0L))
            Notifications.cancelWarning(context)
        }
    }

    @Synchronized
    private fun finish(context: Context, success: Boolean) {
        val s = Store.active.value ?: return
        val now = System.currentTimeMillis()
        Store.setActive(null)
        val end = if (success && !s.stopwatch) s.endAt else minOf(now, s.endAt)
        val minutes = ((end - s.startAt) / 60_000).toInt()
        val record = SessionRecord(
            startAt = s.startAt,
            endAt = end,
            plannedMs = s.durationMs,
            speciesId = s.speciesId,
            tagId = s.tagId,
            success = success,
            coins = if (success) minutes else 0,
            stopwatch = s.stopwatch,
        )
        Store.addRecord(record)
        Store.lastResult.value = record
        cancelAlarm(context)
        Notifications.cancelWarning(context)
        FocusService.stop(context)
        Dnd.end(context)
        uz.focuson.widget.FocusWidget.refresh(context)
        Notifications.showResult(context, record)
        val st = Store.settings.value
        if (success && !s.stopwatch && st.autoBreak) BreakTimer.start(context, st.breakMinutes)
        if (success && Store.settings.value.vibrate) buzz(context, longArrayOf(0, 180, 120, 260))
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAlarm(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context))
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    private fun buzz(context: Context, pattern: LongArray) {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
