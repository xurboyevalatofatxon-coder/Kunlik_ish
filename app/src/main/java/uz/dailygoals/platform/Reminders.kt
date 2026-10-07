package uz.dailygoals.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import uz.dailygoals.*
import uz.dailygoals.data.UserSettings
import uz.dailygoals.domain.BusinessClock
import java.time.ZonedDateTime
import java.util.Locale

fun localizedContext(context:Context,language:String):Context {
    val config=android.content.res.Configuration(context.resources.configuration)
    config.setLocale(Locale.forLanguageTag(language));return context.createConfigurationContext(config)
}
class ReminderScheduler(private val context:Context,private val clock:BusinessClock) {
    private val alarm=context.getSystemService(AlarmManager::class.java)
    private fun intent()=PendingIntent.getBroadcast(context,4101,Intent(context,ReminderReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(settings:UserSettings):Boolean {
        return try {
            alarm.cancel(intent())
            if(!settings.notificationEnabled) return true
            val now=clock.now().atZone(BusinessClock.ZONE)
            var next=now.toLocalDate().atTime(settings.hour,settings.minute).atZone(BusinessClock.ZONE)
            if(!next.isAfter(now)) next=next.plusDays(1)
            // Inexact by design: no restricted exact-alarm permission; OS may defer delivery.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),intent())
            true
        } catch (_:RuntimeException) { false }
    }
    fun allowed():Boolean = (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun show(settings:UserSettings) {
        if(!settings.notificationEnabled || !allowed()) return
        val ctx=localizedContext(context,settings.language)
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("daily-review",ctx.getString(R.string.reminder_channel),NotificationManager.IMPORTANCE_DEFAULT))
        val tap=PendingIntent.getActivity(context,4102,Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_home",true)
        },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,"daily-review")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(ctx.getString(R.string.reminder_text)).setContentIntent(tap).setAutoCancel(true).build()
        if(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) manager.notify(4103,notification)
    }
}
class ReminderReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val token=goAsync();val graph=(context.applicationContext as DailyGoalsApp).graph
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                withTimeout(8500) {
                    val settings=graph.settings.read()
                    if(graph.repository.pending().oldest!=null) graph.reminders.show(settings)
                    graph.reminders.schedule(settings)
                }
            } catch (_:Exception) { /* Receiver must not crash or expose user data in logs. */ }
            finally { token.finish() }
        }
    }
}
class RescheduleReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val token=goAsync();val graph=(context.applicationContext as DailyGoalsApp).graph
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try { withTimeout(8500) { graph.reminders.schedule(graph.settings.read()) } }
            catch (_:Exception) { } finally { token.finish() }
        }
    }
}
