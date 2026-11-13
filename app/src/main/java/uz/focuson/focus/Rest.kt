package uz.focuson.focus

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import uz.focuson.data.Store

/** Do Not Disturb while focusing. The phone's own setting is saved first and put back afterwards. */
object Dnd {
    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(context: Context): Boolean = nm(context).isNotificationPolicyAccessGranted

    fun begin(context: Context) {
        if (!Store.settings.value.dnd || !hasAccess(context)) return
        val nm = nm(context)
        if (Store.savedDnd() == -1) Store.setSavedDnd(nm.currentInterruptionFilter)
        // "Priority only": the user's own DND exceptions (calls, alarms) still come through.
        runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
    }

    fun end(context: Context) {
        val saved = Store.savedDnd()
        if (saved == -1) return
        val restore = if (saved == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) NotificationManager.INTERRUPTION_FILTER_ALL else saved
        if (hasAccess(context)) runCatching { nm(context).setInterruptionFilter(restore) }
        Store.setSavedDnd(-1)
    }
}

/** A short rest after a grown plant. Only a countdown and a notice at the end; nothing is blocked. */
object BreakTimer {
    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 3, Intent(context, BreakReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun start(context: Context, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        Store.setBreakUntil(end)
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, intent(context))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, intent(context))
        Notifications.showBreak(context, end)
    }

    fun stop(context: Context) {
        Store.setBreakUntil(0L)
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
        Notifications.cancelBreak(context)
    }

    fun finished(context: Context) {
        if (Store.breakUntil() == 0L) return
        Store.setBreakUntil(0L)
        Notifications.cancelBreak(context)
        Notifications.showBreakOver(context)
    }
}

class BreakReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        BreakTimer.finished(context)
    }
}
