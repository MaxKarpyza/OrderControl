package ru.ordercontrol.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.ordercontrol.domain.WorkSchedule

/** Аккаунт, в который пишутся новые контакты. `null` — локальная книга. */
data class ContactAccount(
    val name: String,
    val type: String,
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) : ScheduleProvider {

    private object Keys {
        val START_MINUTE = intPreferencesKey("work_start_minute")
        val END_MINUTE = intPreferencesKey("work_end_minute")
        val WORK_DAYS = stringSetPreferencesKey("work_days")
        val ACCOUNT_NAME = stringPreferencesKey("contact_account_name")
        val ACCOUNT_TYPE = stringPreferencesKey("contact_account_type")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val UPDATE_LAST_CHECK = longPreferencesKey("update_last_check")
        val UPDATE_DISMISSED_CODE = intPreferencesKey("update_dismissed_code")
    }

    val schedule: Flow<WorkSchedule> = context.settingsDataStore.data.map { it.toSchedule() }

    override suspend fun currentSchedule(): WorkSchedule =
        context.settingsDataStore.data.first().toSchedule()

    suspend fun setSchedule(schedule: WorkSchedule) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.START_MINUTE] = schedule.startMinute
            prefs[Keys.END_MINUTE] = schedule.endMinute
            prefs[Keys.WORK_DAYS] = schedule.days.map { it.name }.toSet()
        }
    }

    val onboardingDone: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun isOnboardingDone(): Boolean =
        context.settingsDataStore.data.first()[Keys.ONBOARDING_DONE] ?: false

    suspend fun setOnboardingDone() {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    /** Пора ли автоматически проверить обновление. */
    suspend fun isUpdateCheckDue(now: Long = System.currentTimeMillis()): Boolean {
        val last = context.settingsDataStore.data.first()[Keys.UPDATE_LAST_CHECK] ?: 0L
        return now - last >= UPDATE_CHECK_INTERVAL_MS
    }

    suspend fun markUpdateChecked(at: Long = System.currentTimeMillis()) {
        context.settingsDataStore.edit { it[Keys.UPDATE_LAST_CHECK] = at }
    }

    /** Пользователь скрыл плашку этой версии — больше не показываем. */
    suspend fun isUpdateDismissed(versionCode: Int): Boolean =
        (context.settingsDataStore.data.first()[Keys.UPDATE_DISMISSED_CODE] ?: 0) >= versionCode

    suspend fun dismissUpdate(versionCode: Int) {
        context.settingsDataStore.edit { it[Keys.UPDATE_DISMISSED_CODE] = versionCode }
    }

    val contactAccount: Flow<ContactAccount?> = context.settingsDataStore.data
        .map { it.toContactAccount() }

    suspend fun currentContactAccount(): ContactAccount? =
        context.settingsDataStore.data.first().toContactAccount()

    suspend fun setContactAccount(account: ContactAccount?) {
        context.settingsDataStore.edit { prefs ->
            if (account == null) {
                prefs.remove(Keys.ACCOUNT_NAME)
                prefs.remove(Keys.ACCOUNT_TYPE)
            } else {
                prefs[Keys.ACCOUNT_NAME] = account.name
                prefs[Keys.ACCOUNT_TYPE] = account.type
            }
        }
    }

    private fun Preferences.toSchedule(): WorkSchedule {
        val start = this[Keys.START_MINUTE] ?: WorkSchedule.DEFAULT_START_MINUTE
        val end = this[Keys.END_MINUTE] ?: WorkSchedule.DEFAULT_END_MINUTE
        val days = this[Keys.WORK_DAYS]
            ?.mapNotNull { name -> runCatching { DayOfWeek.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: WorkSchedule.DEFAULT_DAYS
        return WorkSchedule(startMinute = start, endMinute = end, days = days)
    }

    private fun Preferences.toContactAccount(): ContactAccount? {
        val name = this[Keys.ACCOUNT_NAME] ?: return null
        val type = this[Keys.ACCOUNT_TYPE] ?: return null
        return ContactAccount(name, type)
    }

    companion object {
        /** Автопроверка обновлений — не чаще двух раз в сутки. */
        private const val UPDATE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
