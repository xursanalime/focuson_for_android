package uz.focuson.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class Period(val title: String) { DAY("Kun"), WEEK("Hafta"), MONTH("Oy"), YEAR("Yil") }

/** [start] inclusive, [end] exclusive. */
data class DateRange(val start: LocalDate, val end: LocalDate)

/** Plain calendar math for the garden and statistics. No Android here, so it is testable. */
object Periods {
    val months = listOf(
        "yanvar", "fevral", "mart", "aprel", "may", "iyun",
        "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr",
    )
    val weekdays = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")

    fun range(p: Period, anchor: LocalDate): DateRange = when (p) {
        Period.DAY -> DateRange(anchor, anchor.plusDays(1))
        Period.WEEK -> {
            val mon = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            DateRange(mon, mon.plusDays(7))
        }
        Period.MONTH -> {
            val first = anchor.withDayOfMonth(1)
            DateRange(first, first.plusMonths(1))
        }
        Period.YEAR -> {
            val first = anchor.withDayOfYear(1)
            DateRange(first, first.plusYears(1))
        }
    }

    fun shift(p: Period, anchor: LocalDate, n: Long): LocalDate = when (p) {
        Period.DAY -> anchor.plusDays(n)
        Period.WEEK -> anchor.plusWeeks(n)
        Period.MONTH -> anchor.plusMonths(n)
        Period.YEAR -> anchor.plusYears(n)
    }

    fun label(p: Period, r: DateRange, today: LocalDate): String = when (p) {
        Period.DAY -> when (r.start) {
            today -> "Bugun"
            today.minusDays(1) -> "Kecha"
            else -> "${r.start.dayOfMonth}-${months[r.start.monthValue - 1]}, ${r.start.year}"
        }
        Period.WEEK -> {
            val last = r.end.minusDays(1)
            if (r.start.month == last.month) "${r.start.dayOfMonth}–${last.dayOfMonth} ${months[last.monthValue - 1]}"
            else "${r.start.dayOfMonth} ${months[r.start.monthValue - 1]} – ${last.dayOfMonth} ${months[last.monthValue - 1]}"
        }
        Period.MONTH -> "${months[r.start.monthValue - 1].replaceFirstChar { it.uppercase() }} ${r.start.year}"
        Period.YEAR -> "${r.start.year}"
    }

    fun dateOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun inRange(records: List<SessionRecord>, r: DateRange, zone: ZoneId): List<SessionRecord> =
        records.filter {
            val d = dateOf(it.startAt, zone)
            !d.isBefore(r.start) && d.isBefore(r.end)
        }.sortedBy { it.startAt }

    /** Focused time of grown plants per bar: hours of a day, days of a week/month, months of a year. */
    fun buckets(p: Period, r: DateRange, records: List<SessionRecord>, zone: ZoneId): List<Pair<String, Long>> {
        val ok = records.filter { it.success }
        return when (p) {
            Period.DAY -> {
                val out = LongArray(24)
                val dayStart = r.start.atStartOfDay(zone).toInstant().toEpochMilli()
                ok.forEach { rec ->
                    var t = rec.startAt
                    while (t < rec.endAt) {
                        val hour = ((t - dayStart) / 3_600_000L).toInt()
                        val hourEnd = dayStart + (hour + 1) * 3_600_000L
                        val e = minOf(rec.endAt, hourEnd)
                        if (hour in 0..23) out[hour] += e - t
                        t = e
                    }
                }
                out.mapIndexed { i, v -> (if (i % 6 == 0) "$i" else "") to v }
            }
            Period.WEEK -> (0 until 7).map { i ->
                val d = r.start.plusDays(i.toLong())
                weekdays[i] to ok.filter { dateOf(it.startAt, zone) == d }.sumOf { it.focusedMs }
            }
            Period.MONTH -> {
                val n = r.start.lengthOfMonth()
                (1..n).map { day ->
                    val d = r.start.withDayOfMonth(day)
                    (if (day == 1 || day % 5 == 0) "$day" else "") to
                        ok.filter { dateOf(it.startAt, zone) == d }.sumOf { it.focusedMs }
                }
            }
            Period.YEAR -> (1..12).map { m ->
                months[m - 1].take(3).replaceFirstChar { it.uppercase() } to
                    ok.filter { dateOf(it.startAt, zone).monthValue == m }.sumOf { it.focusedMs }
            }
        }
    }
}
