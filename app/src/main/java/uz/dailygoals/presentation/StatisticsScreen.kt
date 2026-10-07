package uz.dailygoals.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import uz.dailygoals.R
import uz.dailygoals.domain.*
import java.time.LocalDate
import kotlin.math.abs

@Composable fun StatsSummary(stats:StatisticsResult) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(percentage(stats.overall),style=MaterialTheme.typography.headlineLarge)
            Text(tr(R.string.completed_days)+": ${stats.completedDays}")
            Text(tr(R.string.failed_days)+": ${stats.notCompletedDays}")
            Text(tr(R.string.pending_count,stats.pending))
        }
    }
}
@Composable fun StatisticsScreen(state:UiState,onRange:(DateRange)->Unit) {
    var custom by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf(true) }
    val today=LocalDate.ofEpochDay(state.today)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,16.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={onRange(Statistics.week(today))}) { Text(tr(R.string.week)) }
                OutlinedButton(onClick={onRange(Statistics.month(today))}) { Text(tr(R.string.month)) }
                OutlinedButton(onClick={custom=true}) { Text(tr(R.string.custom)) }
                OutlinedButton(onClick={onRange(DateRange(LocalDate.of(1900,1,1).toEpochDay(),state.today))}) { Text(tr(R.string.all_time)) }
            }
            Text(dateLabel(state.range.start)+" — "+dateLabel(state.range.end),style=MaterialTheme.typography.bodyMedium)
        }
        item { StatsSummary(state.stats) }
        item { Text(tr(R.string.stats_note),style=MaterialTheme.typography.bodySmall) }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=line,onClick={line=true},label={Text(tr(R.string.line_chart))})
                FilterChip(selected=!line,onClick={line=false},label={Text(tr(R.string.bar_chart))})
            }
            DailyChart(state.stats.days,line)
        }
        item { SectionTitle(tr(R.string.goal_ranking)) }
        items(state.stats.goals,key={"goal-"+it.goalId}) { goal ->
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(goal.name,style=MaterialTheme.typography.titleMedium)
                Text(percentage(goal.percentage));Text(tr(R.string.counts,goal.done,goal.notDone))
            } }
        }
        item { SectionTitle(tr(R.string.daily_values)) }
        // Equivalent text for every chart datum makes all values available to TalkBack.
        items(state.stats.days,key={"date-"+it.date}) { day ->
            val description=tr(R.string.show_day,dateLabel(day.date),day.done,day.notDone,day.percentage?:0.0)
            Text(description,Modifier.fillMaxWidth().padding(vertical=8.dp).semantics(mergeDescendants=true) { contentDescription=description })
        }
    }
    if(custom) CustomRangeDialog(state.range,onDismiss={custom=false},onApply={custom=false;onRange(it)})
}
@Composable private fun CustomRangeDialog(initial:DateRange,onDismiss:()->Unit,onApply:(DateRange)->Unit) {
    var start by remember { mutableLongStateOf(initial.start) };var end by remember { mutableLongStateOf(initial.end) }
    var single by remember { mutableStateOf(initial.start==initial.end) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr(R.string.custom))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=single,onClick={single=true;end=start},label={Text(tr(R.string.single_date))})
                FilterChip(selected=!single,onClick={single=false},label={Text(tr(R.string.date_range))})
            }
            DateButton(tr(R.string.start_date),start,onValue={start=it;if(single||end<it) end=it})
            if(!single) DateButton(tr(R.string.end_date),end,onValue={end=it;if(start>it) start=it})
        }
    },confirmButton={TextButton(onClick={onApply(DateRange(start,if(single)start else end))}){Text(tr(R.string.apply))}},dismissButton={TextButton(onClick=onDismiss){Text(tr(R.string.cancel))}})
}
@Composable private fun DailyChart(days:List<DayCounts>,line:Boolean) {
    if(days.isEmpty()) { Text(tr(R.string.no_chart),Modifier.padding(vertical=24.dp));return }
    var selected by remember(days) { mutableStateOf<DayCounts?>(null) }
    val color=MaterialTheme.colorScheme.primary;val grid=MaterialTheme.colorScheme.outlineVariant
    val description=tr(R.string.chart_desc)
    Canvas(Modifier.fillMaxWidth().height(220.dp).padding(12.dp).semantics { contentDescription=description }.pointerInput(days,line) {
        detectTapGestures { tap ->
            val first=days.first().date;val length=(days.last().date-first).coerceAtLeast(1)
            val target=first+tap.x/size.width*length
            selected=days.minByOrNull { abs(it.date-target) }
        }
    }) {
        (0..4).forEach { n->val y=size.height*n/4f;drawLine(grid,Offset(0f,y),Offset(size.width,y),1.dp.toPx()) }
        val first=days.first().date;val span=(days.last().date-first).coerceAtLeast(1)
        fun point(day:DayCounts)=Offset(if(days.size==1)size.width/2 else (day.date-first).toFloat()/span*size.width,
            size.height*(1f-(day.percentage?:0.0).toFloat()/100f))
        if(line) {
            val path=Path();days.forEachIndexed { i,day->val p=point(day);if(i==0)path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y) }
            drawPath(path,color,style=Stroke(width=3.dp.toPx()))
            days.forEach { drawCircle(color,4.dp.toPx(),point(it)) }
        } else {
            val width=(size.width/(span+1)*.65f).coerceIn(2.dp.toPx(),32.dp.toPx())
            days.forEach { day->val p=point(day);drawLine(color,Offset(p.x,size.height),p,width) }
        }
    }
    selected?.let { day->AlertDialog(onDismissRequest={selected=null},title={Text(dateLabel(day.date))},text={
        Text(tr(R.string.show_day,dateLabel(day.date),day.done,day.notDone,day.percentage?:0.0))
    },confirmButton={TextButton(onClick={selected=null}){Text(tr(R.string.close))}}) }
}
