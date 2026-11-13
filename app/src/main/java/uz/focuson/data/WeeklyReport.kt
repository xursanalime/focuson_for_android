package uz.focuson.data

import java.time.LocalDate
import java.time.ZoneId

/** A week in numbers, compared with the week before. Plain math, testable. */
data class WeeklyReport(
    val range: DateRange,
    val focusedMs: Long,
    val prevFocusedMs: Long,
    val grown: Int,
    val withered: Int,
    val topTagId: String?,
    val topTagMs: Long,
    val bestDay: LocalDate?,
    val bestDayMs: Long,
    val activeDays: Int,
) {
    /** Change against last week in percent, or null when last week was empty. */
    val changePercent: Int?
        get() = if (prevFocusedMs <= 0) null else (((focusedMs - prevFocusedMs) * 100) / prevFocusedMs).toInt()

    companion object {
        fun build(history: List<SessionRecord>, anyDayOfWeek: LocalDate, zone: ZoneId): WeeklyReport {
            val week = Periods.range(Period.WEEK, anyDayOfWeek)
            val prev = Periods.range(Period.WEEK, anyDayOfWeek.minusWeeks(1))
            val rec = Periods.inRange(history, week, zone)
            val ok = rec.filter { it.success }
            val prevMs = Periods.inRange(history, prev, zone).filter { it.success }.sumOf { it.focusedMs }
            val byTag = ok.groupBy { it.tagId }.mapValues { (_, l) -> l.sumOf { it.focusedMs } }
            val top = byTag.maxByOrNull { it.value }
            val byDay = ok.groupBy { Periods.dateOf(it.startAt, zone) }.mapValues { (_, l) -> l.sumOf { it.focusedMs } }
            val best = byDay.maxByOrNull { it.value }
            return WeeklyReport(
                range = week,
                focusedMs = ok.sumOf { it.focusedMs },
                prevFocusedMs = prevMs,
                grown = ok.size,
                withered = rec.size - ok.size,
                topTagId = top?.key,
                topTagMs = top?.value ?: 0L,
                bestDay = best?.key,
                bestDayMs = best?.value ?: 0L,
                activeDays = byDay.size,
            )
        }
    }
}
