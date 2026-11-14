package uz.focuson.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uz.focuson.data.Store
import uz.focuson.data.WeeklyReport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Sunday 20:00: a short summary of the week as a notification. */
object WeeklyAlarm {
    const val HOUR = 20

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 4, Intent(context, WeeklyReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun next(now: LocalDateTime): LocalDateTime {
        var t = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(HOUR, 0)
        if (!t.isAfter(now)) t = t.plusWeeks(1)
        return t
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(intent(context))
        if (!Store.settings.value.weeklyReport) return
        val zone = ZoneId.systemDefault()
        val at = next(LocalDateTime.now(zone)).atZone(zone).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context))
    }
}

class WeeklyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val zone = ZoneId.systemDefault()
        val r = WeeklyReport.build(Store.history.value, LocalDate.now(zone), zone)
        if (r.focusedMs > 0 || r.withered > 0) Notifications.showWeekly(context, r)
        WeeklyAlarm.schedule(context)
    }
}
