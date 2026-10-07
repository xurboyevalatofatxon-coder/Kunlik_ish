package uz.dailygoals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import uz.dailygoals.presentation.*

class MainActivity:ComponentActivity() {
    private val vm:MainViewModel by viewModels { MainViewModel.Factory((application as DailyGoalsApp).graph) }
    private val locker=object:DefaultLifecycleObserver {
        override fun onStop(owner:LifecycleOwner) { vm.lock() }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        ProcessLifecycleOwner.get().lifecycle.addObserver(locker)
        setContent { DailyGoalsRoot(vm) }
    }
    override fun onResume() { super.onResume();vm.refreshSafe() }
    override fun onDestroy() { ProcessLifecycleOwner.get().lifecycle.removeObserver(locker);super.onDestroy() }
}
