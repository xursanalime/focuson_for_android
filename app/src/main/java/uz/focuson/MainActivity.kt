package uz.focuson

import android.app.Application
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.focuson.block.Blocking
import uz.focuson.data.BlockMode
import uz.focuson.data.Store
import uz.focuson.focus.FocusController
import uz.focuson.focus.FocusService
import uz.focuson.focus.Notifications
import uz.focuson.focus.Reminder
import uz.focuson.ui.AppIcons
import uz.focuson.ui.AppPickerScreen
import uz.focuson.ui.C
import uz.focuson.ui.FocusTheme
import uz.focuson.ui.GardenScreen
import uz.focuson.ui.HomeScreen
import uz.focuson.ui.PermissionsScreen
import uz.focuson.ui.SettingsScreen
import uz.focuson.ui.ShopScreen
import uz.focuson.ui.TagsScreen

class FocusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifications.createChannels(this)
        Reminder.schedule(this)
        uz.focuson.focus.WeeklyAlarm.schedule(this)
        Thread { uz.focuson.data.AutoBackup.maybeRun(this) }.start()
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    FOCUS("Fokus", AppIcons.Focus),
    GARDEN("Bog'", AppIcons.Garden),
    LIMITS("Limitlar", AppIcons.Limits),
    SHOP("Do'kon", AppIcons.Shop),
    SETTINGS("Sozlama", AppIcons.Settings),
}

private sealed interface Sub {
    data object Permissions : Sub
    data object Tags : Sub
    data class Picker(val mode: BlockMode) : Sub
}

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_WEEK = "garden_week"
    }

    private fun handleOpen(intent: android.content.Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_WEEK) uz.focuson.ui.NavRequest.gardenWeek = true
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOpen(intent)
        enableEdgeToEdge()
        setContent {
            val mode by Store.settings.collectAsStateWithLifecycle()
            FocusTheme(mode.themeMode) {
                val dark = C.dark
                androidx.compose.runtime.LaunchedEffect(dark) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(transparent)
                        else androidx.activity.SystemBarStyle.light(transparent, transparent),
                        navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(transparent)
                        else androidx.activity.SystemBarStyle.light(transparent, transparent),
                    )
                }
                val context = LocalContext.current
                var tab by remember { mutableStateOf(Tab.FOCUS) }
                var sub by remember { mutableStateOf<Sub?>(null) }
                androidx.compose.runtime.LaunchedEffect(uz.focuson.ui.NavRequest.gardenWeek) {
                    if (uz.focuson.ui.NavRequest.gardenWeek) { tab = Tab.GARDEN; sub = null }
                }
                var tick by remember { mutableIntStateOf(0) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
                val settings by Store.settings.collectAsStateWithLifecycle()
                val needsSetup = remember(tick, settings.blockingEnabled, settings.strict) {
                    (settings.blockingEnabled || settings.strict) && !Blocking.isServiceEnabled(context)
                }

                Box(Modifier.fillMaxSize().background(C.background).systemBarsPadding()) {
                    when (val s = sub) {
                        Sub.Permissions -> PermissionsScreen(onBack = { sub = null })
                        Sub.Tags -> TagsScreen(onBack = { sub = null })
                        is Sub.Picker -> AppPickerScreen(s.mode, onBack = { sub = null })
                        null -> Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                when (tab) {
                                    Tab.FOCUS -> HomeScreen(
                                        needsSetup = needsSetup,
                                        onOpenPermissions = { sub = Sub.Permissions },
                                        onOpenShop = { tab = Tab.SHOP },
                                    )
                                    Tab.GARDEN -> GardenScreen()
                                    Tab.LIMITS -> uz.focuson.ui.LimitsScreen(onOpenPermissions = { sub = Sub.Permissions })
                                    Tab.SHOP -> ShopScreen()
                                    Tab.SETTINGS -> SettingsScreen(
                                        onPickApps = { sub = Sub.Picker(it) },
                                        onPermissions = { sub = Sub.Permissions },
                                        onTags = { sub = Sub.Tags },
                                    )
                                }
                            }
                            if (tab != Tab.FOCUS) BackHandler { tab = Tab.FOCUS }
                            NavigationBar(containerColor = C.card, tonalElevation = 0.dp) {
                                Tab.entries.forEach { t ->
                                    NavigationBarItem(
                                        selected = t == tab,
                                        onClick = { tab = t },
                                        icon = { Icon(t.icon, t.title) },
                                        label = { Text(t.title) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = C.primaryDark,
                                            selectedTextColor = C.primaryDark,
                                            indicatorColor = C.selected,
                                            unselectedIconColor = C.muted,
                                            unselectedTextColor = C.muted,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back cancels the strict-mode warning; a finished session is closed here too.
        FocusController.clearWarning(this)
        if (Store.active.value != null) runCatching { FocusService.start(this) }
        uz.focuson.widget.FocusWidget.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        // Without the accessibility service, strict mode still notices leaving the app.
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        if (!isChangingConfigurations && screenOn && !Blocking.isServiceEnabled(this)) {
            FocusController.leave(this)
        }
    }
}
