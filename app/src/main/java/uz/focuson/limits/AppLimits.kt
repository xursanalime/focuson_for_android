package uz.focuson.limits

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import uz.focuson.block.BlockActivity
import uz.focuson.data.Store

/** Pure decision for a limited app. */
object LimitRules {
    const val GRACE_MS = 5 * 60_000L

    sealed interface State {
        /** Allowed for [leftMs] more. [graced] = running on a 5-minute pass. */
        data class Open(val leftMs: Long, val graced: Boolean) : State
        data object Over : State
    }

    fun state(usedMs: Long, limitMs: Long, graceUntil: Long, now: Long): State = when {
        graceUntil > now -> State.Open(graceUntil - now, graced = true)
        usedMs < limitMs -> State.Open(limitMs - usedMs, graced = false)
        else -> State.Over
    }
}

/**
 * Daily app limits. The blocker service tells us which app is in front; while a limited
 * app is open we set a timer for the moment its time runs out, then cover it.
 */
object AppLimits {
    private const val TAG = "FocusOnLimits"
    private val handler = Handler(Looper.getMainLooper())
    private var watching: String? = null
    private var ctx: Context? = null

    private val check = Runnable {
        val c = ctx ?: return@Runnable
        val p = watching ?: return@Runnable
        evaluate(c, p)
    }
    private val warn = Runnable {
        val c = ctx ?: return@Runnable
        val p = watching ?: return@Runnable
        uz.focuson.focus.Notifications.showLimitSoon(c, label(c, p))
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** A real app came to the front. */
    fun onForeground(context: Context, pkg: String) {
        ctx = context.applicationContext
        Store.applyDueLimits()
        handler.removeCallbacks(check)
        handler.removeCallbacks(warn)
        if (Store.limits.value[pkg] == null) {
            watching = null
            return
        }
        watching = pkg
        evaluate(context, pkg)
    }

    /** Focus On itself (or its block screen) is in front. */
    fun onLeft() {
        watching = null
        handler.removeCallbacks(check)
        handler.removeCallbacks(warn)
    }

    private fun evaluate(context: Context, pkg: String) {
        val limitMin = Store.limits.value[pkg] ?: return
        if (!UsageTracker.hasAccess(context)) return
        val used = UsageTracker.usedToday(context)[pkg] ?: 0L
        val now = System.currentTimeMillis()
        val st = LimitRules.state(used, limitMin * 60_000L, Store.grant(pkg).until, now)
        Log.d(TAG, "$pkg used=${used / 1000}s limit=${limitMin}m state=$st")
        when (st) {
            LimitRules.State.Over -> {
                watching = null
                context.startActivity(
                    Intent(context, BlockActivity::class.java)
                        .putExtra(BlockActivity.EXTRA_PKG, pkg)
                        .putExtra(BlockActivity.EXTRA_MODE, BlockActivity.MODE_LIMIT)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                )
            }
            is LimitRules.State.Open -> {
                handler.postDelayed(check, st.leftMs + 1_500)
                if (!st.graced && st.leftMs > 5 * 60_000L) handler.postDelayed(warn, st.leftMs - 5 * 60_000L)
            }
        }
    }

    /** How many 5-minute passes are left today for [pkg]. */
    fun passesLeft(pkg: String): Int =
        (Store.settings.value.graceCount - Store.grant(pkg).count).coerceAtLeast(0)

    /** Uses one 5-minute pass. Returns false if none are left. */
    fun grant(pkg: String): Boolean {
        if (passesLeft(pkg) <= 0) return false
        val g = Store.grant(pkg)
        Store.setGrant(pkg, g.copy(count = g.count + 1, until = System.currentTimeMillis() + LimitRules.GRACE_MS))
        return true
    }
}
