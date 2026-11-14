package uz.focuson.data

import java.time.LocalDate
import java.time.ZoneId

/** Garden looks. Each one opens after that many days in a row with at least one grown plant. */
enum class GardenTheme(val title: String, val days: Int, val about: String) {
    MEADOW("Yashil maysa", 0, "Oddiy, sokin yashil orolcha."),
    FLOWERS("Gulzor", 3, "Tosh yo'lak, oq panjara va gullar."),
    LAKE("Ko'l bo'yi", 7, "Nilufarli ko'l, qamishlar va qumli qirg'oq."),
    MOUNTAIN("Tog' etagi", 15, "Qoyali orol, xarsangtoshlar va sharshara."),
    ZEN("Yapon bog'i", 30, "Taralgan qum, tosh fonar va qizil ko'prik."),
    NIGHT("Oydin tun", 90, "Yulduzli osmon, fonarlar va yaltiroq qurtlar."),
    SKY("Uchar orol", 180, "Bulutlar ustida suzib yurgan orol."),
    GOLDEN("Oltin vodiy", 365, "Shom shafag'i, daryo va tog'lar."),
    ;

    companion object {
        fun unlocked(best: Int): List<GardenTheme> = entries.filter { it.days <= best }
        fun byName(name: String): GardenTheme? = entries.firstOrNull { it.name == name }
    }
}

/** Days in a row with at least one grown plant. Plain date math, testable. */
object Streaks {
    fun days(records: List<SessionRecord>, zone: ZoneId): Set<LocalDate> =
        records.filter { it.success }.map { Periods.dateOf(it.startAt, zone) }.toSet()

    /** The run that ends today, or yesterday if nothing is grown yet today (it can still continue). */
    fun current(days: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun best(days: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days.sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }
}
