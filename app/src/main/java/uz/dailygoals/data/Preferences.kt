package uz.dailygoals.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import uz.dailygoals.domain.*

private val Context.dailySettings by preferencesDataStore("daily-settings")
data class UserSettings(
    val language:String="uz",val theme:ThemeMode=ThemeMode.SYSTEM,
    val notificationEnabled:Boolean=true,val hour:Int=8,val minute:Int=0,
    val pin:PinCredential?=null
) { val pinEnabled:Boolean get()=pin!=null }
class SettingsStore(context:Context) {
    private val store=context.dailySettings
    private val language=stringPreferencesKey("language")
    private val theme=stringPreferencesKey("theme")
    private val notifications=booleanPreferencesKey("notifications")
    private val hour=intPreferencesKey("notificationHour")
    private val minute=intPreferencesKey("notificationMinute")
    private val hash=stringPreferencesKey("pinHash")
    private val salt=stringPreferencesKey("pinSalt")
    private val iterations=intPreferencesKey("pinIterations")
    // No corruption-to-default handler: a damaged PIN store must not silently bypass the lock.
    val flow:Flow<UserSettings> = store.data.map { p ->
        val h=p[hash];val s=p[salt]
        ensure((h==null)==(s==null),ErrorCode.STORAGE_ERROR)
        UserSettings(p[language]?:"uz",runCatching { ThemeMode.valueOf(p[theme]?:"SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            p[notifications]?:true,p[hour]?:8,p[minute]?:0,
            if(h!=null && s!=null) PinCredential(h,s,p[iterations]?:210_000) else null)
    }
    suspend fun read()=flow.first()
    suspend fun setLanguage(value:String) { require(value in setOf("uz","ru","en"));store.edit { it[language]=value } }
    suspend fun setTheme(value:ThemeMode) { store.edit { it[theme]=value.name } }
    suspend fun notifications(enabled:Boolean,h:Int,m:Int) {
        require(h in 0..23 && m in 0..59)
        store.edit { it[notifications]=enabled;it[hour]=h;it[minute]=m }
    }
    suspend fun setPin(credential:PinCredential?) { store.edit { p ->
        if(credential==null) { p.remove(hash);p.remove(salt);p.remove(iterations) }
        else { p[hash]=credential.hash;p[salt]=credential.salt;p[iterations]=credential.iterations }
    } }
    suspend fun reset() { store.edit { it.clear() } }
}
