@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package uz.dailygoals.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import uz.dailygoals.R
import uz.dailygoals.domain.*
import java.time.*

@Composable fun HomeScreen(state:UiState,onReview:()->Unit,onAdd:()->Unit,onStats:(DateRange)->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,16.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(state.goals.isEmpty()) item {
            InfoCard(tr(R.string.app_name),tr(R.string.empty_goals))
            Button(onClick=onAdd,modifier=Modifier.fillMaxWidth().padding(top=16.dp)) { Text("+ "+tr(R.string.add_goal)) }
        } else {
            item {
                val date=state.pending.oldest
                if(date!=null) {
                    SectionTitle(tr(R.string.oldest_pending,dateLabel(date)))
                    Text(tr(R.string.more_pending,state.pending.count-1),Modifier.padding(vertical=8.dp))
                    Button(onClick=onReview,modifier=Modifier.fillMaxWidth()) { Text(tr(R.string.check_goals)) }
                } else SectionTitle(tr(R.string.all_reviewed))
            }
            items(state.cards,key={it.label}) { card ->
                ElevatedCard(onClick={onStats(card.range)},modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(tr(card.label),style=MaterialTheme.typography.titleMedium)
                        Text(percentage(card.stats.overall),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                        Text(tr(R.string.counts,card.stats.completedDays,card.stats.notCompletedDays))
                        if(card.label==R.string.today) Text(tr(R.string.today_note),style=MaterialTheme.typography.bodySmall)
                        card.stats.goals.forEach { goal ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(goal.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                                Text(percentage(goal.percentage),style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable fun GoalListScreen(state:UiState,archived:Boolean,onGoal:(String)->Unit,onAdd:()->Unit) {
    val periods=state.periods.groupBy { it.goalId }
    val goals=state.goals.filter { (it.status==Status.ARCHIVED)==archived }.let { list ->
        if(archived) list.sortedByDescending { g->periods[g.id].orEmpty().maxOfOrNull { it.archivedAt?:0 }?:0 } else list.sortedBy { it.insertionOrder }
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,16.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(goals.isEmpty()) item {
            Text(tr(if(archived) R.string.empty_archive else R.string.empty_goals),style=MaterialTheme.typography.titleMedium)
            if(!archived) Button(onClick=onAdd,modifier=Modifier.padding(top=16.dp)) { Text("+ "+tr(R.string.add_goal)) }
        }
        items(goals,key={it.id}) { goal ->
            val name=periods[goal.id]?.maxByOrNull { it.periodOrder }?.name.orEmpty()
            ElevatedCard(onClick={onGoal(goal.id)},modifier=Modifier.fillMaxWidth()) {
                // Active cards deliberately contain ONLY the goal name.
                Text(name,Modifier.fillMaxWidth().padding(22.dp),style=MaterialTheme.typography.titleMedium)
            }
        }
    }
}
@Composable fun ReviewScreen(state:UiState,onSelect:(Long,String,ResultValue)->Unit,onStats:()->Unit) {
    val review=state.review
    if(review==null) PageColumn {
        SectionTitle(tr(R.string.all_reviewed));StatsSummary(state.overall)
        Button(onClick=onStats) { Text(tr(R.string.statistics)) }
    } else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(dateLabel(review.date),style=MaterialTheme.typography.headlineSmall);Text(tr(R.string.review_hint),Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyMedium) }
        items(review.items,key={it.period.id}) { item ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    SectionTitle(item.displayName)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        ResultValue.entries.forEach { value ->
                            val selected=item.selected==value
                            val color=if(value==ResultValue.DONE) Color(0xFF147746) else Color(0xFFB3261E)
                            val dark=MaterialTheme.colorScheme.background.luminance()<0.5f
                            val readable=if(!dark) color else if(value==ResultValue.DONE) Color(0xFF7CDDA4) else Color(0xFFFFB4AB)
                            val label=tr(if(value==ResultValue.DONE) R.string.done else R.string.not_done)
                            Button(onClick={onSelect(review.date,item.period.id,value)},enabled=!state.busy,
                                modifier=Modifier.weight(1f).heightIn(min=64.dp).semantics { this.selected=selected },
                                colors=ButtonDefaults.buttonColors(containerColor=if(selected) color else readable.copy(alpha=.12f),contentColor=if(selected) Color.White else readable),
                                shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(8.dp)) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                    Icon(if(value==ResultValue.DONE) Icons.Default.Check else Icons.Default.Close,null)
                                    Text(label,style=MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class EditorState(val name:String,val start:Long,val end:Long,val startLocked:Boolean,val error:ErrorCode?=null) {
    val canSave:Boolean get()=name.isNotBlank() && error==null
}
class GoalEditorViewModel(period:GoalPeriod?,reactivation:Boolean,today:Long,locked:Boolean):ViewModel() {
    private val today=today
    private val initialStart=if(reactivation) today+1 else period?.start?:today
    private val initialEnd=if(reactivation || period==null) initialStart+30 else period.end
    private val effective=if(period!=null && !reactivation) period.trackingStart else today+1
    private val originalStart=initialStart
    private val mutable=MutableStateFlow(EditorState(period?.name.orEmpty(),initialStart,initialEnd,reactivation||locked))
    val state:StateFlow<EditorState> = mutable.asStateFlow()
    private fun earliest(start:Long)=if(start==originalStart) effective else GoalRules.trackingStart(start,today)
    fun name(value:String) {
        val error=runCatching { GoalRules.cleanName(value) }.exceptionOrNull().let { (it as? DomainException)?.code }
        mutable.update { it.copy(name=value,error=error) }
    }
    fun start(value:Long) { if(!state.value.startLocked) mutable.update { it.copy(start=value,end=GoalRules.normalizedEnd(value,it.end,earliest(value))) } }
    fun end(value:Long) { mutable.update { it.copy(end=GoalRules.normalizedEnd(it.start,value,earliest(it.start))) } }
    fun duration(days:Long)=end(state.value.start+days)
    class Factory(private val p:GoalPeriod?,private val reactivate:Boolean,private val today:Long,private val locked:Boolean):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=GoalEditorViewModel(p,reactivate,today,locked) as T
    }
}
@Composable fun GoalFormScreen(app:UiState,period:GoalPeriod?,reactivation:Boolean,onSave:(String,Long,Long)->Unit) {
    val hasResults=period?.let { app.detail?.maxRecorded?.get(it.id)!=null }?:false
    val editor:GoalEditorViewModel=viewModel(key="editor-${period?.id?:"new"}-$reactivation",factory=GoalEditorViewModel.Factory(period,reactivation,app.today,hasResults))
    val state by editor.state.collectAsStateWithLifecycle()
    PageColumn {
        OutlinedTextField(value=state.name,onValueChange=editor::name,label={Text(tr(R.string.goal_name))},singleLine=true,
            modifier=Modifier.fillMaxWidth(),isError=state.name.isBlank() || state.error!=null,
            supportingText={Text(if(state.name.isBlank()) tr(R.string.enter_name) else state.error?.let { ErrorMessage(it) }?:"${state.name.codePointCount(0,state.name.length)} / 50")})
        DateButton(tr(R.string.start_date),state.start,!state.startLocked,editor::start)
        if(state.startLocked && !reactivation) Text(tr(R.string.start_locked),style=MaterialTheme.typography.bodySmall)
        DateButton(tr(R.string.end_date),state.end,onValue=editor::end)
        Text(tr(if(reactivation) R.string.reactivate_note else R.string.tracking_note),style=MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(7,30,90).forEach { days ->
            OutlinedButton(onClick={editor.duration(days.toLong())},modifier=Modifier.weight(1f),contentPadding=PaddingValues(8.dp)) { Text(tr(R.string.days_preset,days)) }
        } }
        Button(onClick={onSave(state.name,state.start,state.end)},enabled=state.canSave&&!app.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(tr(R.string.save)) }
    }
}
@Composable fun GoalDetailScreen(app:UiState,detail:DetailUi,vm:MainViewModel,onEdit:(String)->Unit,onReactivate:()->Unit,onDeleted:()->Unit) {
    var chosen by rememberSaveable(detail.goal.id) { mutableStateOf(detail.periods.last().id) }
    val p=detail.periods.find { it.id==chosen }?:detail.periods.last()
    var confirm by remember { mutableStateOf<String?>(null) }
    PageColumn {
        Text(p.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={onEdit(p.id)},enabled=p.status==Status.ACTIVE) { Text(tr(R.string.edit)) }
            TextButton(onClick={confirm="delete"},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Text(tr(R.string.delete)) }
        }
        if(detail.periods.size>1) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            detail.periods.forEach { period->FilterChip(selected=p.id==period.id,onClick={chosen=period.id},label={Text(tr(R.string.period,period.periodOrder))}) }
        }
        Text(tr(R.string.start_date)+": "+dateLabel(p.start))
        Text(tr(R.string.end_date)+": "+dateLabel(p.end))
        Text(tr(if(p.status==Status.ACTIVE) R.string.active else R.string.archived),color=MaterialTheme.colorScheme.primary)
        Text(if(p.trackingStart<=p.trackingEnd) tr(R.string.tracking_dates,dateLabel(p.trackingStart),dateLabel(p.trackingEnd)) else tr(R.string.no_tracking_days),style=MaterialTheme.typography.bodySmall)
        detail.counts.find { it.periodId==p.id }?.let { counts->
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                SectionTitle(tr(R.string.statistics));Text(percentage(Statistics.percentage(counts.done,counts.notDone)),style=MaterialTheme.typography.headlineMedium)
                Text(tr(R.string.counts,counts.done,counts.notDone))
            } }
        }
        if(p.status==Status.ACTIVE) OutlinedButton(onClick={confirm="archive"}) { Text(tr(R.string.archive_action)) }
        else if(detail.goal.status==Status.ARCHIVED) Button(onClick=onReactivate) { Text(tr(R.string.reactivate)) }
        SectionTitle(tr(R.string.history));Text(tr(R.string.immutable),style=MaterialTheme.typography.bodySmall)
        HistoryCalendar(detail.month,p,detail.results.filter { it.periodId==p.id },app.today,onMonth={vm.loadDetail(detail.goal.id,it)})
    }
    if(confirm=="archive") ConfirmDialog(tr(R.string.archive_action),tr(R.string.archive_confirm),tr(R.string.archive_action),{confirm=null},{confirm=null;vm.archive(p.id){}})
    if(confirm=="delete") ConfirmDialog(tr(R.string.delete),tr(R.string.delete_confirm),tr(R.string.delete),{confirm=null},{confirm=null;vm.delete(detail.goal.id,onDeleted)})
}
@Composable private fun HistoryCalendar(month:YearMonth,period:GoalPeriod,results:List<DailyResult>,today:Long,onMonth:(YearMonth)->Unit) {
    val locale=java.util.Locale.forLanguageTag(LocalLanguage.current)
    var selected by remember(month,period.id) { mutableStateOf<Long?>(null) }
    val byDate=results.associateBy { it.date }
    val previousLabel=tr(R.string.previous);val nextLabel=tr(R.string.next)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        IconButton(onClick={onMonth(month.minusMonths(1))}) { Icon(Icons.Default.KeyboardArrowLeft,previousLabel) }
        Text(month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy",locale)),style=MaterialTheme.typography.titleMedium)
        IconButton(onClick={onMonth(month.plusMonths(1))}) { Icon(Icons.Default.KeyboardArrowRight,nextLabel) }
    }
    Column(Modifier.horizontalScroll(rememberScrollState())) { Column(Modifier.width(350.dp)) {
    Row(Modifier.fillMaxWidth()) { java.time.DayOfWeek.values().forEach { day->Text(day.getDisplayName(java.time.format.TextStyle.SHORT,locale),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall) } }
    val pad=month.atDay(1).dayOfWeek.value-1
    val cells=List(pad){0}+(1..month.lengthOfMonth()).toList()
    cells.chunked(7).forEach { row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            (row+List(7-row.size){0}).forEach { number ->
                if(number==0) Spacer(Modifier.weight(1f)) else {
                    val date=month.atDay(number).toEpochDay();val result=byDate[date]
                    val finalized=result?.finalized==true
                    val icon=if(finalized) { if(result?.value==ResultValue.DONE) "✓" else "×" } else if(period.applies(date)&&date<today) "…" else "·"
                    val label=if(finalized) tr(if(result?.value==ResultValue.DONE) R.string.done else R.string.not_done) else if(period.applies(date)&&date<today) tr(R.string.no_results) else tr(R.string.not_applicable)
                    val description=dateLabel(date)+": "+label
                    val dark=MaterialTheme.colorScheme.background.luminance()<0.5f
                    val color=if(finalized&&result?.value==ResultValue.DONE) (if(dark) Color(0xFF7CDDA4) else Color(0xFF147746)) else if(finalized) (if(dark) Color(0xFFFFB4AB) else Color(0xFFB3261E)) else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(onClick={selected=date},modifier=Modifier.weight(1f).heightIn(min=52.dp).semantics { contentDescription=description },
                        shape=RoundedCornerShape(8.dp),color=if(finalized) color.copy(alpha=.1f) else MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text(number.toString(),style=MaterialTheme.typography.bodySmall);Text(icon,color=color) }
                    }
                }
            }
        }
    }
    } }
    selected?.let { date ->
        val record=byDate[date]
        val text=when {
            record?.finalized==true -> record.goalNameSnapshot+"\n"+tr(if(record.value==ResultValue.DONE) R.string.done else R.string.not_done)+"\n"+tr(R.string.immutable)
            period.applies(date)&&date<today -> tr(R.string.no_results)
            period.applies(date) -> tr(R.string.future)
            else -> tr(R.string.not_applicable)
        }
        AlertDialog(onDismissRequest={selected=null},title={Text(dateLabel(date))},text={Text(text)},confirmButton={TextButton(onClick={selected=null}){Text(tr(R.string.close))}})
    }
}
