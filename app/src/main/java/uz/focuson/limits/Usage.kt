package uz.focuson.limits

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

/** One activity event, as the pure math below needs it. [cls] = the activity's class name. */
data class UsageEv(val pkg: String, val kind: Kind, val time: Long, val cls: String = "") {
    enum class Kind { START, STOP, SCREEN_OFF }
}

/**
 * Turns activity events into "milliseconds on screen today" per app, the same way the phone's
 * own Screen time does: each activity counts from RESUMED until STOPPED (a paused activity is
 * usually still visible, e.g. under a game overlay or a small video window). An app counts
 * while at least one of its activities is open; turning the screen off ends everything.
 * No Android here: testable.
 */
object UsageMath {
    fun foregroundMs(events: List<UsageEv>, now: Long): Map<String, Long> {
        val total = HashMap<String, Long>()
        val openActs = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()
        fun closeApp(pkg: String, at: Long) {
            val s = since.remove(pkg) ?: return
            if (at > s) total[pkg] = (total[pkg] ?: 0L) + (at - s)
        }
        for (e in events.sortedBy { it.time }) {
            when (e.kind) {
                UsageEv.Kind.START -> {
                    val acts = openActs.getOrPut(e.pkg) { HashSet() }
                    if (acts.isEmpty()) since[e.pkg] = e.time
                    acts += e.cls
                }
                UsageEv.Kind.STOP -> {
                    val acts = openActs[e.pkg] ?: continue
                    if (acts.remove(e.cls) && acts.isEmpty()) closeApp(e.pkg, e.time)
                }
                UsageEv.Kind.SCREEN_OFF -> {
                    since.keys.toList().forEach { closeApp(it, e.time) }
                    openActs.clear()
                }
            }
        }
        since.keys.toList().forEach { closeApp(it, now) }
        return total
    }
}

/** Reads today's app usage from Android (needs "usage access"). */
object UsageTracker {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Milliseconds each app was on screen since midnight. Empty without usage access. */
    fun usedToday(context: Context): Map<String, Long> {
        if (!hasAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val list = ArrayList<UsageEv>()
        val events = usm.queryEvents(startOfToday(), now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEv.Kind.START
                UsageEvents.Event.ACTIVITY_STOPPED -> UsageEv.Kind.STOP
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEv.Kind.SCREEN_OFF
                else -> null
            } ?: continue
            list += UsageEv(e.packageName ?: "", kind, e.timeStamp, e.className ?: "")
        }
        return UsageMath.foregroundMs(list, now)
    }
}
