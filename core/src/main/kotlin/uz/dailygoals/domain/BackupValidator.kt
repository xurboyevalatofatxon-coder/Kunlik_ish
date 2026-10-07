package uz.dailygoals.domain

import java.time.LocalDate
import java.util.UUID

object BackupValidator {
    private fun bad(ok: Boolean) = ensure(ok, ErrorCode.INVALID_BACKUP)
    private fun id(value: String) { bad(runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)) }
    private fun date(value: Long) { bad(runCatching { LocalDate.ofEpochDay(value).year in 1900..9999 }.getOrDefault(false)) }
    private fun named(value: String) { bad(value.isNotBlank() && value.codePointCount(0, value.length) <= 50) }
    fun validate(s: Snapshot, today: Long) {
        ensure(s.schemaVersion == 1, ErrorCode.UNSUPPORTED_SCHEMA)
        bad(s.exportedAt >= 0 && s.goals.size <= 10_000 && s.periods.size <= 50_000 && s.results.size <= 500_000)
        bad(s.goals.map { it.id }.distinct().size == s.goals.size)
        bad(s.periods.map { it.id }.distinct().size == s.periods.size)
        bad(s.results.map { it.id }.distinct().size == s.results.size)
        bad(s.revisions.map { it.id }.distinct().size == s.revisions.size)
        bad(s.goals.map { it.insertionOrder }.distinct().size == s.goals.size)
        bad(s.days.map { it.date }.distinct().size == s.days.size)
        bad(s.results.map { it.periodId to it.date }.distinct().size == s.results.size)
        bad(s.revisions.map { it.periodId to it.from }.distinct().size == s.revisions.size)
        val goals = s.goals.associateBy { it.id }; val periods = s.periods.associateBy { it.id }
        val days = s.days.associateBy { it.date }
        val periodsByGoal = s.periods.groupBy { it.goalId }
        val namesByPeriod = s.revisions.groupBy { it.periodId }
        s.goals.forEach { g ->
            id(g.id); bad(g.rootId == g.id && g.insertionOrder >= 0 && g.createdAt >= 0)
            val own = periodsByGoal[g.id].orEmpty().sortedBy { it.periodOrder }
            bad(own.isNotEmpty() && own.map { it.periodOrder }.distinct().size == own.size)
            bad(own.count { it.status == Status.ACTIVE } <= 1)
            bad((g.status == Status.ACTIVE) == own.any { it.status == Status.ACTIVE })
            if (own.any { it.status == Status.ACTIVE }) bad(own.last().status == Status.ACTIVE)
            val nonEmpty = own.filter { it.trackingStart <= it.trackingEnd }
            nonEmpty.zipWithNext().forEach { (a,b) -> bad(a.trackingEnd < b.trackingStart) }
        }
        s.periods.forEach { p ->
            id(p.id); bad(p.goalId in goals && p.periodOrder >= 1 && p.createdAt >= 0)
            date(p.start); date(p.end); date(p.trackingStart); date(p.trackingEnd); named(p.name)
            bad(p.start < p.end && p.trackingStart >= p.start && p.trackingStart >= dayOf(p.createdAt) + 1)
            bad(p.trackingEnd <= p.end)
            if (p.status == Status.ACTIVE) bad(p.archivedAt == null && p.archiveReason == null && p.trackingEnd == p.end)
            else {
                bad(p.archivedAt != null && p.archiveReason != null)
                if (p.archiveReason == ArchiveReason.MANUAL) bad(p.trackingEnd <= dayOf(p.archivedAt!!) - 1)
                else bad(p.end in days && p.trackingEnd == p.end)
            }
            val names = namesByPeriod[p.id].orEmpty()
            bad(names.isNotEmpty() && names.minOf { it.from } <= p.trackingStart)
        }
        s.revisions.forEach { r -> id(r.id); date(r.from); named(r.name); bad(r.periodId in periods) }
        s.days.forEach { d -> date(d.date); bad(d.date < today && d.finalizedAt > 0 && dayOf(d.finalizedAt) > d.date) }
        s.results.forEach { r ->
            id(r.id); date(r.date); named(r.goalNameSnapshot)
            val p = periods[r.periodId] ?: throw DomainException(ErrorCode.INVALID_BACKUP)
            bad(p.applies(r.date) && r.date < today && dayOf(r.recordedAt) > r.date)
            bad(r.finalized == (r.date in days))
            if (r.finalized) bad(r.recordedAt <= days.getValue(r.date).finalizedAt)
        }
        // Every finalized date must have exactly one row per applicable retained period.
        // Empty day seals can survive explicit goal deletion; they do not count in statistics.
        val resultsByDay = s.results.groupBy { it.date }
        val changes = mutableMapOf<Long, Int>()
        s.periods.filter { it.trackingStart <= it.trackingEnd }.forEach { p ->
            changes[p.trackingStart] = (changes[p.trackingStart] ?: 0) + 1
            changes[p.trackingEnd + 1] = (changes[p.trackingEnd + 1] ?: 0) - 1
        }
        val events = changes.entries.sortedBy { it.key }
        val activeCounts = mutableMapOf<Long, Int>()
        var eventIndex = 0; var activeCount = 0
        for (d in s.days.sortedBy { it.date }) {
            while (eventIndex < events.size && events[eventIndex].key <= d.date) {
                activeCount += events[eventIndex].value; eventIndex++
            }
            // Every row is already unique and belongs to an applicable period above.
            // Equality of counts therefore proves equality of the whole roster.
            bad(resultsByDay[d.date].orEmpty().size == activeCount)
            activeCounts[d.date] = activeCount
        }
        val pending = ReviewEngine.pending(s.periods, days.keys, today).oldest
        bad(s.results.filter { !it.finalized }.all { it.date == pending })
        // Finalized days cannot jump over an earlier eligible unreviewed day.
        if (pending != null) bad(s.days.none { it.date > pending && (activeCounts[it.date] ?: 0) > 0 })
    }
}
