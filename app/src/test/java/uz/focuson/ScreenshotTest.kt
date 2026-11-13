package uz.focuson

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.focuson.data.Catalog
import uz.focuson.data.BlockMode
import uz.focuson.ui.AppPickerScreen
import uz.focuson.ui.C
import uz.focuson.ui.FocusTheme
import uz.focuson.ui.HomeScreen
import uz.focuson.ui.PermissionsScreen
import uz.focuson.ui.PlantView
import uz.focuson.ui.SettingsScreen
import java.io.File

/** Renders screens to PNG files in build/screens so they can be looked at without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, mode: String = "light", content: @Composable () -> Unit) {
        rule.setContent { FocusTheme(mode) { content() } }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = shot("home") { HomeScreen(needsSetup = true, onOpenPermissions = {}, onOpenShop = {}) }

    @Test fun running() {
        uz.focuson.data.Store.setActive(
            uz.focuson.data.ActiveSession(System.currentTimeMillis() - 11 * 60_000L, 25 * 60_000L, "olcha", "ielts")
        )
        shot("running") { HomeScreen(needsSetup = false, onOpenPermissions = {}, onOpenShop = {}) }
        uz.focuson.data.Store.setActive(null)
    }

    @Test fun clearWitheredCostsCoins() {
        val st = uz.focuson.data.Store
        val now = System.currentTimeMillis()
        val dead1 = uz.focuson.data.SessionRecord(now - 900_000, now - 800_000, 600_000, "eman", "oqish", false)
        val dead2 = uz.focuson.data.SessionRecord(now - 700_000, now - 650_000, 600_000, "olma", "oqish", false)
        val alive = uz.focuson.data.SessionRecord(now - 600_000, now, 600_000, "eman", "oqish", true, coins = 10)
        listOf(dead1, dead2, alive).forEach { st.addRecord(it) }
        val coinsBefore = st.coins.value
        org.junit.Assert.assertEquals(10, coinsBefore)
        // not enough: 2 x 20 = 40 > 10
        org.junit.Assert.assertFalse(st.clearWithered(listOf(dead1, dead2)))
        org.junit.Assert.assertEquals(3, st.history.value.size)
        st.addCoins(30)
        // grown plants are never removed, even if passed in
        org.junit.Assert.assertTrue(st.clearWithered(listOf(dead1, dead2, alive)))
        org.junit.Assert.assertEquals(0, st.coins.value)
        org.junit.Assert.assertEquals(listOf(alive), st.history.value)
    }

    /** Same space the phone gives the focus tab (bottom menu and system bars taken away). */
    @Test fun homeOnPhoneHeight() = shot("home_phone") {
        androidx.compose.foundation.layout.Box(Modifier.size(width = 384.dp, height = 650.dp).background(C.bgTop)) {
            HomeScreen(needsSetup = true, onOpenPermissions = {}, onOpenShop = {})
        }
    }

    /** A session that starts a moment "in the future" (clock lag) must not crash. */
    @Test fun runningJustStarted() {
        uz.focuson.data.Store.setActive(
            uz.focuson.data.ActiveSession(System.currentTimeMillis() + 5_000L, 25 * 60_000L, "eman", "ielts")
        )
        shot("running_lag") { HomeScreen(needsSetup = false, onOpenPermissions = {}, onOpenShop = {}) }
        uz.focuson.data.Store.setActive(null)
    }

    @Test fun gardenSizes() = shot("garden_sizes") {
        val ids = Catalog.species.map { it.id }
        fun recs(n: Int) = (0 until n).map { i ->
            uz.focuson.data.SessionRecord(i * 1000L, i * 1000L + 500, 500, ids[i % ids.size], "oqish", i % 9 != 4)
        }
        Column(Modifier.background(C.bgTop)) {
            listOf(uz.focuson.data.Period.DAY to 5, uz.focuson.data.Period.WEEK to 20,
                uz.focuson.data.Period.MONTH to 60, uz.focuson.data.Period.YEAR to 150).forEach { (p, n) ->
                androidx.compose.material3.Text(p.title)
                androidx.compose.foundation.layout.Box(Modifier.size(width = 230.dp, height = 220.dp)) {
                    uz.focuson.ui.GardenBoard(recs(n), seed = 3, period = p)
                }
            }
        }
    }

    private fun themePair(n: Int) {
        val ids = Catalog.species.map { it.id }
        val recs = (0 until 28).map { i ->
            uz.focuson.data.SessionRecord(i * 1000L, i * 1000L + 500, 500, ids[(i * 7) % ids.size], "oqish", i % 11 != 4)
        }
        val pair = uz.focuson.data.GardenTheme.entries.chunked(2)[n]
        shot("theme_$n") {
            Column(Modifier.background(C.bgBottom)) {
                pair.forEach { t ->
                    androidx.compose.material3.Text(t.title)
                    uz.focuson.ui.GardenBoard(recs, seed = 5, period = uz.focuson.data.Period.MONTH, theme = t)
                }
            }
        }
    }

    @Test fun theme0() = themePair(0)
    @Test fun theme1() = themePair(1)
    @Test fun theme2() = themePair(2)
    @Test fun theme3() = themePair(3)

    @Test fun homeDark() = shot("home_dark", mode = "dark") {
        androidx.compose.foundation.layout.Box(Modifier.size(width = 384.dp, height = 650.dp).background(C.background)) {
            HomeScreen(needsSetup = true, onOpenPermissions = {}, onOpenShop = {})
        }
    }

    @Test fun settingsDark() = shot("settings_dark", mode = "dark") {
        androidx.compose.foundation.layout.Box(Modifier.background(C.background)) { SettingsScreen({}, {}, {}) }
    }

    @Test fun gardenDark() = shot("garden_dark", mode = "dark") {
        androidx.compose.foundation.layout.Box(Modifier.background(C.background)) { uz.focuson.ui.GardenScreen() }
    }

    @Test fun limits() {
        uz.focuson.data.Store.setLimit("com.example.none", 60)
        shot("limits") { androidx.compose.foundation.layout.Box(Modifier.background(C.background)) { uz.focuson.ui.LimitsScreen {} } }
        uz.focuson.data.Store.setLimit("com.example.none", null)
    }

    @Test fun passesAreCountedPerDay() {
        val st = uz.focuson.data.Store
        st.updateSettings { it.copy(graceCount = 2) }
        val A = uz.focuson.limits.AppLimits
        org.junit.Assert.assertEquals(2, A.passesLeft("x"))
        org.junit.Assert.assertTrue(A.grant("x"))
        org.junit.Assert.assertTrue(A.grant("x"))
        org.junit.Assert.assertFalse(A.grant("x"))
        org.junit.Assert.assertEquals(0, A.passesLeft("x"))
        org.junit.Assert.assertTrue(st.grant("x").until > System.currentTimeMillis())
        org.junit.Assert.assertEquals(2, A.passesLeft("y"))
    }

    @Test fun limitRaiseWaitsForTomorrow() {
        val st = uz.focuson.data.Store
        val today = java.time.LocalDate.of(2026, 11, 13)
        org.junit.Assert.assertTrue(st.requestLimit("app.a", 60, today))
        org.junit.Assert.assertFalse(st.requestLimit("app.a", 120, today))
        org.junit.Assert.assertEquals(60, st.limits.value["app.a"])
        st.applyDueLimits(today)
        org.junit.Assert.assertEquals(60, st.limits.value["app.a"])
        st.applyDueLimits(today.plusDays(1))
        org.junit.Assert.assertEquals(120, st.limits.value["app.a"])
        // removing waits too, and a stricter limit cancels the waiting change
        org.junit.Assert.assertFalse(st.requestLimit("app.a", null, today.plusDays(1)))
        org.junit.Assert.assertTrue(st.requestLimit("app.a", 30, today.plusDays(1)))
        org.junit.Assert.assertEquals(30, st.limits.value["app.a"])
        org.junit.Assert.assertNull(st.pendingLimits.value["app.a"])
        st.setLimit("app.a", null)
    }

    @Test fun settings() = shot("settings") { SettingsScreen({}, {}, {}) }

    @Test fun shop() = shot("shop") { uz.focuson.ui.ShopScreen() }

    @Test fun garden() {
        val now = System.currentTimeMillis()
        val ids = uz.focuson.data.Catalog.species.map { it.id }
        (0 until 22).forEach { i ->
            val st = now - (i + 1) * 50 * 60_000L
            uz.focuson.data.Store.addRecord(uz.focuson.data.SessionRecord(st, st + 30 * 60_000L, 30 * 60_000L, ids[i % ids.size], listOf("oqish", "ielts", "ish")[i % 3], i % 7 != 3))
        }
        shot("garden") { uz.focuson.ui.GardenScreen() }
    }

    @Test fun permissions() = shot("permissions") { PermissionsScreen {} }

    @Test fun plants() = shot("plants") {
        Column(Modifier.background(C.bgTop)) {
            Catalog.species.chunked(4).forEach { row ->
                Row {
                    row.forEach { sp -> PlantView(sp.id, 1f, modifier = Modifier.size(100.dp)) }
                }
            }
        }
    }

    @Test fun growth() = shot("growth") {
        Column(Modifier.background(C.bgTop)) {
            listOf("eman", "archa", "olma", "kungaboqar").forEach { id ->
                Row {
                    listOf(0.05f, 0.3f, 0.65f, 1f).forEach { g -> PlantView(id, g, modifier = Modifier.size(80.dp)) }
                    PlantView(id, 0.7f, withered = true, modifier = Modifier.size(80.dp))
                }
            }
        }
    }

    @Test fun big() = shot("big") {
        Column(Modifier.background(C.bgTop)) {
            listOf(listOf("eman", "olcha"), listOf("chinor", "palma"), listOf("majnuntol", "qaragay")).forEach { row ->
                Row { row.forEach { PlantView(it, 1f, modifier = Modifier.size(200.dp)) } }
            }
        }
    }
}
