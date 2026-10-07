@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package uz.dailygoals.presentation

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import uz.dailygoals.R
import uz.dailygoals.domain.*
import uz.dailygoals.platform.localizedContext
import java.time.LocalDate

data class Destination(val route:String,val label:Int,val icon:ImageVector)
private val destinations=listOf(Destination("home",R.string.home,Icons.Default.Home),Destination("goals",R.string.goals,Icons.Default.CheckCircle),
    Destination("stats",R.string.statistics,Icons.Default.BarChart),Destination("archive",R.string.archive,Icons.Default.Archive),Destination("settings",R.string.settings,Icons.Default.Settings))
@Composable fun DailyGoalsRoot(vm:MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val base=LocalContext.current
    val lang=state.settings?.language?:"uz"
    val localized=remember(base,lang) { localizedContext(base,lang) }
    val nav=rememberNavController()
    var transferUri by rememberSaveable { mutableStateOf<String?>(null) }
    var transferExport by rememberSaveable { mutableStateOf(true) }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri->if(uri!=null) { transferExport=true;transferUri=uri.toString() } }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null) { transferExport=false;transferUri=uri.toString() } }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshSafe() }
    LaunchedEffect(transferUri,state.unlocked,state.ready) {
        val uri=transferUri
        if(uri!=null && state.unlocked && state.ready) {
            transferUri=null
            if(transferExport) vm.exportTo(base,Uri.parse(uri)) else vm.importFrom(base,Uri.parse(uri))
        }
    }
    DisposableEffect(state.settings?.pinEnabled) {
        val activity=base as? Activity
        if(state.settings?.pinEnabled==true) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }
    CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides localized.resources.configuration,LocalLanguage provides lang) {
        AppTheme(state.settings?.theme?:ThemeMode.SYSTEM) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                when {
                    !state.ready -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator();Text(tr(R.string.loading))
                            if(state.error!=null) TextButton(onClick=vm::refreshSafe) { Text(tr(R.string.retry)) }
                        }
                    }
                    !state.unlocked -> PinEntryScreen(state.busy,vm::unlock)
                    else -> MainScaffold(nav,state,vm,
                        onExport={exporter.launch("daily-goals-${LocalDate.ofEpochDay(state.today)}.json")},
                        onImport={importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))},
                        onPermission={if(Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)})
                }
                state.error?.let { error -> AlertDialog(onDismissRequest=vm::clearMessages,title={Text(tr(R.string.error_title))},
                    text={Text(ErrorMessage(error))},confirmButton={TextButton(onClick=vm::clearMessages) { Text(tr(R.string.ok)) }}) }
                state.info?.let { info -> AlertDialog(onDismissRequest=vm::clearMessages,text={Text(tr(info))},confirmButton={TextButton(onClick=vm::clearMessages) { Text(tr(R.string.ok)) }}) }
            }
        }
    }
}
@Composable private fun MainScaffold(nav:NavHostController,state:UiState,vm:MainViewModel,onExport:()->Unit,onImport:()->Unit,onPermission:()->Unit) {
    val entry by nav.currentBackStackEntryAsState();val route=entry?.destination?.route?:"home"
    val main=destinations.any { it.route==route }
    val title=destinations.find { it.route==route }?.label?:when {
        route=="create"->R.string.add_goal;route.startsWith("edit")->R.string.edit_goal
        route=="review"->R.string.review;route.startsWith("reactivate")->R.string.reactivate
        route.startsWith("pin")->R.string.security;else->R.string.goal_detail
    }
    Scaffold(topBar={TopAppBar(title={Text(tr(title),maxLines=2,overflow=TextOverflow.Ellipsis)},navigationIcon={
        if(!main) IconButton(onClick={nav.popBackStack()}) { Icon(Icons.AutoMirrored.Filled.ArrowBack,tr(R.string.back)) }
    })},bottomBar={if(main) NavigationBar { destinations.forEach { item ->
        NavigationBarItem(selected=route==item.route,onClick={nav.navigate(item.route) { popUpTo("home") { saveState=true };launchSingleTop=true;restoreState=true }},
            icon={Icon(item.icon,null)},label={Text(tr(item.label),maxLines=2,overflow=TextOverflow.Ellipsis)})
    } }},floatingActionButton={if(main) FloatingActionButton(onClick={nav.navigate("create")}) { Icon(Icons.Default.Add,tr(R.string.add_goal)) }}) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            NavHost(navController=nav,startDestination="home") {
                composable("home") { HomeScreen(state,onReview={nav.navigate("review")},onAdd={nav.navigate("create")},onStats={range->vm.loadStats(range);nav.navigate("stats")}) }
                composable("goals") { GoalListScreen(state,false,onGoal={nav.navigate("detail/$it")},onAdd={nav.navigate("create")}) }
                composable("archive") { GoalListScreen(state,true,onGoal={nav.navigate("detail/$it")},onAdd={nav.navigate("create")}) }
                composable("stats") { StatisticsScreen(state,vm::loadStats) }
                composable("review") { ReviewScreen(state,vm::select,onStats={vm.loadStats(DateRange(LocalDate.of(1900,1,1).toEpochDay(),state.today));nav.navigate("stats")}) }
                composable("create") { GoalFormScreen(state,null,false,onSave={name,start,end->vm.saveGoal(null,name,start,end){nav.popBackStack()} }) }
                composable("detail/{id}") { back->
                    val id=back.arguments?.getString("id").orEmpty()
                    LaunchedEffect(id) { vm.loadDetail(id) }
                    val detail=state.detail?.takeIf { it.goal.id==id }
                    if(detail!=null) GoalDetailScreen(state,detail,vm,
                        onEdit={nav.navigate("edit/$it")},onReactivate={nav.navigate("reactivate/$id")},onDeleted={nav.popBackStack()})
                }
                composable("edit/{id}") { back->
                    val id=back.arguments?.getString("id");val p=state.periods.find { it.id==id }
                    if(p!=null) GoalFormScreen(state,p,false,onSave={name,start,end->vm.saveGoal(p.id,name,start,end){nav.popBackStack()} })
                }
                composable("reactivate/{id}") { back->
                    val id=back.arguments?.getString("id").orEmpty();val p=state.periods.filter { it.goalId==id }.maxByOrNull { it.periodOrder }
                    if(p!=null) GoalFormScreen(state,p,true,onSave={name,_,end->vm.reactivate(id,name,end){nav.popBackStack()} })
                }
                composable("settings") { SettingsScreen(state,vm,onExport,onImport,onPermission,
                    onPin={mode->nav.navigate("pin/$mode")},onDeleteAll={vm.deleteAll { nav.navigate("home") { popUpTo("home"){inclusive=true} } }}) }
                composable("pin/{mode}") { back->PinSetupScreen(state,back.arguments?.getString("mode")=="disable",onSave={old,new,confirm,disable->vm.setPin(old,new,confirm,disable){nav.popBackStack()} }) }
            }
            if(state.busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
