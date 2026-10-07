package uz.dailygoals.presentation

import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uz.dailygoals.R
import uz.dailygoals.domain.*
import java.util.Locale

@Composable fun SettingsScreen(state:UiState,vm:MainViewModel,onExport:()->Unit,onImport:()->Unit,onPermission:()->Unit,onPin:(String)->Unit,onDeleteAll:()->Unit) {
    val settings=state.settings?:return
    val context=LocalContext.current
    var exportWarning by remember { mutableStateOf(false) }
    var deleteStage by remember { mutableIntStateOf(0) }
    PageColumn(Modifier.padding(bottom=70.dp)) {
        SectionTitle(tr(R.string.notifications))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text(tr(R.string.enabled));Switch(checked=settings.notificationEnabled,onCheckedChange={
                vm.notification(it,settings.hour,settings.minute);if(it&&!state.notificationsAllowed) onPermission()
            })
        }
        OutlinedButton(onClick={TimePickerDialog(context,{_,h,m->vm.notification(settings.notificationEnabled,h,m)},settings.hour,settings.minute,true).show()}) {
            Text(tr(R.string.reminder_time)+"  "+String.format(Locale.ROOT,"%02d:%02d",settings.hour,settings.minute))
        }
        Text(tr(R.string.timezone_note),style=MaterialTheme.typography.bodySmall)
        if(settings.notificationEnabled&&!state.notificationsAllowed) {
            Text(tr(R.string.permission_needed),color=MaterialTheme.colorScheme.error)
            TextButton(onClick=onPermission){Text(tr(R.string.grant_permission))}
        }
        HorizontalDivider();SectionTitle(tr(R.string.language))
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("uz" to "O‘zbek","ru" to "Русский","en" to "English").forEach { (code,label)->
                FilterChip(selected=settings.language==code,onClick={vm.language(code)},label={Text(label)})
            }
        }
        SectionTitle(tr(R.string.theme))
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.LIGHT to R.string.light,ThemeMode.DARK to R.string.dark,ThemeMode.SYSTEM to R.string.system).forEach { (mode,label)->
                FilterChip(selected=settings.theme==mode,onClick={vm.theme(mode)},label={Text(tr(label))})
            }
        }
        HorizontalDivider();SectionTitle(tr(R.string.security));Text(tr(R.string.pin_limit_note),style=MaterialTheme.typography.bodySmall)
        if(settings.pinEnabled) {
            OutlinedButton(onClick={onPin("change")}){Text(tr(R.string.change_pin))}
            TextButton(onClick={onPin("disable")}){Text(tr(R.string.disable_pin))}
        } else OutlinedButton(onClick={onPin("setup")}){Text(tr(R.string.setup_pin))}
        HorizontalDivider();SectionTitle(tr(R.string.data))
        Text(tr(R.string.import_note),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={exportWarning=true},modifier=Modifier.fillMaxWidth()){Text(tr(R.string.export))}
        OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()){Text(tr(R.string.import_action))}
        TextButton(onClick={deleteStage=1},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text(tr(R.string.delete_all))}
    }
    if(exportWarning) ConfirmDialog(tr(R.string.export),tr(R.string.export_warning),tr(R.string.export),{exportWarning=false},{exportWarning=false;onExport()})
    if(deleteStage==1) AlertDialog(onDismissRequest={deleteStage=0},title={Text(tr(R.string.delete_all))},text={Text(tr(R.string.export_first))},
        confirmButton={Column {
            TextButton(onClick={deleteStage=0;exportWarning=true}){Text(tr(R.string.export))}
            TextButton(onClick={deleteStage=2}){Text(tr(R.string.continue_delete))}
        }},dismissButton={TextButton(onClick={deleteStage=0}){Text(tr(R.string.cancel))}})
    if(deleteStage==2) ConfirmDialog(tr(R.string.delete_all),tr(R.string.delete_all_confirm),tr(R.string.delete_all),{deleteStage=0},{deleteStage=0;onDeleteAll()})
}
@Composable private fun PinField(label:String,value:String,onChange:(String)->Unit) {
    OutlinedTextField(value=value,onValueChange={onChange(it.filter { c->c in '0'..'9' }.take(4))},label={Text(label)},singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
}
@Composable fun PinEntryScreen(busy:Boolean,onUnlock:(String)->Unit) {
    // PIN text intentionally not saveable or persisted in SavedStateHandle.
    var pin by remember { mutableStateOf("") };var forgot by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Text(tr(R.string.app_name),style=MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(28.dp));Text(tr(R.string.enter_pin));Spacer(Modifier.height(16.dp))
        PinField(tr(R.string.pin_lock),pin,{pin=it})
        Button(onClick={val submitted=pin;pin="";onUnlock(submitted)},enabled=pin.length==4&&!busy,modifier=Modifier.fillMaxWidth().padding(top=20.dp)){Text(tr(R.string.unlock))}
        TextButton(onClick={forgot=true}){Text(tr(R.string.forgot_pin))}
    }
    if(forgot) AlertDialog(onDismissRequest={forgot=false},title={Text(tr(R.string.forgot_pin))},text={Text(tr(R.string.forgot_pin_help))},confirmButton={TextButton(onClick={forgot=false}){Text(tr(R.string.close))}})
}
@Composable fun PinSetupScreen(state:UiState,disable:Boolean,onSave:(String,String,String,Boolean)->Unit) {
    var old by remember { mutableStateOf("") };var new by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") }
    val exists=state.settings?.pinEnabled==true
    PageColumn {
        Text(tr(R.string.pin_limit_note))
        if(exists) PinField(tr(R.string.old_pin),old,{old=it})
        if(!disable) { PinField(tr(R.string.new_pin),new,{new=it});PinField(tr(R.string.confirm_pin),confirm,{confirm=it}) }
        Button(onClick={onSave(old,new,confirm,disable)},enabled=!state.busy&&(!exists||old.length==4)&&(disable||(new.length==4&&confirm.length==4)),modifier=Modifier.fillMaxWidth()){
            Text(tr(if(disable) R.string.disable_pin else R.string.save))
        }
        Text(tr(R.string.forgot_pin_help),style=MaterialTheme.typography.bodySmall)
    }
}
