package uz.focuson.block

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import uz.focuson.data.BlockMode

/** What a foreground app means for the running session. */
enum class Verdict {
    /** System pop-ups, keyboards, calls, Focus On itself: ignore. */
    NEUTRAL,
    /** The home screen or recent apps: the user left (counts in strict mode). */
    LEFT,
    /** A blocked app: show the block screen (and start the strict-mode countdown). */
    BLOCK,
    /** An app the user may use during focus: fine, cancels a strict-mode countdown. */
    ALLOWED,
}

/** Pure decision, kept free of Android calls so it can be tested. */
object BlockRules {
    fun verdict(
        pkg: String,
        blockingEnabled: Boolean,
        mode: BlockMode,
        allowed: Set<String>,
        blocked: Set<String>,
        neutral: Set<String>,
        launchers: Set<String>,
        isLaunchable: Boolean,
    ): Verdict {
        if (pkg in neutral) return Verdict.NEUTRAL
        if (pkg in launchers) return Verdict.LEFT
        // Things without a home-screen icon (edge panels, overlays) are not real apps.
        if (!isLaunchable) return Verdict.NEUTRAL
        if (!blockingEnabled) return Verdict.ALLOWED
        val isBlocked = when (mode) {
            BlockMode.BLOCKLIST -> pkg in blocked
            BlockMode.ALLOWLIST -> pkg !in allowed
        }
        return if (isBlocked) Verdict.BLOCK else Verdict.ALLOWED
    }
}

/** Android-side helpers for blocking. */
object Blocking {
    private val launchable = HashMap<String, Boolean>()
    private var neutralCache: Set<String>? = null
    private var launcherCache: Set<String>? = null

    private val SYSTEM_NEUTRAL = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.samsung.android.incallui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.emergency",
        "com.samsung.android.emergency",
        "com.android.settings.intelligence",
    )

    fun launchers(context: Context): Set<String> {
        launcherCache?.let { return it }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val set = context.packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }
            .filter { it != "com.android.settings" }
            .toSet()
        launcherCache = set
        return set
    }

    /** Never blocked and never counted as leaving: Focus On, phone calls, keyboards, system UI. */
    fun neutral(context: Context): Set<String> {
        neutralCache?.let { return it }
        val set = HashSet(SYSTEM_NEUTRAL)
        set += context.packageName
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { set += it }
        }
        runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList?.forEach { set += it.packageName }
        }
        neutralCache = set
        return set
    }

    /** Apps the picker shows as "always open". */
    fun alwaysOpen(context: Context): Set<String> = neutral(context) + launchers(context)

    fun clearCache() {
        neutralCache = null
        launcherCache = null
        launchable.clear()
    }

    fun isLaunchable(context: Context, pkg: String): Boolean =
        launchable.getOrPut(pkg) { context.packageManager.getLaunchIntentForPackage(pkg) != null }

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
