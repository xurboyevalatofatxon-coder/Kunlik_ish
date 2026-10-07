package uz.dailygoals

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import uz.dailygoals.data.*
import uz.dailygoals.domain.*
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomRepositoryTest {
    private lateinit var db:DailyDatabase
    private lateinit var clock:FixedBusinessClock
    private lateinit var repo:LocalGoalRepository
    @Before fun setup() {
        clock=FixedBusinessClock(Instant.parse("2026-10-01T07:00:00Z"))
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),DailyDatabase::class.java)
            .addCallback(DailyDatabase.INTEGRITY_CALLBACK).build()
        repo=LocalGoalRepository(db,clock)
    }
    @After fun close() { db.close() }
    private suspend fun goal(name:String="Goal",days:Long=30):String { val today=clock.today().toEpochDay();return repo.create(name,today,today+days) }
    private fun advance() { clock.set(Instant.parse("2026-10-04T07:00:00Z")) }
    @Test fun transactionFinalizesAndRejectsRewrite()=runBlocking {
        val id=goal();val p=repo.dao.goalPeriods(id).single().model();advance()
        val date=repo.pending().oldest!!;repo.select(date,p.id,ResultValue.DONE)
        assertTrue(repo.dao.resultsOn(date).single().finalized)
        try { repo.select(date,p.id,ResultValue.NOT_DONE);fail("Must reject rewrite") }
        catch(e:DomainException){assertEquals(ErrorCode.HISTORY_LOCKED,e.code)}
    }
    @Test fun sqliteTriggerProtectsFinalizedRow()=runBlocking {
        val id=goal();val p=repo.dao.goalPeriods(id).single();advance()
        val date=repo.pending().oldest!!;repo.select(date,p.id,ResultValue.DONE)
        val row=repo.dao.resultsOn(date).single()
        try { repo.dao.putResults(listOf(row.copy(value="NOT_DONE")));fail("Trigger must reject update") }
        catch(_:android.database.sqlite.SQLiteException) { }
        assertEquals("DONE",repo.dao.resultsOn(date).single().value)
    }
    @Test fun importRejectsNonemptyDatabase()=runBlocking {
        goal();val text=repo.exportData()
        try { repo.importData(text);fail("Must reject nonempty import") }
        catch(e:DomainException){assertEquals(ErrorCode.IMPORT_NOT_EMPTY,e.code)}
    }
    @Test fun exportDeleteImportRoundtrip()=runBlocking {
        val id=goal();val p=repo.dao.goalPeriods(id).single();advance()
        val date=repo.pending().oldest!!;repo.select(date,p.id,ResultValue.DONE)
        val text=repo.exportData();repo.deleteAll();assertEquals(0,repo.dao.goalCount())
        repo.importData(text);assertEquals(1,repo.dao.goalCount());assertTrue(repo.dao.resultsOn(date).single().finalized)
    }
    @Test fun corruptionLeavesEmptyDatabaseUntouched()=runBlocking {
        try { repo.importData("{broken");fail("Must reject") }catch(e:DomainException){assertEquals(ErrorCode.INVALID_BACKUP,e.code)}
        assertEquals(0,repo.dao.goalCount());assertTrue(repo.dao.days().isEmpty())
    }
    @Test fun deleteCascadesAndChangesStatistics()=runBlocking {
        val a=goal("A");val b=goal("B");advance()
        val date=repo.pending().oldest!!
        repo.select(date,repo.dao.goalPeriods(a).single().id,ResultValue.DONE)
        repo.select(date,repo.dao.goalPeriods(b).single().id,ResultValue.NOT_DONE)
        repo.delete(a)
        assertTrue(repo.dao.goalPeriods(a).isEmpty());assertEquals(1,repo.dao.resultsOn(date).size)
        assertEquals(0.0,repo.statistics(DateRange(date,date)).overall!!,0.0001)
    }
    @Test fun deleteUnansweredGoalFinalizesRemainingDraft()=runBlocking {
        val a=goal("A");val b=goal("B");advance();val date=repo.pending().oldest!!
        repo.select(date,repo.dao.goalPeriods(a).single().id,ResultValue.DONE)
        repo.delete(b)
        assertTrue(repo.dao.resultsOn(date).single().finalized)
    }
    @Test fun finalApplicableDateAutomaticallyArchives()=runBlocking {
        val id=goal(days=2);val p=repo.dao.goalPeriods(id).single();advance()
        repo.select(p.trackingStart,p.id,ResultValue.DONE);repo.select(p.end,p.id,ResultValue.DONE)
        assertEquals("ARCHIVED",repo.dao.goal(id)!!.status)
        assertNull(repo.pending().oldest)
    }
    @Test fun renamingBacklogPreservesDateAppropriateNames()=runBlocking {
        val id=goal("Old name");val p=repo.dao.goalPeriods(id).single();advance()
        repo.edit(p.id,"New name",p.start,p.end)
        val date=repo.pending().oldest!!;repo.select(date,p.id,ResultValue.DONE)
        assertEquals("Old name",repo.dao.resultsOn(date).single().goalNameSnapshot)
    }
    @Test fun manualArchiveLeavesPendingHistoryAvailable()=runBlocking {
        val id=goal();val p=repo.dao.goalPeriods(id).single();advance();repo.archive(p.id)
        assertEquals("ARCHIVED",repo.dao.goal(id)!!.status)
        assertEquals(2L,repo.pending().count)
    }
}
