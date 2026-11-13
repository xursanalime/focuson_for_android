package uz.focuson.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uz.focuson.data.Store
import uz.focuson.ui.todayStats
import java.util.Calendar

/** Daily reminder at the chosen time, only on days with no grown plant yet. */
object Reminder {
    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 2, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun nextTime(minuteOfDay: Int, now: Calendar = Calendar.getInstance()): Long {
        val c = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now.timeInMillis) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val s = Store.settings.value
        am.cancel(intent(context))
        if (!s.reminder) return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime(s.reminderMinute), intent(context))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        if (Store.active.value == null && todayStats(Store.history.value).first == 0) {
            Notifications.showReminder(context)
        }
        Reminder.schedule(context)
    }
}
