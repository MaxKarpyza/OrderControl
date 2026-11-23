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
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ordercontrol.domain.UnprocessedSort
import ru.ordercontrol.domain.WorkSchedule

/** Аккаунт, в который пишутся новые контакты. `null` — локальная книга. */
data class ContactAccount(
    val name: String,
    val type: String,
)

/**
 * Оператор этого телефона в общей истории.
 *
 * @property id постоянный идентификатор вида `опер_37-A3F`: по нему строки,
 *   которые вернулись из таблицы, отличаются от чужих.
 * @property name ФИО из настроек; `null` — оператор себя не назвал.
 */
data class Operator(
    val id: String,
    val name: String?,
) {
    /** Подпись в таблице: ФИО, если введено, иначе техническое имя. */
    val displayName: String
        get() = name?.trim()?.takeIf { it.isNotEmpty() } ?: id
}

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) : ScheduleProvider, OperatorProvider {

    private object Keys {
        val START_MINUTE = intPreferencesKey("work_start_minute")
        val END_MINUTE = intPreferencesKey("work_end_minute")
        val WORK_DAYS = stringSetPreferencesKey("work_days")
        val ACCOUNT_NAME = stringPreferencesKey("contact_account_name")
        val ACCOUNT_TYPE = stringPreferencesKey("contact_account_type")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val UPDATE_LAST_CHECK = longPreferencesKey("update_last_check")
        val UPDATE_DISMISSED_CODE = intPreferencesKey("update_dismissed_code")
        val ADDRESS_BASE_LAST_REFRESH = longPreferencesKey("address_base_last_refresh")
        val ADDRESS_BASE_DISTRICTS = stringSetPreferencesKey("address_base_districts")
        val SHEET_FETCH_COUNT = intPreferencesKey("sheet_fetch_count")
        val OPERATOR_ID = stringPreferencesKey("operator_id")
        val OPERATOR_NAME = stringPreferencesKey("operator_name")
        val UNPROCESSED_SORT = stringPreferencesKey("unprocessed_sort")
    }

    private val operatorMutex = Mutex()

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

    /**
     * Кто обрабатывает заказы на этом телефоне: постоянный идентификатор и
     * ФИО, если оператор его ввёл.
     */
    override suspend fun operator(): Operator = Operator(id = operatorId(), name = operatorName())

    /** Имя, отчество из настроек; `null` — не введено. */
    suspend fun operatorName(): String? = context.settingsDataStore.data.first()[Keys.OPERATOR_NAME]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    suspend fun setOperatorName(name: String?) {
        val text = name?.trim().orEmpty()
        context.settingsDataStore.edit { prefs ->
            if (text.isEmpty()) {
                prefs.remove(Keys.OPERATOR_NAME)
            } else {
                prefs[Keys.OPERATOR_NAME] = text
            }
        }
    }

    /**
     * Постоянный идентификатор телефона в общей истории: `опер_37-A3F`.
     *
     * Номер случайный двузначный, код — три символа из идентификатора
     * устройства. Код и делает имя уникальным: два телефона с одинаковым
     * номером всё равно различаются. Генерируется один раз и больше не
     * меняется — иначе строки в таблице перестали бы считаться своими.
     */
    private suspend fun operatorId(): String = operatorMutex.withLock {
        val stored = context.settingsDataStore.data.first()[Keys.OPERATOR_ID]
        if (!stored.isNullOrBlank()) return@withLock stored

        val generated = buildOperatorId()
        context.settingsDataStore.edit { it[Keys.OPERATOR_ID] = generated }
        generated
    }

    private fun buildOperatorId(): String {
        val androidId = runCatching {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            )
        }.getOrNull()

        // Идентификатор устройства есть на всех версиях Android, но если
        // прошивка его не отдала — хватит случайной строки: она всё равно
        // сохранится навсегда.
        val source = androidId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val code = (source.hashCode().toLong() and 0xFFFFFF)
            .toString(16)
            .uppercase()
            .padStart(6, '0')
            .takeLast(3)
        return "опер_${Random.nextInt(10, 100)}-$code"
    }

    /** Когда базу адресов обновляли в последний раз. */
    suspend fun addressBaseLastRefresh(): Long =
        context.settingsDataStore.data.first()[Keys.ADDRESS_BASE_LAST_REFRESH] ?: 0L

    suspend fun markAddressBaseRefreshed(at: Long = System.currentTimeMillis()) {
        context.settingsDataStore.edit { it[Keys.ADDRESS_BASE_LAST_REFRESH] = at }
    }

    /**
     * Пора ли обновлять базу адресов.
     *
     * На десятое скачивание таблицы — за это время в таблице могли поправить
     * адреса, — и не реже раза в сутки.
     */
    suspend fun isAddressBaseRefreshDue(now: Long = System.currentTimeMillis()): Boolean =
        isRefreshDue(
            fetchCount = sheetFetchCount(),
            lastRefreshAt = addressBaseLastRefresh(),
            now = now,
        )

    /** Сколько раз таблицу скачивали с последнего обновления базы. */
    suspend fun sheetFetchCount(): Int =
        context.settingsDataStore.data.first()[Keys.SHEET_FETCH_COUNT] ?: 0

    /** Таблицу скачали: счётчик растёт. */
    suspend fun markTableFetched() {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SHEET_FETCH_COUNT] = (prefs[Keys.SHEET_FETCH_COUNT] ?: 0) + 1
        }
    }

    /** База обновилась: счётчик скачиваний сбрасывается. */
    suspend fun resetSheetFetchCount() {
        context.settingsDataStore.edit { it.remove(Keys.SHEET_FETCH_COUNT) }
    }

    /** Районы, которые приложение уже научилось отличать от комментариев. */
    suspend fun addressBaseDistricts(): Set<String> =
        context.settingsDataStore.data.first()[Keys.ADDRESS_BASE_DISTRICTS].orEmpty()

    suspend fun setAddressBaseDistricts(districts: Set<String>) {
        context.settingsDataStore.edit { it[Keys.ADDRESS_BASE_DISTRICTS] = districts }
    }

    /** Порядок записей на вкладке «Незаписанные». */
    val unprocessedSort: Flow<UnprocessedSort> = context.settingsDataStore.data
        .map { UnprocessedSort.fromKey(it[Keys.UNPROCESSED_SORT]) }

    suspend fun currentUnprocessedSort(): UnprocessedSort =
        UnprocessedSort.fromKey(context.settingsDataStore.data.first()[Keys.UNPROCESSED_SORT])

    suspend fun setUnprocessedSort(sort: UnprocessedSort) {
        context.settingsDataStore.edit { it[Keys.UNPROCESSED_SORT] = sort.name }
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
