package uz.dailygoals.data

import androidx.room.withTransaction
import uz.dailygoals.domain.*

/** Every mutation is transactional. Database IO is dispatched by Room/coroutines. */
class LocalGoalRepository(val db:DailyDatabase,val clock:BusinessClock):GoalRepository {
    val dao=db.dao()
    private fun now()=clock.now().toEpochMilli()
    private fun today()=clock.today().toEpochDay()
    override suspend fun create(name:String,start:Long,end:Long):String = db.withTransaction {
        val id=newId(); val time=now()
        val period=GoalRules.newPeriod(id,name,start,end,1,time)
        dao.putGoal(Goal(id,id,(dao.maxOrder()?:0)+1,time,Status.ACTIVE).row())
        dao.putPeriod(period.row())
        dao.putName(NameRevision(newId(),period.id,period.trackingStart,period.name).row())
        id
    }
    override suspend fun edit(periodId:String,name:String,start:Long,end:Long)=db.withTransaction {
        val p=dao.period(periodId)?.model()?:throw DomainException(ErrorCode.UNKNOWN_GOAL)
        val changed=GoalRules.edit(p,name,start,end,dao.maxRecorded(periodId),now())
        // Reject retroactive additions to already finalized dates (protect day membership).
        val closed=dao.days().map { it.date }
        ensure(closed.none { changed.applies(it) && !p.applies(it) },ErrorCode.HISTORY_LOCKED)
        dao.putPeriod(changed.row())
        if(changed.trackingStart!=p.trackingStart) {
            dao.deleteNames(p.id)
            dao.putName(NameRevision(newId(),p.id,changed.trackingStart,changed.name).row())
        } else if(changed.name!=p.name) {
            val from=maxOf(today(),changed.trackingStart)
            val existing=dao.revision(p.id,from)
            dao.putName(NameRevision(existing?.id?:newId(),p.id,from,changed.name).row())
        }
        finalizeReadyDays()
    }
    override suspend fun select(date:Long,periodId:String,value:ResultValue)=db.withTransaction {
        val periods=dao.periods().map { it.model() }
        val closed=dao.days().map { it.date }.toSet()
        val change=ReviewEngine.select(date,periodId,value,now(),periods,closed,
            dao.resultsOn(date).map { it.model() },dao.names().map { it.model() })
        dao.putResults(change.results.map { it.row() })
        if(change.finalized) dao.putDay(DayRow(date,now()))
        autoArchiveInTransaction()
    }
    override suspend fun archive(periodId:String)=db.withTransaction {
        val p=dao.period(periodId)?.model()?:throw DomainException(ErrorCode.UNKNOWN_GOAL)
        val updated=GoalRules.manualArchive(p,now())
        dao.putPeriod(updated.row()); updateGoalStatus(p.goalId)
        finalizeReadyDays()
    }
    override suspend fun reactivate(goalId:String,name:String,end:Long)=db.withTransaction {
        val g=dao.goal(goalId)?.model()?:throw DomainException(ErrorCode.UNKNOWN_GOAL)
        ensure(g.status==Status.ARCHIVED,ErrorCode.INVALID_DATES)
        val last=dao.goalPeriods(goalId).last().model()
        val p=GoalRules.reactivate(last,name,end,now())
        dao.putPeriod(p.row()); dao.putName(NameRevision(newId(),p.id,p.trackingStart,p.name).row())
        dao.putGoal(g.copy(status=Status.ACTIVE).row())
    }
    override suspend fun delete(goalId:String)=db.withTransaction {
        dao.deleteGoal(goalId); dao.pruneEmptyDays(); finalizeReadyDays()
    }
    override suspend fun deleteAll()=db.withTransaction { dao.deleteGoals(); dao.deleteDays() }
    override suspend fun pending():PendingSummary = ReviewEngine.pending(dao.periods().map { it.model() },dao.days().map { it.date }.toSet(),today())
    override suspend fun statistics(range:DateRange):StatisticsResult = db.withTransaction {
        val g=dao.goalStats(range.start,range.end).map { GoalCounts(it.goalId,it.name,it.done,it.notDone) }
        val d=dao.dayStats(range.start,range.end).map { DayCounts(it.date,it.done,it.notDone) }
        val p=ReviewEngine.pending(dao.periods().map { it.model() },dao.days().map { it.date }.toSet(),today(),range)
        Statistics.summarize(g,d,p.count)
    }
    suspend fun archiveExpired()=db.withTransaction { autoArchiveInTransaction() }
    private suspend fun autoArchiveInTransaction() {
        val closed=dao.days().map { it.date }.toSet()
        dao.periods().map { it.model() }.filter { GoalRules.canAutoArchive(it,closed,today()) }.forEach {
            dao.putPeriod(GoalRules.autoArchive(it,now()).row()); updateGoalStatus(it.goalId)
        }
    }
    private suspend fun updateGoalStatus(id:String) {
        val g=dao.goal(id)?.model()?:return
        val active=dao.goalPeriods(id).any { it.status==Status.ACTIVE.name }
        dao.putGoal(g.copy(status=if(active) Status.ACTIVE else Status.ARCHIVED).row())
    }
    /** A deletion/shortening may leave all remaining answers selected; finalize without a new button. */
    private suspend fun finalizeReadyDays() {
        while(true) {
            val all=dao.periods().map { it.model() }; val closed=dao.days().map { it.date }.toSet()
            val date=ReviewEngine.pending(all,closed,today()).oldest?:break
            val results=dao.resultsOn(date).map { it.model() }
            if(!ReviewEngine.readyToFinalize(date,all,results)) break
            dao.putResults(results.map { it.copy(finalized=true).row() }); dao.putDay(DayRow(date,now()))
        }
        autoArchiveInTransaction()
    }
    override suspend fun exportData():String= db.withTransaction {
        BackupCodec.encode(Snapshot(exportedAt=now(),goals=dao.goals().map { it.model() },periods=dao.periods().map { it.model() },
            revisions=dao.names().map { it.model() },results=dao.allResultsForExport().map { it.model() },days=dao.days().map { it.model() }))
    }
    override suspend fun importData(text:String) {
        // Fast rejection before expensive parsing, then re-check inside the write transaction.
        ensure(dao.goalCount()==0,ErrorCode.IMPORT_NOT_EMPTY)
        val s=BackupCodec.decode(text,today())
        db.withTransaction {
            ensure(dao.goalCount()==0 && dao.days().isEmpty(),ErrorCode.IMPORT_NOT_EMPTY)
            s.goals.forEach { dao.putGoal(it.row()) }; s.periods.forEach { dao.putPeriod(it.row()) }
            s.revisions.forEach { dao.putName(it.row()) }; dao.putResults(s.results.map { it.row() })
            s.days.forEach { dao.putDay(it.row()) }
            autoArchiveInTransaction()
        }
    }
}
