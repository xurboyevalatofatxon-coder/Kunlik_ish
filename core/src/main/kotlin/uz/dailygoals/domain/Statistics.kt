package uz.dailygoals.domain

import java.time.*
import java.time.temporal.TemporalAdjusters

object Statistics {
    fun percentage(done: Long, notDone: Long): Double? {
        require(done >= 0 && notDone >= 0)
        return if (done + notDone == 0L) null else done.toDouble() * 100.0 / (done + notDone)
    }
    /** Equal weight PER LOGICAL GOAL, NOT pooled results and NOT weight by duration. */
    fun overall(goals: List<GoalCounts>): Double? = goals.mapNotNull { it.percentage }.let { if (it.isEmpty()) null else it.average() }
    fun summarize(goals: List<GoalCounts>, days: List<DayCounts>, pending: Long): StatisticsResult {
        val validDays = days.filter { it.done + it.notDone > 0 }.sortedBy { it.date }
        return StatisticsResult(goals.sortedWith(compareByDescending<GoalCounts> { it.percentage ?: -1.0 }.thenBy { it.name }),
            validDays, pending, overall(goals), validDays.count { it.complete }, validDays.count { !it.complete })
    }
    /** Used for deterministic core tests; production statistics are aggregated by SQL. */
    fun fromResults(goals: List<Goal>, periods: List<GoalPeriod>, results: List<DailyResult>, days: List<FinalizedDay>, range: DateRange): StatisticsResult {
        val closed = days.map { it.date }.toSet()
        val valid = results.filter { it.finalized && it.date in closed && it.date in range.start..range.end }
        val byPeriod = periods.associateBy { it.id }
        val counts = goals.map { g ->
            val records = valid.filter { byPeriod[it.periodId]?.goalId == g.id }
            val name = periods.filter { it.goalId == g.id }.maxByOrNull { it.periodOrder }?.name.orEmpty()
            GoalCounts(g.id, name, records.count { it.value == ResultValue.DONE }.toLong(), records.count { it.value == ResultValue.NOT_DONE }.toLong())
        }
        val dc = valid.groupBy { it.date }.map { (date, items) -> DayCounts(date,
            items.count { it.value == ResultValue.DONE }.toLong(), items.count { it.value == ResultValue.NOT_DONE }.toLong()) }
        return summarize(counts, dc, 0)
    }
    fun week(today: LocalDate): DateRange {
        val first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return DateRange(first.toEpochDay(), first.plusDays(6).toEpochDay())
    }
    fun month(today: LocalDate) = DateRange(today.withDayOfMonth(1).toEpochDay(), today.withDayOfMonth(today.lengthOfMonth()).toEpochDay())
    /** 'Bugun' is the last review COMPLETED today, not today's tracking date. */
    fun todayReview(days: List<FinalizedDay>, today: Long): Long? =
        days.filter { dayOf(it.finalizedAt) == today }.maxWithOrNull(compareBy<FinalizedDay> { it.finalizedAt }.thenBy { it.date })?.date
}
