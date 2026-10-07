package uz.dailygoals.domain

/** Pure deterministic business rules. No database, UI, or device clock dependency. */
object ReviewEngine {
    /** Merge overlapping active intervals before counting; gap days do not become pending. */
    fun intervals(periods: List<GoalPeriod>, from: Long = Long.MIN_VALUE, until: Long): List<LongRange> {
        val input = periods.mapNotNull {
            val lo = maxOf(it.trackingStart, from)
            val hi = minOf(it.trackingEnd, until)
            if (lo <= hi) lo..hi else null
        }.sortedBy { it.first }
        val merged = mutableListOf<LongRange>()
        for (range in input) {
            val previous = merged.lastOrNull()
            if (previous != null && range.first <= previous.last + 1) {
                merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
            } else merged += range
        }
        return merged
    }
    fun pending(periods: List<GoalPeriod>, closed: Set<Long>, today: Long, range: DateRange? = null): PendingSummary {
        val ranges = intervals(periods, range?.start ?: Long.MIN_VALUE, minOf(today - 1, range?.end ?: Long.MAX_VALUE))
        var oldest: Long? = null
        var count = 0L
        for (r in ranges) {
            count += r.last - r.first + 1 - closed.count { it in r }
            if (oldest == null) {
                var candidate = r.first
                while (candidate <= r.last && candidate in closed) candidate++
                if (candidate <= r.last) oldest = candidate
            }
        }
        return PendingSummary(oldest, count)
    }
    /** The repository MUST execute this read/modify/finalize operation in one transaction. */
    fun select(
        date: Long, periodId: String, value: ResultValue, now: Long,
        periods: List<GoalPeriod>, closed: Set<Long>, drafts: List<DailyResult>,
        revisions: List<NameRevision>
    ): ReviewChange {
        ensure(date !in closed, ErrorCode.HISTORY_LOCKED)
        val oldest = pending(periods, closed, dayOf(now)).oldest ?: throw DomainException(ErrorCode.NO_PENDING)
        ensure(date == oldest, ErrorCode.WRONG_DAY)
        val applicable = periods.filter { it.applies(date) }
        val period = applicable.find { it.id == periodId } ?: throw DomainException(ErrorCode.NOT_APPLICABLE)
        ensure(drafts.all { it.date == date && !it.finalized }, ErrorCode.HISTORY_LOCKED)
        val prior = drafts.find { it.periodId == periodId }
        val result = DailyResult(prior?.id ?: newId(), periodId, date, value,
            prior?.goalNameSnapshot ?: GoalRules.nameForDate(period, date, revisions), now, false)
        val updated = drafts.filter { it.periodId != periodId } + result
        val complete = applicable.all { p -> updated.any { it.periodId == p.id } }
        return ReviewChange(updated.map { it.copy(finalized = complete) }, complete)
    }
    fun readyToFinalize(date: Long, periods: List<GoalPeriod>, drafts: List<DailyResult>): Boolean {
        val applicable = periods.filter { it.applies(date) }
        return applicable.isNotEmpty() && applicable.all { p -> drafts.any { it.periodId == p.id && it.date == date } }
    }
}
