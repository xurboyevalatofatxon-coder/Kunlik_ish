package uz.dailygoals.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.dailygoals.AppGraph
import uz.dailygoals.R
import uz.dailygoals.data.*
import uz.dailygoals.domain.*
import java.time.LocalDate
import java.time.YearMonth

private val EMPTY_STATS=Statistics.summarize(emptyList(),emptyList(),0)
data class ReviewItem(val period:GoalPeriod,val displayName:String,val selected:ResultValue?)
data class ReviewUi(val date:Long,val items:List<ReviewItem>)
data class HomeCard(val label:Int,val range:DateRange,val stats:StatisticsResult)
data class DetailUi(val goal:Goal,val periods:List<GoalPeriod>,val counts:List<PeriodAggregate>,
    val month:YearMonth,val results:List<DailyResult>,val maxRecorded:Map<String,Long?>)
data class UiState(
    val settings:UserSettings?=null,val ready:Boolean=false,val busy:Boolean=false,val unlocked:Boolean=false,
    val today:Long=0,val goals:List<Goal> = emptyList(),val periods:List<GoalPeriod> = emptyList(),
    val pending:PendingSummary=PendingSummary(null,0),val review:ReviewUi?=null,
    val cards:List<HomeCard> = emptyList(),val overall:StatisticsResult=EMPTY_STATS,
    val stats:StatisticsResult=EMPTY_STATS,val range:DateRange=DateRange(0,0),val detail:DetailUi?=null,
    val error:ErrorCode?=null,val info:Int?=null,val notificationsAllowed:Boolean=false
)
class MainViewModel(val graph:AppGraph):ViewModel() {
    private val repo=graph.repository
    private val mutable=MutableStateFlow(UiState(today=graph.clock.today().toEpochDay(),range=Statistics.month(graph.clock.today())))
    val state:StateFlow<UiState> = mutable.asStateFlow()
    private val mutation=Mutex();private val refreshLock=Mutex()
    private var detailId:String?=null
    private var detailMonth:YearMonth=YearMonth.from(graph.clock.today())
    private val saveResult=SaveResultUseCase(repo)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                graph.settings.flow.collect { settings ->
                    val initial=mutable.value.settings==null
                    mutable.update { it.copy(settings=settings,unlocked=if(!settings.pinEnabled) true else if(initial) false else it.unlocked,
                        notificationsAllowed=graph.reminders.allowed()) }
                    if(!graph.reminders.schedule(settings)) showError(ErrorCode.NOTIFICATION_ERROR)
                    refresh()
                }
            } catch (e:CancellationException) { throw e } catch (_:Exception) { showError(ErrorCode.STORAGE_ERROR) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.dao.watchChanges().collect { refresh() } }
            catch(e:CancellationException) { throw e } catch (_:Exception) { showError(ErrorCode.STORAGE_ERROR) }
        }
        viewModelScope.launch {
            while(isActive) { delay(30_000); if(graph.clock.today().toEpochDay()!=state.value.today) refreshSafe() }
        }
    }
    fun clearMessages() { mutable.update { it.copy(error=null,info=null) } }
    fun showError(code:ErrorCode) { mutable.update { it.copy(error=code,busy=false) } }
    fun lock() { if(state.value.settings?.pinEnabled==true) mutable.update { it.copy(unlocked=false) } }
    fun refreshSafe() { viewModelScope.launch(Dispatchers.IO) { try { refresh() } catch(e:CancellationException) { throw e } catch (_:Exception) { showError(ErrorCode.STORAGE_ERROR) } } }
    private suspend fun refresh()=refreshLock.withLock {
        repo.archiveExpired()
        val today=graph.clock.today();val day=today.toEpochDay()
        val goals=repo.dao.goals().map { it.model() };val periods=repo.dao.periods().map { it.model() }
        val days=repo.dao.days().map { it.model() };val pending=ReviewEngine.pending(periods,days.map { it.date }.toSet(),day)
        val review=pending.oldest?.let { date ->
            val drafts=repo.dao.resultsOn(date).map { it.model() }.associateBy { it.periodId }
            val names=repo.dao.names().map { it.model() };val order=goals.associate { it.id to it.insertionOrder }
            ReviewUi(date,periods.filter { it.applies(date) }.sortedBy { order[it.goalId] }.map { p ->
                ReviewItem(p,drafts[p.id]?.goalNameSnapshot?:GoalRules.nameForDate(p,date,names),drafts[p.id]?.value)
            })
        }
        val todayReview=Statistics.todayReview(days,day)
        val cardRanges=listOf(R.string.today to DateRange(todayReview?:day,todayReview?:day),R.string.week to Statistics.week(today),R.string.month to Statistics.month(today))
        val cards=cardRanges.map { (label,range)->HomeCard(label,range,repo.statistics(range)) }
        val all=repo.statistics(DateRange(LocalDate.of(1900,1,1).toEpochDay(),day))
        val selected=repo.statistics(mutable.value.range)
        mutable.update { it.copy(ready=it.settings!=null,today=day,goals=goals,periods=periods,pending=pending,review=review,cards=cards,overall=all,stats=selected,notificationsAllowed=graph.reminders.allowed()) }
        detailId?.let { loadDetailInternal(it,detailMonth) }
    }
    private fun runAction(action:suspend ()->Unit,onSuccess:(()->Unit)?=null) {
        if(mutable.value.busy) return
        mutable.update { it.copy(busy=true,error=null,info=null) }
        viewModelScope.launch(Dispatchers.IO) {
            mutation.withLock {
                try {
                    ensure(state.value.unlocked,ErrorCode.NEED_UNLOCK)
                    action();refresh()
                    withContext(Dispatchers.Main) { onSuccess?.invoke() }
                } catch(e:CancellationException) { throw e }
                catch(e:DomainException) { showError(e.code) }
                catch(_:Exception) { showError(ErrorCode.STORAGE_ERROR) }
                finally { mutable.update { it.copy(busy=false) } }
            }
        }
    }
    fun saveGoal(periodId:String?,name:String,start:Long,end:Long,onSaved:()->Unit)=runAction({
        if(periodId==null) repo.create(name,start,end) else repo.edit(periodId,name,start,end)
    },onSaved)
    fun select(date:Long,periodId:String,result:ResultValue)=runAction({saveResult(date,periodId,result)})
    fun archive(periodId:String,onDone:()->Unit)=runAction({repo.archive(periodId)},onDone)
    fun delete(goalId:String,onDone:()->Unit)=runAction({repo.delete(goalId);detailId=null;mutable.update { it.copy(detail=null) }},onDone)
    fun reactivate(goalId:String,name:String,end:Long,onDone:()->Unit)=runAction({repo.reactivate(goalId,name,end)},onDone)
    fun loadStats(range:DateRange) {
        mutable.update { it.copy(range=range) }
        viewModelScope.launch(Dispatchers.IO) {
            try { val value=repo.statistics(range);if(state.value.range==range) mutable.update { it.copy(stats=value) } }
            catch(e:CancellationException) { throw e } catch(_:Exception) { showError(ErrorCode.STORAGE_ERROR) }
        }
    }
    fun loadDetail(id:String,month:YearMonth=YearMonth.from(graph.clock.today())) {
        detailId=id;detailMonth=month
        viewModelScope.launch(Dispatchers.IO) {
            try { loadDetailInternal(id,month) } catch(e:CancellationException) { throw e } catch(_:Exception) { showError(ErrorCode.STORAGE_ERROR) }
        }
    }
    private suspend fun loadDetailInternal(id:String,month:YearMonth) {
        val goal=repo.dao.goal(id)?.model()?:return
        val periods=repo.dao.goalPeriods(id).map { it.model() }
        val result=DetailUi(goal,periods,repo.dao.periodStats(id),month,
            repo.dao.history(id,month.atDay(1).toEpochDay(),month.atEndOfMonth().toEpochDay()).map { it.model() },
            periods.associate { it.id to repo.dao.maxRecorded(it.id) })
        if(detailId==id && detailMonth==month) mutable.update { it.copy(detail=result) }
    }
    fun language(code:String)=runAction({graph.settings.setLanguage(code)})
    fun theme(mode:ThemeMode)=runAction({graph.settings.setTheme(mode)})
    fun notification(enabled:Boolean,hour:Int,minute:Int)=runAction({graph.settings.notifications(enabled,hour,minute)})
    fun unlock(pin:String) {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val credential=graph.settings.read().pin
                ensure(credential==null || PinHasher.verify(pin.toCharArray(),credential),ErrorCode.PIN_MISMATCH)
                mutable.update { it.copy(unlocked=true,error=null) }
            } catch(e:DomainException) { showError(e.code) }
            catch(e:CancellationException) { throw e } catch(_:Exception) { showError(ErrorCode.STORAGE_ERROR) }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    fun setPin(old:String,new:String,confirm:String,disable:Boolean,onDone:()->Unit)=runAction({
        val settings=graph.settings.read()
        if(settings.pinEnabled) ensure(PinHasher.verify(old.toCharArray(),settings.pin!!),ErrorCode.PIN_MISMATCH)
        if(disable) graph.settings.setPin(null) else {
            ensure(new==confirm,ErrorCode.PIN_MISMATCH)
            val credential=withContext(Dispatchers.Default) { PinHasher.create(new.toCharArray()) }
            graph.settings.setPin(credential)
        }
    },onDone)
    fun exportTo(context:Context,uri:Uri)=runAction({
        val text=repo.exportData()
        val output=context.contentResolver.openOutputStream(uri,"wt")?:throw DomainException(ErrorCode.STORAGE_ERROR)
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        mutable.update { it.copy(info=R.string.export_saved) }
    })
    fun importFrom(context:Context,uri:Uri)=runAction({
        ensure(repo.dao.goalCount()==0,ErrorCode.IMPORT_NOT_EMPTY)
        val input=context.contentResolver.openInputStream(uri)?:throw DomainException(ErrorCode.STORAGE_ERROR)
        val bytes=input.use {
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var count=0
            while(true) { val n=it.read(buffer);if(n<0) break;count+=n;ensure(count<=BackupCodec.MAX_BYTES,ErrorCode.FILE_TOO_LARGE);out.write(buffer,0,n) }
            out.toByteArray()
        }
        // Reject malformed UTF-8 rather than silently substituting replacement characters.
        val text=try { Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString() } catch (_:java.nio.charset.CharacterCodingException) { throw DomainException(ErrorCode.INVALID_BACKUP) }
        repo.importData(text);mutable.update { it.copy(info=R.string.import_done) }
    })
    fun deleteAll(onDone:()->Unit)=runAction({repo.deleteAll();graph.settings.reset();detailId=null;mutable.update { it.copy(detail=null,info=R.string.deleted_all) }},onDone)
    class Factory(private val graph:AppGraph):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=MainViewModel(graph) as T
    }
}
