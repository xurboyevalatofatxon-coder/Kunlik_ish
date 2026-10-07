package uz.dailygoals

import android.app.Application
import uz.dailygoals.data.*
import uz.dailygoals.domain.*
import uz.dailygoals.platform.ReminderScheduler

/** Explicit constructor DI: a small single-process app does not require a Hilt graph. */
class AppGraph(app:Application) {
    val clock:BusinessClock=SystemBusinessClock()
    val database=DailyDatabase.create(app)
    val repository=LocalGoalRepository(database,clock)
    val settings=SettingsStore(app)
    val reminders=ReminderScheduler(app,clock)
}
class DailyGoalsApp:Application() { val graph:AppGraph by lazy { AppGraph(this) } }
