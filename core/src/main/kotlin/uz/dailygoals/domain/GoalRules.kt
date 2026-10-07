package uz.dailygoals.domain

object GoalRules {
    fun cleanName(raw: String): String {
        val name = raw.trim()
        ensure(name.isNotEmpty(), ErrorCode.EMPTY_NAME)
        ensure(name.codePointCount(0, name.length) <= 50, ErrorCode.NAME_TOO_LONG)
        return name
    }
    /** End date is INCLUSIVE. A newly created goal never applies on its creation day. */
    fun trackingStart(configured: Long, today: Long): Long = maxOf(configured, today + 1)
    fun normalizedEnd(start: Long, end: Long, earliestTracking: Long): Long = maxOf(end, start + 1, earliestTracking)
    fun newPeriod(goalId: String, name: String, start: Long, end: Long, order: Int, now: Long): GoalPeriod {
        val effective = trackingStart(start, dayOf(now))
        ensure(start < end && end >= effective, ErrorCode.INVALID_DATES)
        return GoalPeriod(newId(), goalId, cleanName(name), start, effective, end,
            periodOrder = order, createdAt = now)
    }
    fun edit(p: GoalPeriod, name: String, start: Long, end: Long, maxRecorded: Long?, now: Long): GoalPeriod {
        ensure(p.status == Status.ACTIVE, ErrorCode.GOAL_ARCHIVED)
        val cleaned = cleanName(name)
        ensure(maxRecorded == null || start == p.start, ErrorCode.START_LOCKED)
        // Changing a not-yet-recorded start never retroactively adds tracking days.
        val effective = if (start == p.start) p.trackingStart else trackingStart(start, dayOf(now))
        ensure(start < end && end >= effective, ErrorCode.INVALID_DATES)
        ensure(maxRecorded == null || end >= maxRecorded, ErrorCode.HISTORY_LOCKED)
        return p.copy(name = cleaned, start = start, trackingStart = effective, end = end, trackingEnd = end)
    }
    fun manualArchive(p: GoalPeriod, now: Long): GoalPeriod {
        ensure(p.status == Status.ACTIVE, ErrorCode.GOAL_ARCHIVED)
        return p.copy(status = Status.ARCHIVED, archivedAt = now, archiveReason = ArchiveReason.MANUAL,
            trackingEnd = minOf(p.end, dayOf(now) - 1))
    }
    fun canAutoArchive(p: GoalPeriod, closed: Set<Long>, today: Long): Boolean =
        p.status == Status.ACTIVE && p.end < today && p.end in closed
    fun autoArchive(p: GoalPeriod, now: Long) = p.copy(status = Status.ARCHIVED, archivedAt = now,
        archiveReason = ArchiveReason.EXPIRED)
    fun reactivate(last: GoalPeriod, name: String, end: Long, now: Long): GoalPeriod {
        ensure(last.status == Status.ARCHIVED, ErrorCode.INVALID_DATES)
        return newPeriod(last.goalId, name, dayOf(now) + 1, end, last.periodOrder + 1, now)
    }
    fun nameForDate(p: GoalPeriod, date: Long, revisions: List<NameRevision>): String =
        revisions.filter { it.periodId == p.id && it.from <= date }.maxByOrNull { it.from }?.name ?: p.name
}
