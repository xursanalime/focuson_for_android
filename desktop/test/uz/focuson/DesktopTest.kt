package uz.focuson

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import uz.focuson.data.Backup
import uz.focuson.data.SessionRecord
import uz.focuson.data.Store
import uz.focuson.desktop.DesktopFocus
import uz.focuson.desktop.DesktopSettings
import uz.focuson.ui.C
import uz.focuson.ui.FocusTheme
import uz.focuson.ui.GardenScreen
import uz.focuson.ui.HomeScreen
import uz.focuson.ui.LocalFocusActions
import java.io.File
import java.nio.file.Files

class DesktopTest {
    companion object {
        init {
            // never touch the real data in ~/.local/share/focuson
            System.setProperty("focuson.dataDir", Files.createTempDirectory("focuson-test").toString())
        }
    }

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val reg = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply {
            currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = reg
    }

    private fun shot(name: String, w: Int = 460, h: Int = 860, mode: String = "light", content: @Composable () -> Unit) {
        val scene = ImageComposeScene(w * 2, h * 2, Density(2f)) {
            FocusTheme(mode) {
                CompositionLocalProvider(
                    LocalFocusActions provides DesktopFocus,
                    androidx.lifecycle.compose.LocalLifecycleOwner provides owner,
                ) {
                    Box(Modifier.fillMaxSize().background(C.background)) { content() }
                }
            }
        }
        scene.render(0)
        val img = scene.render(500_000_000)
        File("build/screens").mkdirs()
        File("build/screens/$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    private fun rec(i: Int, ok: Boolean = true) = SessionRecord(
        System.currentTimeMillis() - (i + 1) * 3_000_000L, System.currentTimeMillis() - (i + 1) * 3_000_000L + 1_500_000L,
        1_500_000L, listOf("eman", "olcha", "archa", "kaktus", "lola")[i % 5], listOf("oqish", "ielts", "ish")[i % 3], ok,
    )

    @Test fun screens() {
        (0 until 9).forEach { Store.addRecord(rec(it, it != 4)) }
        shot("home") { HomeScreen(needsSetup = false, onOpenPermissions = {}, onOpenShop = {}) }
        shot("garden") { GardenScreen() }
        shot("settings") { DesktopSettings(onTags = {}) }
        shot("home_dark", mode = "dark") { HomeScreen(needsSetup = false, onOpenPermissions = {}, onOpenShop = {}) }
    }

    @Test fun mergeAddsOnlyNewSessions() {
        val a = listOf(rec(1), rec(2))
        val b = listOf(rec(2), rec(3, ok = false))
        val merged = Backup.mergeHistory(a, b)
        assertEquals(3, merged.size)
        assertEquals(1, Backup.countNew(a, b))
        assertEquals(merged.sortedBy { it.startAt }, merged)
    }

    @Test fun phoneBackupMergesIntoComputer() {
        val phone = Backup.write(listOf(rec(20), rec(21)), emptyList(), 999, setOf("palma"), "android")
        val coins = Store.coins.value
        val added = Store.importJson(phone, merge = true)
        assertEquals(2, added)
        assertEquals(0, Store.importJson(phone, merge = true))  // twice: nothing new
        assertEquals(coins, Store.coins.value)                   // coins stay per device
        assertEquals(true, "palma" in Store.owned.value)          // bought plants come along
    }
}
