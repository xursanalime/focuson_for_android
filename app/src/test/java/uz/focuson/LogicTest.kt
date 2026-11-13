package uz.focuson

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.focuson.limits.UsageEv as E
import uz.focuson.limits.UsageEv.Kind as K
import uz.focuson.block.BlockRules
import uz.focuson.block.Verdict
import uz.focuson.data.BlockMode
import uz.focuson.ui.DialMath
import uz.focuson.ui.formatClock
import uz.focuson.ui.formatDuration

class LogicTest {
    private val neutral = setOf("uz.focuson", "com.android.systemui", "com.samsung.android.honeyboard")
    private val launchers = setOf("com.sec.android.app.launcher")

    private fun v(pkg: String, mode: BlockMode, launchable: Boolean = true, enabled: Boolean = true) = BlockRules.verdict(
        pkg, enabled, mode,
        allowed = setOf("com.ielts"),
        blocked = setOf("org.telegram.messenger"),
        neutral = neutral, launchers = launchers,
        isLaunchable = launchable,
    )

    @Test fun allowlistBlocksEverythingElse() {
        assertEquals(Verdict.BLOCK, v("org.telegram.messenger", BlockMode.ALLOWLIST))
        assertEquals(Verdict.BLOCK, v("com.pinterest", BlockMode.ALLOWLIST))
        assertEquals(Verdict.ALLOWED, v("com.ielts", BlockMode.ALLOWLIST))
    }

    @Test fun systemAndOverlaysAreNeutral() {
        assertEquals(Verdict.NEUTRAL, v("uz.focuson", BlockMode.ALLOWLIST))
        assertEquals(Verdict.NEUTRAL, v("com.samsung.android.honeyboard", BlockMode.ALLOWLIST))
        assertEquals(Verdict.NEUTRAL, v("com.samsung.overlay", BlockMode.ALLOWLIST, launchable = false))
    }

    @Test fun homeScreenCountsAsLeaving() {
        assertEquals(Verdict.LEFT, v("com.sec.android.app.launcher", BlockMode.ALLOWLIST))
        assertEquals(Verdict.LEFT, v("com.sec.android.app.launcher", BlockMode.BLOCKLIST, enabled = false))
    }

    @Test fun blocklistBlocksOnlyChosen() {
        assertEquals(Verdict.BLOCK, v("org.telegram.messenger", BlockMode.BLOCKLIST))
        assertEquals(Verdict.ALLOWED, v("com.pinterest", BlockMode.BLOCKLIST))
    }

    @Test fun blockingOffAllowsApps() {
        assertEquals(Verdict.ALLOWED, v("org.telegram.messenger", BlockMode.ALLOWLIST, enabled = false))
    }

    @Test fun dialRoundsToFiveAndClamps() {
        assertEquals(30, DialMath.minutesFor(90f, 25))   // quarter turn = 30 min
        assertEquals(60, DialMath.minutesFor(180f, 60))
        assertEquals(10, DialMath.minutesFor(5f, 15))    // never below 10
        assertEquals(120, DialMath.minutesFor(359f, 115))
    }

    @Test fun dialDoesNotJumpAcrossTop() {
        assertEquals(120, DialMath.minutesFor(10f, 120)) // past the top from 120 stays 120
        assertEquals(10, DialMath.minutesFor(350f, 10))  // back past the top from 10 stays 10
    }

    @Test fun formats() {
        assertEquals("25:00", formatClock(25 * 60_000L))
        assertEquals("1:05:00", formatClock(65 * 60_000L))
        assertEquals("00:01", formatClock(400))
        assertEquals("25 daqiqa", formatDuration(25 * 60_000L))
        assertEquals("1 soat 15 daqiqa", formatDuration(75 * 60_000L))
        assertEquals("2 soat", formatDuration(120 * 60_000L))
    }

    @Test fun periods() {
        val d = java.time.LocalDate.of(2026, 11, 13) // Friday
        val w = uz.focuson.data.Periods.range(uz.focuson.data.Period.WEEK, d)
        assertEquals(java.time.LocalDate.of(2026, 11, 9), w.start)
        assertEquals("9–15 noyabr", uz.focuson.data.Periods.label(uz.focuson.data.Period.WEEK, w, d))
        val m = uz.focuson.data.Periods.range(uz.focuson.data.Period.MONTH, d)
        assertEquals(30, uz.focuson.data.Periods.buckets(uz.focuson.data.Period.MONTH, m, emptyList(), java.time.ZoneOffset.UTC).size)
    }

    @Test fun hourBucketsSplitAcrossHours() {
        val zone = java.time.ZoneOffset.UTC
        val day = java.time.LocalDate.of(2026, 11, 13)
        val start = day.atTime(9, 40).toInstant(zone).toEpochMilli()
        val rec = uz.focuson.data.SessionRecord(start, start + 40 * 60_000L, 40 * 60_000L, "eman", "oqish", true)
        val b = uz.focuson.data.Periods.buckets(uz.focuson.data.Period.DAY, uz.focuson.data.Periods.range(uz.focuson.data.Period.DAY, day), listOf(rec), zone)
        assertEquals(20 * 60_000L, b[9].second)
        assertEquals(20 * 60_000L, b[10].second)
    }

    @Test fun soundIsAudibleAndBounded() {
        uz.focuson.data.Sound.entries.filter { it != uz.focuson.data.Sound.NONE }.forEach { snd ->
            val g = uz.focuson.focus.AmbientPlayer.Generator(snd, 22050)
            val samples = DoubleArray(22050 * 3) { g.next() }
            val tail = samples.drop(22050 * 2)
            val rms = kotlin.math.sqrt(tail.sumOf { it * it } / tail.size)
            org.junit.Assert.assertTrue("$snd too quiet: $rms", rms > 0.01)
            org.junit.Assert.assertTrue("$snd clipping", samples.all { it in -1.0..1.0 })
        }
    }

    @Test fun gardenGrowsWithPeriod() {
        val g = uz.focuson.ui.GardenSize
        val p = uz.focuson.data.Period.entries
        // with no plants: day < week < month < year
        val empty = p.map { g.grid(it, 0) }
        assertEquals(empty.sorted(), empty)
        assertEquals(4, empty.distinct().size)
        val widths = p.map { g.widthFraction(it) }
        assertEquals(widths.sorted(), widths)
        // more plants than squares: the island grows, but not without limit
        assertEquals(5, g.grid(uz.focuson.data.Period.DAY, 25))
        assertEquals(8, g.grid(uz.focuson.data.Period.DAY, 500))
    }

    @Test fun streaks() {
        val d = java.time.LocalDate.of(2026, 11, 13)
        val S = uz.focuson.data.Streaks
        val days = setOf(d, d.minusDays(1), d.minusDays(2), d.minusDays(5), d.minusDays(6))
        assertEquals(3, S.current(days, d))
        assertEquals(3, S.best(days))
        // nothing grown yet today: yesterday's run still counts
        assertEquals(2, S.current(setOf(d.minusDays(1), d.minusDays(2)), d))
        assertEquals(0, S.current(setOf(d.minusDays(3)), d))
        val open = uz.focuson.data.GardenTheme.unlocked(7).map { it.days }
        assertEquals(listOf(0, 3, 7), open)
    }

    @Test fun everyThemeLeavesRoomForPlants() {
        uz.focuson.data.GardenTheme.entries.forEach { t ->
            uz.focuson.data.Period.entries.forEach { p ->
                val k = uz.focuson.ui.GardenLayout.grid(t, p, 0)
                val free = k * k - uz.focuson.ui.GardenLayout.blocked(t, k).size
                org.junit.Assert.assertTrue("$t $p free=$free", free >= k * k / 2)
            }
        }
    }

    @Test fun usageMathCountsForegroundTime() {
        val m = 60_000L
        val ev = listOf(
            E("insta", K.START, 0, "Feed"), E("insta", K.STOP, 10 * m, "Feed"),     // 10 min
            // chess stays on screen while a game overlay of another app opens on top of it
            E("chess", K.START, 12 * m, "Game"), E("overlay", K.START, 15 * m, "Panel"),
            E("overlay", K.STOP, 16 * m, "Panel"), E("chess", K.STOP, 32 * m, "Game"), // chess 20, overlay 1
            // two activities of one app overlap: counted once
            E("yt", K.START, 40 * m, "Main"), E("yt", K.START, 45 * m, "Player"),
            E("yt", K.STOP, 46 * m, "Main"), E("yt", K.STOP, 50 * m, "Player"),        // yt 10
            E("tg", K.START, 52 * m, "Chat"), E("tg", K.SCREEN_OFF, 55 * m),          // tg 3
            E("insta", K.START, 58 * m, "Feed"),                                       // still open: until now
        )
        val r = uz.focuson.limits.UsageMath.foregroundMs(ev, 60 * m)
        assertEquals(12 * m, r["insta"])
        assertEquals(20 * m, r["chess"])
        assertEquals(1 * m, r["overlay"])
        assertEquals(10 * m, r["yt"])
        assertEquals(3 * m, r["tg"])
    }

    @Test fun limitRules() {
        val L = uz.focuson.limits.LimitRules
        val Open = { left: Long, g: Boolean -> uz.focuson.limits.LimitRules.State.Open(left, g) }
        val Over = uz.focuson.limits.LimitRules.State.Over
        val m = 60_000L
        assertEquals(Open(20 * m, false), L.state(40 * m, 60 * m, 0, 1000))
        assertEquals(Over, L.state(60 * m, 60 * m, 0, 1000))
        // a 5-minute pass wins over the used-up limit
        assertEquals(Open(3 * m, true), L.state(70 * m, 60 * m, 1000 + 3 * m, 1000))
        // an expired pass does not
        assertEquals(Over, L.state(70 * m, 60 * m, 500, 1000))
    }

    @Test fun limitChangesThatLoosenWaitForTomorrow() {
        val L = uz.focuson.data.LimitChange
        assertEquals(true, L.appliesNow(null, 60))   // new limit: now
        assertEquals(true, L.appliesNow(60, 30))     // stricter: now
        assertEquals(true, L.appliesNow(60, 60))
        assertEquals(false, L.appliesNow(60, 90))    // looser: tomorrow
        assertEquals(false, L.appliesNow(60, null))  // remove: tomorrow
    }

    @Test fun weeklyReport() {
        val zone = java.time.ZoneOffset.UTC
        val mon = java.time.LocalDate.of(2026, 11, 9)
        fun at(d: java.time.LocalDate, h: Int) = d.atTime(h, 0).toInstant(zone).toEpochMilli()
        val m = 60_000L
        val recs = listOf(
            uz.focuson.data.SessionRecord(at(mon, 9), at(mon, 9) + 30 * m, 30 * m, "eman", "ielts", true),
            uz.focuson.data.SessionRecord(at(mon.plusDays(3), 9), at(mon.plusDays(3), 9) + 90 * m, 90 * m, "eman", "ielts", true),
            uz.focuson.data.SessionRecord(at(mon.plusDays(3), 14), at(mon.plusDays(3), 14) + 5 * m, 25 * m, "eman", "ish", false),
            // last week: 60 min
            uz.focuson.data.SessionRecord(at(mon.minusDays(3), 9), at(mon.minusDays(3), 9) + 60 * m, 60 * m, "eman", "ish", true),
        )
        val r = uz.focuson.data.WeeklyReport.build(recs, mon.plusDays(6), zone)
        assertEquals(120 * m, r.focusedMs)
        assertEquals(2, r.grown)
        assertEquals(1, r.withered)
        assertEquals("ielts", r.topTagId)
        assertEquals(mon.plusDays(3), r.bestDay)
        assertEquals(2, r.activeDays)
        assertEquals(100, r.changePercent)
    }

    @Test fun weeklyAlarmIsNextSundayEvening() {
        val W = uz.focuson.focus.WeeklyAlarm
        val fri = java.time.LocalDateTime.of(2026, 11, 13, 21, 0)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 15, 20, 0), W.next(fri))
        val sunLate = java.time.LocalDateTime.of(2026, 11, 15, 20, 30)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 22, 20, 0), W.next(sunLate))
    }
}
