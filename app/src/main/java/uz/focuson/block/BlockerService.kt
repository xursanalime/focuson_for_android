package uz.focuson.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import uz.focuson.data.Store
import uz.focuson.focus.FocusController
import uz.focuson.limits.AppLimits

/**
 * Watches which app comes to the front. Daily app limits are checked always. While a focus session runs:
 * a blocked app is covered by the block screen; in strict mode, leaving
 * Focus On (home screen or a blocked app) starts a 10 second countdown.
 * It reads nothing on the screen, only the name of the app that opened.
 */
class BlockerService : AccessibilityService() {
    private var lastPkg: String? = null
    private var lastAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(applicationContext)
        Blocking.clearCache()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            AppLimits.onLeft()
            return
        }
        if (Store.active.value != null) FocusController.check(applicationContext)
        val st = Store.settings.value
        val launchable = Blocking.isLaunchable(this, pkg)

        if (Store.active.value != null) {
            val verdict = BlockRules.verdict(
                pkg = pkg,
                blockingEnabled = st.blockingEnabled,
                mode = st.mode,
                allowed = st.allowed,
                blocked = st.blocked,
                neutral = Blocking.neutral(this),
                launchers = Blocking.launchers(this),
                isLaunchable = launchable,
            )
            Log.d(TAG, "front=$pkg verdict=$verdict")
            when (verdict) {
                Verdict.NEUTRAL -> return
                Verdict.LEFT -> FocusController.leave(applicationContext)
                Verdict.ALLOWED -> FocusController.clearWarning(applicationContext)
                Verdict.BLOCK -> {
                    FocusController.leave(applicationContext)
                    AppLimits.onLeft()
                    showBlock(pkg, BlockActivity.MODE_FOCUS)
                    return
                }
            }
        }

        // Daily app limits work all the time, with or without a focus session.
        if (pkg in Blocking.neutral(this) || !launchable) return
        AppLimits.onForeground(this, pkg)
    }

    private fun showBlock(pkg: String, mode: String) {
        // One app can fire several events in a row; react once.
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastPkg && now - lastAt < 1_000) return
        lastPkg = pkg
        lastAt = now
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_PKG, pkg)
                .putExtra(BlockActivity.EXTRA_MODE, mode)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
        )
    }

    override fun onInterrupt() {}

    private companion object {
        const val TAG = "FocusOnBlocker"
    }
}
