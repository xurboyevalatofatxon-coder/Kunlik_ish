@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package uz.dailygoals.presentation

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.dailygoals.R
import uz.dailygoals.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalLanguage=staticCompositionLocalOf { "uz" }
@Composable fun tr(id:Int,vararg args:Any):String=LocalContext.current.getString(id,*args)
@Composable fun dateLabel(day:Long):String {
    val locale=Locale.forLanguageTag(LocalLanguage.current)
    return LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMMM yyyy",locale))
}
@Composable fun percentage(value:Double?):String=if(value==null) tr(R.string.no_results) else tr(R.string.percent,value)
@Composable fun AppTheme(mode:ThemeMode,content:@Composable ()->Unit) {
    val dark=mode==ThemeMode.DARK || (mode==ThemeMode.SYSTEM && isSystemInDarkTheme())
    val colors=if(dark) darkColorScheme(primary=Color(0xFFBBB7FF),background=Color(0xFF111318),surface=Color(0xFF191B23),primaryContainer=Color(0xFF373267))
    else lightColorScheme(primary=Color(0xFF5250D9),background=Color(0xFFF6F7FB),surface=Color.White,primaryContainer=Color(0xFFE7E5FF),onPrimaryContainer=Color(0xFF242254))
    MaterialTheme(colorScheme=colors,shapes=Shapes(medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp)),content=content)
}
@Composable fun PageColumn(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}
@Composable fun SectionTitle(text:String) { Text(text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold) }
@Composable fun InfoCard(title:String,body:String) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SectionTitle(title);Text(body,style=MaterialTheme.typography.bodyMedium)
    } }
}
@Composable fun DateButton(label:String,value:Long,enabled:Boolean=true,onValue:(Long)->Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick={open=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(4.dp)) { Text(label,style=MaterialTheme.typography.labelMedium);Text(dateLabel(value),style=MaterialTheme.typography.bodyLarge) }
    }
    if(open) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=LocalDate.ofEpochDay(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={
            picker.selectedDateMillis?.let { onValue(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) };open=false
        }) { Text(tr(R.string.ok)) }},dismissButton={TextButton(onClick={open=false}) { Text(tr(R.string.cancel)) }}) {
            DatePicker(state=picker)
        }
    }
}
@Composable fun ConfirmDialog(title:String,message:String,confirm:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(message)},
        confirmButton={TextButton(onClick=onConfirm) { Text(confirm) }},dismissButton={TextButton(onClick=onDismiss) { Text(tr(R.string.cancel)) }})
}
@Composable fun ErrorMessage(code:ErrorCode):String {
    val id=when(code) {
        ErrorCode.EMPTY_NAME->R.string.error_empty_name;ErrorCode.NAME_TOO_LONG->R.string.error_name_too_long
        ErrorCode.INVALID_DATES->R.string.error_invalid_dates;ErrorCode.START_LOCKED->R.string.error_start_locked
        ErrorCode.HISTORY_LOCKED->R.string.error_history_locked;ErrorCode.GOAL_ARCHIVED->R.string.error_goal_archived
        ErrorCode.WRONG_DAY->R.string.error_wrong_day;ErrorCode.NO_PENDING->R.string.error_no_pending
        ErrorCode.UNKNOWN_GOAL->R.string.error_unknown_goal;ErrorCode.NOT_APPLICABLE->R.string.error_not_applicable
        ErrorCode.INVALID_PIN->R.string.error_invalid_pin;ErrorCode.PIN_MISMATCH->R.string.error_pin_mismatch
        ErrorCode.IMPORT_NOT_EMPTY->R.string.error_import_not_empty;ErrorCode.INVALID_BACKUP->R.string.error_invalid_backup
        ErrorCode.UNSUPPORTED_SCHEMA->R.string.error_unsupported_schema;ErrorCode.FILE_TOO_LARGE->R.string.error_file_too_large
        ErrorCode.STORAGE_ERROR->R.string.error_storage_error;ErrorCode.NOTIFICATION_ERROR->R.string.error_notification_error
        ErrorCode.NEED_UNLOCK->R.string.error_need_unlock
    };return tr(id)
}
