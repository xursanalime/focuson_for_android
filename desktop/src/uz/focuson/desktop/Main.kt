package uz.focuson.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import uz.focuson.data.Catalog
import uz.focuson.data.Store
import uz.focuson.ui.AppIcons
import uz.focuson.ui.C
import uz.focuson.ui.FocusTheme
import uz.focuson.ui.GardenScreen
import uz.focuson.ui.HomeScreen
import uz.focuson.ui.LocalFocusActions
import uz.focuson.ui.NavRequest
import uz.focuson.ui.ShopScreen
import uz.focuson.ui.TagsScreen
import uz.focuson.ui.formatClock

private enum class Tab(val title: String, val icon: ImageVector) {
    FOCUS("Fokus", AppIcons.Focus),
    GARDEN("Bog'", AppIcons.Garden),
    SHOP("Do'kon", AppIcons.Shop),
    SETTINGS("Sozlamalar", AppIcons.Settings),
}

private fun resource(name: String): ByteArray =
    Thread.currentThread().contextClassLoader.getResourceAsStream(name)!!.use { it.readBytes() }

private fun loadIcon(name: String): ImageBitmap =
    org.jetbrains.skia.Image.makeFromEncoded(resource(name)).toComposeImageBitmap()

/** notify-send needs the icon as a file; keep it the same as the app's current icon. */
private fun writeNotifyIcon() {
    runCatching {
        val bytes = resource("icons/256.png")
        if (!Notify.iconFile.exists() || !Notify.iconFile.readBytes().contentEquals(bytes)) Notify.iconFile.writeBytes(bytes)
    }
}

fun main(args: Array<String>) = application {
    val settings by Store.settings.collectAsState()
    val active by Store.active.collectAsState()
    // "--tray" (used when the computer starts): stay in the panel until opened.
    var visible by remember { mutableStateOf(!(isTraySupported && "--tray" in args)) }
    val icon = remember { writeNotifyIcon(); BitmapPainter(loadIcon("icons/256.png")) }
    // the panel icon is tiny: the simple sprout version stays readable
    val trayIcon = remember { BitmapPainter(loadIcon("icons/32.png")) }
    LaunchedEffect(Unit) { DesktopFocus.startLoop() }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    if (isTraySupported) {
        val s = active
        Tray(
            icon = trayIcon,
            tooltip = if (s == null) "Focus On" else
                "${Catalog.byId(s.speciesId).name} o'smoqda · " +
                    (if (s.stopwatch) formatClock(now - s.startAt) else "${formatClock(s.endAt - now)} qoldi"),
            onAction = { visible = true },
            menu = {
                Item("Focus On'ni ochish", onClick = { visible = true })
                Item("Chiqish", onClick = ::exitApplication)
            },
        )
    }

    val state = rememberWindowState(size = DpSize(460.dp, 920.dp), position = WindowPosition(Alignment.Center))
    Window(
        onCloseRequest = { if (isTraySupported) visible = false else exitApplication() },
        visible = visible,
        title = "Focus On",
        icon = icon,
        state = state,
    ) {
        FocusTheme(settings.themeMode) {
            CompositionLocalProvider(LocalFocusActions provides DesktopFocus) {
                DesktopApp()
            }
        }
    }
}

@Composable
private fun DesktopApp() {
    var tab by remember { mutableStateOf(Tab.FOCUS) }
    var tags by remember { mutableStateOf(false) }
    LaunchedEffect(NavRequest.gardenWeek) { if (NavRequest.gardenWeek) tab = Tab.GARDEN }

    Box(Modifier.fillMaxSize().background(C.background), contentAlignment = Alignment.TopCenter) {
        // On a wide window the app stays phone-shaped in the middle, like on the phone.
        Column(Modifier.widthIn(max = 560.dp).fillMaxHeight()) {
            Box(Modifier.weight(1f)) {
                if (tags) TagsScreen(onBack = { tags = false })
                else when (tab) {
                    Tab.FOCUS -> HomeScreen(needsSetup = false, onOpenPermissions = {}, onOpenShop = { tab = Tab.SHOP }, showBlocking = false)
                    Tab.GARDEN -> GardenScreen()
                    Tab.SHOP -> ShopScreen()
                    Tab.SETTINGS -> DesktopSettings(onTags = { tags = true })
                }
            }
            NavigationBar(containerColor = C.card, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab && !tags,
                        onClick = { tab = t; tags = false },
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
