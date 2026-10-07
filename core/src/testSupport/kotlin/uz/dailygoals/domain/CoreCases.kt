package uz.dailygoals.domain

import java.time.*
import java.util.TimeZone

/** The exact same specifications run offline and via Gradle/JUnit. */
object CoreCases {
    private fun d(v:String)=LocalDate.parse(v).toEpochDay()
    private fun t(v:String)=Instant.parse(v).toEpochMilli()
    private val creation=t("2026-10-01T07:00:00Z")
    private val now=t("2026-10-04T07:00:00Z")
    private val today=d("2026-10-04")
    private fun equal(expected:Any?,actual:Any?) { check(expected==actual) { "Expected $expected, got $actual" } }
    private fun close(expected:Double,actual:Double?) { check(actual!=null && kotlin.math.abs(expected-actual)<0.000001) { "$expected != $actual" } }
    private fun fail(code:ErrorCode,block:()->Unit) { try { block();error("Expected $code") } catch(e:DomainException) { equal(code,e.code) } }
    private fun fixture(n:Int=2,end:Long=d("2026-10-10")):Snapshot {
        val goals=(1..n).map { val id=newId();Goal(id,id,it.toLong(),creation,Status.ACTIVE) }
        val ps=goals.mapIndexed { i,g->GoalRules.newPeriod(g.id,"Goal ${i+1}",d("2026-10-01"),end,1,creation) }
        return Snapshot(exportedAt=now,goals=goals,periods=ps,revisions=ps.map { NameRevision(newId(),it.id,it.trackingStart,it.name) },results=emptyList(),days=emptyList())
    }
    private fun answer(s:Snapshot,index:Int,value:ResultValue,date:Long=d("2026-10-02"),time:Long=now):Snapshot {
        val c=ReviewEngine.select(date,s.periods[index].id,value,time,s.periods,s.days.map { it.date }.toSet(),s.results.filter { it.date==date },s.revisions)
        return s.copy(results=s.results.filter { it.date!=date }+c.results,days=s.days+if(c.finalized) listOf(FinalizedDay(date,time)) else emptyList())
    }
    private fun finished():Snapshot=answer(answer(fixture(),0,ResultValue.DONE),1,ResultValue.NOT_DONE)
    val cases:List<Pair<String,()->Unit>> = listOf(
        "blank goal rejected" to { fail(ErrorCode.EMPTY_NAME) { GoalRules.cleanName("  \n ") } },
        "goal name trimmed" to { equal("Read",GoalRules.cleanName(" Read ")) },
        "fifty code points accepted" to { equal(50,GoalRules.cleanName("a".repeat(50)).length) },
        "fifty one code points rejected" to { fail(ErrorCode.NAME_TOO_LONG) { GoalRules.cleanName("a".repeat(51)) } },
        "unicode goal length counted correctly" to { equal(100,GoalRules.cleanName("😀".repeat(50)).length) },
        "start equal end rejected" to { fail(ErrorCode.INVALID_DATES) { GoalRules.newPeriod(newId(),"A",today,today,1,now) } },
        "end preceding start rejected" to { fail(ErrorCode.INVALID_DATES) { GoalRules.newPeriod(newId(),"A",today,today-1,1,now) } },
        "new goal begins tomorrow" to { equal(today+1,GoalRules.newPeriod(newId(),"A",today,today+30,1,now).trackingStart) },
        "future configured date preserved" to { equal(today+7,GoalRules.newPeriod(newId(),"A",today+7,today+30,1,now).trackingStart) },
        "past configured date never backfills" to { equal(today+1,GoalRules.newPeriod(newId(),"A",today-20,today+30,1,now).trackingStart) },
        "no remaining tracking day rejected" to { fail(ErrorCode.INVALID_DATES) { GoalRules.newPeriod(newId(),"A",today-20,today-10,1,now) } },
        "dependent end corrected" to { equal(today+1,GoalRules.normalizedEnd(today,today-4,today+1)) },
        "duration thirty days matches example" to { equal(d("2026-11-04"),d("2026-10-05")+30) },
        "end date is inclusive" to { val p=fixture().periods.first();check(p.applies(p.end));check(!p.applies(p.end+1)) },
        "creation day excluded" to { check(!fixture().periods.first().applies(d("2026-10-01"))) },
        "fixed timezone crosses UTC midnight boundary" to { val c=FixedBusinessClock(Instant.parse("2026-10-01T20:00:00Z"));equal(LocalDate.parse("2026-10-02"),c.today()) },
        "device timezone does not affect business date" to { val old=TimeZone.getDefault();try { TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));equal(today,dayOf(now)) } finally { TimeZone.setDefault(old) } },
        "oldest pending selected" to { equal(d("2026-10-02"),ReviewEngine.pending(fixture().periods,emptySet(),today).oldest) },
        "overlapping goals do not double count days" to { equal(2L,ReviewEngine.pending(fixture(20).periods,emptySet(),today).count) },
        "no goals no pending" to { equal(PendingSummary(null,0),ReviewEngine.pending(emptyList(),emptySet(),today)) },
        "future periods not pending" to { val p=GoalRules.newPeriod(newId(),"A",today+3,today+30,1,now);equal(null,ReviewEngine.pending(listOf(p),emptySet(),today).oldest) },
        "interval gaps excluded" to { val f=fixture();val a=f.periods[0].copy(trackingStart=today-10,trackingEnd=today-8);val b=f.periods[1].copy(trackingStart=today-3,trackingEnd=today-2);equal(5L,ReviewEngine.pending(listOf(a,b),emptySet(),today).count) },
        "cannot jump pending day" to { fail(ErrorCode.WRONG_DAY) { answer(fixture(),0,ResultValue.DONE,d("2026-10-03")) } },
        "cannot review current calendar day" to { fail(ErrorCode.WRONG_DAY) { answer(fixture(),0,ResultValue.DONE,today) } },
        "one answer persists without finalization" to { val s=answer(fixture(),0,ResultValue.DONE);equal(1,s.results.size);equal(false,s.results[0].finalized);check(s.days.isEmpty()) },
        "unfinalized selection toggles" to { val s=answer(answer(fixture(),0,ResultValue.DONE),0,ResultValue.NOT_DONE);equal(1,s.results.size);equal(ResultValue.NOT_DONE,s.results[0].value) },
        "last answer finalizes all rows atomically" to { val s=finished();equal(1,s.days.size);check(s.results.all { it.finalized }) },
        "next oldest day follows automatically" to { val s=finished();equal(d("2026-10-03"),ReviewEngine.pending(s.periods,s.days.map { it.date }.toSet(),today).oldest) },
        "finalized day rejects changes" to { fail(ErrorCode.HISTORY_LOCKED) { answer(finished(),0,ResultValue.NOT_DONE) } },
        "period not applicable rejected" to { val s=fixture();fail(ErrorCode.NOT_APPLICABLE) { ReviewEngine.select(d("2026-10-02"),newId(),ResultValue.DONE,now,s.periods,emptySet(),emptyList(),s.revisions) } },
        "single goal finalizes on one tap" to { check(answer(fixture(1),0,ResultValue.DONE).results.single().finalized) },
        "history keeps old name after rename" to { val s=finished();val p=s.periods[0];val changed=GoalRules.edit(p,"Renamed",p.start,p.end,d("2026-10-02"),now);equal("Renamed",changed.name);equal("Goal 1",s.results.find { it.periodId==p.id }!!.goalNameSnapshot) },
        "backlog gets date appropriate name" to { val s=fixture();val p=s.periods[0].copy(name="New");val rev=s.revisions+NameRevision(newId(),p.id,today,"New");equal("Goal 1",GoalRules.nameForDate(p,today-1,rev));equal("New",GoalRules.nameForDate(p,today,rev)) },
        "any recorded result locks start" to { val p=fixture().periods[0];fail(ErrorCode.START_LOCKED) { GoalRules.edit(p,"Name",p.start+1,p.end,today-1,now) } },
        "no result allows future start change" to { val p=fixture().periods[0];equal(today+3,GoalRules.edit(p,"Name",today+3,today+30,null,now).trackingStart) },
        "end cannot exclude a recorded result" to { val p=fixture().periods[0];fail(ErrorCode.HISTORY_LOCKED) { GoalRules.edit(p,p.name,p.start,d("2026-10-02"),d("2026-10-03"),now) } },
        "end may extend without changing records" to { val p=fixture().periods[0];equal(p.end+10,GoalRules.edit(p,p.name,p.start,p.end+10,today-1,now).end) },
        "goal percentage one decimal input unrounded" to { close(100.0/3,Statistics.percentage(1,2)) },
        "zero result has no percentage" to { equal(null,Statistics.percentage(0,0)) },
        "overall is not pooled" to { close(95.0,Statistics.overall(listOf(GoalCounts("a","A",9,1),GoalCounts("b","B",1,0)))) },
        "zero result goal excluded from overall" to { close(50.0,Statistics.overall(listOf(GoalCounts("a","A",1,1),GoalCounts("b","B",0,0)))) },
        "all zero result goals remain pending" to { equal(null,Statistics.overall(listOf(GoalCounts("a","A",0,0)))) },
        "daily five goals four done is eighty" to { close(80.0,DayCounts(today-1,4,1).percentage) },
        "one not done means incomplete day" to { equal(false,DayCounts(today-1,9,1).complete) },
        "all done means completed day" to { equal(true,DayCounts(today-1,3,0).complete) },
        "draft results excluded from statistics" to { val s=answer(fixture(),0,ResultValue.DONE);equal(null,Statistics.fromResults(s.goals,s.periods,s.results,s.days,DateRange(today-3,today)).overall) },
        "week Monday to Sunday" to { equal(DateRange(d("2026-10-05"),d("2026-10-11")),Statistics.week(LocalDate.parse("2026-10-07"))) },
        "month full calendar including leap day" to { equal(DateRange(d("2024-02-01"),d("2024-02-29")),Statistics.month(LocalDate.parse("2024-02-12"))) },
        "today means completed today not calendar today" to { val s=finished();equal(d("2026-10-02"),Statistics.todayReview(s.days,today)) },
        "today equal timestamp tie selects later reviewed date" to { equal(today-1,Statistics.todayReview(listOf(FinalizedDay(today-2,now),FinalizedDay(today-1,now)),today)) },
        "no review completed today returns pending" to { equal(null,Statistics.todayReview(listOf(FinalizedDay(today-2,now-86400000)),today)) },
        "custom range excludes outside results" to { val s=finished();equal(null,Statistics.fromResults(s.goals,s.periods,s.results,s.days,DateRange(today-1,today)).overall) },
        "manual archive ends yesterday" to { val p=GoalRules.manualArchive(fixture().periods[0],now);equal(today-1,p.trackingEnd);equal(Status.ARCHIVED,p.status) },
        "archive preserves earlier pending dates" to { val p=GoalRules.manualArchive(fixture().periods[0],now);equal(2L,ReviewEngine.pending(listOf(p),emptySet(),today).count) },
        "archive of future goal creates no phantom days" to { val p=GoalRules.newPeriod(newId(),"A",today+2,today+30,1,now);equal(0L,ReviewEngine.pending(listOf(GoalRules.manualArchive(p,now)),emptySet(),today).count) },
        "expired period waits for final applicable day" to { val p=fixture(1,d("2026-10-03")).periods[0];equal(false,GoalRules.canAutoArchive(p,setOf(d("2026-10-02")),today));equal(true,GoalRules.canAutoArchive(p,setOf(d("2026-10-03")),today)) },
        "reactivation uses new period id and tomorrow" to { val p=GoalRules.manualArchive(fixture().periods[0],now);val n=GoalRules.reactivate(p,"Again",today+30,now);check(p.id!=n.id);equal(p.goalId,n.goalId);equal(today+1,n.trackingStart);equal(2,n.periodOrder) },
        "reactivation does not mutate old period" to { val p=GoalRules.manualArchive(fixture().periods[0],now);GoalRules.reactivate(p,"Other",today+40,now);equal("Goal 1",p.name);equal(today-1,p.trackingEnd) },
        "archived period not editable" to { val p=GoalRules.manualArchive(fixture().periods[0],now);fail(ErrorCode.GOAL_ARCHIVED) { GoalRules.edit(p,"x",p.start,p.end,null,now) } },
        "finalized backup round trip" to { val s=finished();equal(s,BackupCodec.decode(BackupCodec.encode(s),today)) },
        "partial backup round trip" to { val s=answer(fixture(),0,ResultValue.DONE);equal(s,BackupCodec.decode(BackupCodec.encode(s),today)) },
        "empty backup round trip" to { val s=Snapshot(exportedAt=now,goals=emptyList(),periods=emptyList(),revisions=emptyList(),results=emptyList(),days=emptyList());equal(s,BackupCodec.decode(BackupCodec.encode(s),today)) },
        "unsupported schema rejected" to { fail(ErrorCode.UNSUPPORTED_SCHEMA) { BackupCodec.decode(BackupCodec.encode(fixture()).replace("\"schemaVersion\":1","\"schemaVersion\":2"),today) } },
        "schema integer overflow rejected" to { fail(ErrorCode.UNSUPPORTED_SCHEMA) { BackupCodec.decode(BackupCodec.encode(fixture()).replace("\"schemaVersion\":1","\"schemaVersion\":4294967297"),today) } },
        "corrupt JSON rejected" to { fail(ErrorCode.INVALID_BACKUP) { BackupCodec.decode("{bad",today) } },
        "trailing JSON rejected" to { fail(ErrorCode.INVALID_BACKUP) { BackupCodec.decode(BackupCodec.encode(fixture())+"[]",today) } },
        "duplicate JSON keys rejected" to { fail(ErrorCode.INVALID_BACKUP) { BackupCodec.decode("{\"schemaVersion\":1,\"schemaVersion\":1}",today) } },
        "JSON string escaping preserved" to { val value="Uzbek ‘test’ \"quoted\" \\ slash\nline 😀";equal(value,Json.parse(Json.write(value))) },
        "unknown period foreign key rejected" to { val s=finished();fail(ErrorCode.INVALID_BACKUP) { BackupValidator.validate(s.copy(results=s.results.map { it.copy(periodId=newId()) }),today) } },
        "duplicate daily result rejected" to { val s=finished();fail(ErrorCode.INVALID_BACKUP) { BackupValidator.validate(s.copy(results=s.results+s.results[0].copy(id=newId())),today) } },
        "invalid date string rejected" to { val text=BackupCodec.encode(fixture()).replace("2026-10-01","2026-02-30");fail(ErrorCode.INVALID_BACKUP) { BackupCodec.decode(text,today) } },
        "inconsistent finalized flag rejected" to { val s=finished();fail(ErrorCode.INVALID_BACKUP) { BackupValidator.validate(s.copy(results=s.results.map { it.copy(finalized=false) }),today) } },
        "incomplete finalized roster rejected" to { val s=finished();fail(ErrorCode.INVALID_BACKUP) { BackupValidator.validate(s.copy(results=s.results.take(1)),today) } },
        "result from creation day rejected" to { val s=finished();fail(ErrorCode.INVALID_BACKUP) { BackupValidator.validate(s.copy(results=s.results.map { it.copy(date=d("2026-10-01")) }),today) } },
        "future result rejected" to { val s=finished();fail(ErrorCode.INVALID_BACKUP) { BackupValidator.validate(s.copy(results=s.results.map { it.copy(date=today) }),today) } },
        "PIN correct succeeds wrong fails" to { val c=PinHasher.create("0123".toCharArray());check(PinHasher.verify("0123".toCharArray(),c));check(!PinHasher.verify("0124".toCharArray(),c)) },
        "PIN stored as salted hash not plaintext" to { val a=PinHasher.create("1234".toCharArray());val b=PinHasher.create("1234".toCharArray());equal(64,a.hash.length);check(a.hash!=b.hash);check(a.salt!=b.salt) },
        "invalid PIN length rejected" to { fail(ErrorCode.INVALID_PIN) { PinHasher.create("12345".toCharArray()) } },
        "invalid PIN characters rejected" to { fail(ErrorCode.INVALID_PIN) { PinHasher.create("12ab".toCharArray()) } },
        "unlimited wrong attempts do not lock correct PIN" to { val c=PinHasher.create("7788".toCharArray());repeat(12) { check(!PinHasher.verify("0000".toCharArray(),c)) };check(PinHasher.verify("7788".toCharArray(),c)) },
        "malformed credential fails closed" to { check(!PinHasher.verify("1234".toCharArray(),PinCredential("x","bad"))) },
        "large overlap pending query remains correct" to { val periods=fixture(500).periods.map { it.copy(trackingStart=today-10000,trackingEnd=today-1) };val closed=(today-10000 until today-3).toSet();equal(PendingSummary(today-3,3),ReviewEngine.pending(periods,closed,today)) }
    )
}
