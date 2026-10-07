package uz.dailygoals.domain

import java.time.*
import java.util.UUID

/** Dates are epoch days in the FIXED business timezone, never device timezone. */
interface BusinessClock {
    fun now(): Instant
    fun today(): LocalDate = now().atZone(ZONE).toLocalDate()
    companion object { val ZONE: ZoneId = ZoneId.of("Asia/Tashkent") }
}
class SystemBusinessClock : BusinessClock { override fun now(): Instant = Instant.now() }
class FixedBusinessClock(private var instant: Instant) : BusinessClock {
    override fun now(): Instant = instant
    fun set(value: Instant) { instant = value }
}
enum class Status { ACTIVE, ARCHIVED }
enum class ArchiveReason { MANUAL, EXPIRED }
enum class ResultValue { DONE, NOT_DONE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Goal(val id: String, val rootId: String, val insertionOrder: Long, val createdAt: Long, val status: Status)
data class GoalPeriod(
    val id: String, val goalId: String, val name: String,
    val start: Long, val trackingStart: Long, val end: Long,
    val status: Status = Status.ACTIVE, val periodOrder: Int,
    val createdAt: Long, val archivedAt: Long? = null, val archiveReason: ArchiveReason? = null,
    val trackingEnd: Long = end
) {
    fun applies(date: Long) = date >= trackingStart && date <= trackingEnd
}
data class NameRevision(val id: String, val periodId: String, val from: Long, val name: String)
data class DailyResult(
    val id: String, val periodId: String, val date: Long, val value: ResultValue,
    val goalNameSnapshot: String, val recordedAt: Long, val finalized: Boolean
)
data class FinalizedDay(val date: Long, val finalizedAt: Long)
data class Snapshot(
    val schemaVersion: Int = 1, val exportedAt: Long,
    val goals: List<Goal>, val periods: List<GoalPeriod>,
    val revisions: List<NameRevision>, val results: List<DailyResult>, val days: List<FinalizedDay>
)
data class PendingSummary(val oldest: Long?, val count: Long)
data class ReviewChange(val results: List<DailyResult>, val finalized: Boolean)
data class GoalCounts(val goalId: String, val name: String, val done: Long, val notDone: Long) {
    val percentage: Double? get() = Statistics.percentage(done, notDone)
}
data class DayCounts(val date: Long, val done: Long, val notDone: Long) {
    val percentage: Double? get() = Statistics.percentage(done, notDone)
    val complete: Boolean get() = done > 0 && notDone == 0L
}
data class StatisticsResult(
    val goals: List<GoalCounts>, val days: List<DayCounts>, val pending: Long,
    val overall: Double?, val completedDays: Int, val notCompletedDays: Int
)
data class DateRange(val start: Long, val end: Long) { init { require(start <= end) } }

enum class ErrorCode {
    EMPTY_NAME, NAME_TOO_LONG, INVALID_DATES, START_LOCKED, HISTORY_LOCKED, GOAL_ARCHIVED,
    WRONG_DAY, NO_PENDING, UNKNOWN_GOAL, NOT_APPLICABLE, INVALID_PIN, PIN_MISMATCH,
    IMPORT_NOT_EMPTY, INVALID_BACKUP, UNSUPPORTED_SCHEMA, FILE_TOO_LARGE, STORAGE_ERROR,
    NOTIFICATION_ERROR, NEED_UNLOCK
}
class DomainException(val code: ErrorCode) : IllegalArgumentException(code.name)
fun ensure(condition: Boolean, code: ErrorCode) { if (!condition) throw DomainException(code) }
fun newId(): String = UUID.randomUUID().toString()
fun dayOf(timestamp: Long): Long = Instant.ofEpochMilli(timestamp).atZone(BusinessClock.ZONE).toLocalDate().toEpochDay()
