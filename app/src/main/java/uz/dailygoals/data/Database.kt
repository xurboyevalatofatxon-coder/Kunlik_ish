package uz.dailygoals.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import uz.dailygoals.domain.*

@Entity(tableName="goals", indices=[Index(value=["insertionOrder"], unique=true)])
data class GoalRow(@PrimaryKey val id: String, val rootId: String, val insertionOrder: Long, val createdAt: Long, val status: String)
@Entity(tableName="periods", foreignKeys=[ForeignKey(entity=GoalRow::class, parentColumns=["id"], childColumns=["goalId"], onDelete=ForeignKey.CASCADE)],
    indices=[Index("goalId"), Index(value=["goalId","periodOrder"], unique=true)])
data class PeriodRow(@PrimaryKey val id: String, val goalId: String, val name: String, val start: Long, val trackingStart: Long,
    val end: Long, val status: String, val periodOrder: Int, val createdAt: Long, val archivedAt: Long?, val archiveReason: String?, val trackingEnd: Long)
@Entity(tableName="name_revisions", foreignKeys=[ForeignKey(entity=PeriodRow::class,parentColumns=["id"],childColumns=["periodId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index(value=["periodId","fromDate"],unique=true)])
data class NameRow(@PrimaryKey val id:String,val periodId:String,val fromDate:Long,val name:String)
@Entity(tableName="results", foreignKeys=[ForeignKey(entity=PeriodRow::class,parentColumns=["id"],childColumns=["periodId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index(value=["periodId","date"],unique=true),Index("date"),Index(value=["finalized","date"])])
data class ResultRow(@PrimaryKey val id:String,val periodId:String,val date:Long,val value:String,val goalNameSnapshot:String,val recordedAt:Long,val finalized:Boolean)
@Entity(tableName="days")
data class DayRow(@PrimaryKey val date:Long,val finalizedAt:Long)
data class GoalAggregate(val goalId:String,val name:String,val done:Long,val notDone:Long)
data class DailyAggregate(val date:Long,val done:Long,val notDone:Long)
data class PeriodAggregate(val periodId:String,val done:Long,val notDone:Long)

@Dao
interface GoalDao {
    @Query("SELECT (SELECT COUNT(*) FROM goals) + (SELECT COUNT(*) FROM periods) + (SELECT COUNT(*) FROM results) + (SELECT COUNT(*) FROM days) + (SELECT COUNT(*) FROM name_revisions)")
    fun watchChanges(): Flow<Long>
    @Query("SELECT * FROM goals ORDER BY insertionOrder") suspend fun goals():List<GoalRow>
    @Query("SELECT * FROM periods ORDER BY periodOrder") suspend fun periods():List<PeriodRow>
    @Query("SELECT * FROM name_revisions ORDER BY fromDate") suspend fun names():List<NameRow>
    @Query("SELECT * FROM days ORDER BY date") suspend fun days():List<DayRow>
    @Query("SELECT * FROM results WHERE date=:date ORDER BY periodId") suspend fun resultsOn(date:Long):List<ResultRow>
    @Query("SELECT * FROM results ORDER BY date") suspend fun allResultsForExport():List<ResultRow>
    @Query("SELECT COUNT(*) FROM goals") suspend fun goalCount():Int
    @Query("SELECT MAX(insertionOrder) FROM goals") suspend fun maxOrder():Long?
    @Query("SELECT MAX(date) FROM results WHERE periodId=:id") suspend fun maxRecorded(id:String):Long?
    @Query("SELECT * FROM periods WHERE id=:id") suspend fun period(id:String):PeriodRow?
    @Query("SELECT * FROM goals WHERE id=:id") suspend fun goal(id:String):GoalRow?
    @Query("SELECT * FROM periods WHERE goalId=:id ORDER BY periodOrder") suspend fun goalPeriods(id:String):List<PeriodRow>
    @Query("SELECT * FROM name_revisions WHERE periodId=:id AND fromDate=:date") suspend fun revision(id:String,date:Long):NameRow?
    @Query("""SELECT g.id AS goalId,
        (SELECT p2.name FROM periods p2 WHERE p2.goalId=g.id ORDER BY p2.periodOrder DESC LIMIT 1) AS name,
        SUM(CASE WHEN r.value='DONE' THEN 1 ELSE 0 END) AS done,
        SUM(CASE WHEN r.value='NOT_DONE' THEN 1 ELSE 0 END) AS notDone
        FROM goals g LEFT JOIN periods p ON p.goalId=g.id
        LEFT JOIN results r ON r.periodId=p.id AND r.finalized=1 AND r.date BETWEEN :start AND :end
        GROUP BY g.id ORDER BY g.insertionOrder""")
    suspend fun goalStats(start:Long,end:Long):List<GoalAggregate>
    @Query("""SELECT date, SUM(CASE WHEN value='DONE' THEN 1 ELSE 0 END) AS done,
        SUM(CASE WHEN value='NOT_DONE' THEN 1 ELSE 0 END) AS notDone
        FROM results WHERE finalized=1 AND date BETWEEN :start AND :end GROUP BY date ORDER BY date""")
    suspend fun dayStats(start:Long,end:Long):List<DailyAggregate>
    @Query("""SELECT p.id AS periodId, SUM(CASE WHEN r.value='DONE' THEN 1 ELSE 0 END) AS done,
        SUM(CASE WHEN r.value='NOT_DONE' THEN 1 ELSE 0 END) AS notDone
        FROM periods p LEFT JOIN results r ON r.periodId=p.id AND r.finalized=1 WHERE p.goalId=:goalId GROUP BY p.id""")
    suspend fun periodStats(goalId:String):List<PeriodAggregate>
    @Query("SELECT r.* FROM results r JOIN periods p ON p.id=r.periodId WHERE p.goalId=:goalId AND r.date BETWEEN :start AND :end ORDER BY r.date")
    suspend fun history(goalId:String,start:Long,end:Long):List<ResultRow>
    @Upsert suspend fun putGoal(row:GoalRow)
    @Upsert suspend fun putPeriod(row:PeriodRow)
    @Upsert suspend fun putName(row:NameRow)
    @Upsert suspend fun putResults(rows:List<ResultRow>)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun putDay(row:DayRow)
    @Query("DELETE FROM name_revisions WHERE periodId=:id") suspend fun deleteNames(id:String)
    @Query("DELETE FROM goals WHERE id=:id") suspend fun deleteGoal(id:String)
    @Query("DELETE FROM days WHERE NOT EXISTS (SELECT 1 FROM results r WHERE r.date=days.date)") suspend fun pruneEmptyDays()
    @Query("DELETE FROM goals") suspend fun deleteGoals()
    @Query("DELETE FROM days") suspend fun deleteDays()
}
@Database(entities=[GoalRow::class,PeriodRow::class,NameRow::class,ResultRow::class,DayRow::class],version=1,exportSchema=true)
abstract class DailyDatabase:RoomDatabase() {
    abstract fun dao():GoalDao
    companion object {
        val INTEGRITY_CALLBACK = object:Callback() {
            override fun onCreate(db:SupportSQLiteDatabase) {
                db.execSQL("""CREATE TRIGGER protect_final_results BEFORE UPDATE ON results
                    WHEN OLD.finalized=1 BEGIN SELECT RAISE(ABORT, 'FINALIZED_IMMUTABLE'); END""")
            }
        }
        fun create(context:Context)=Room.databaseBuilder(context,DailyDatabase::class.java,"daily-goals.db")
            .addCallback(INTEGRITY_CALLBACK).build() // Never use fallbackToDestructiveMigration.
    }
}
fun GoalRow.model()=Goal(id,rootId,insertionOrder,createdAt,Status.valueOf(status))
fun Goal.row()=GoalRow(id,rootId,insertionOrder,createdAt,status.name)
fun PeriodRow.model()=GoalPeriod(id,goalId,name,start,trackingStart,end,Status.valueOf(status),periodOrder,createdAt,archivedAt,archiveReason?.let { ArchiveReason.valueOf(it) },trackingEnd)
fun GoalPeriod.row()=PeriodRow(id,goalId,name,start,trackingStart,end,status.name,periodOrder,createdAt,archivedAt,archiveReason?.name,trackingEnd)
fun NameRow.model()=NameRevision(id,periodId,fromDate,name)
fun NameRevision.row()=NameRow(id,periodId,from,name)
fun ResultRow.model()=DailyResult(id,periodId,date,ResultValue.valueOf(value),goalNameSnapshot,recordedAt,finalized)
fun DailyResult.row()=ResultRow(id,periodId,date,value.name,goalNameSnapshot,recordedAt,finalized)
fun DayRow.model()=FinalizedDay(date,finalizedAt)
fun FinalizedDay.row()=DayRow(date,finalizedAt)
