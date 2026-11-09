package ru.ordercontrol.ui.form

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.domain.BlacklistKind
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.domain.SkipUpdate
import ru.ordercontrol.widget.WidgetRefresh

data class FormUiState(
    val loading: Boolean = true,
    val record: CallRecord? = null,
    val bottles: String = "",
    val address: String = "",
    val nameInput: String = "",
    val knownName: String? = null,
    val skipEnabled: Boolean = false,
    val skipKind: BlacklistKind = BlacklistKind.PERMANENT,
    val deliveryDay: DeliveryDay? = null,
    val previous: CallRecord? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: Boolean = false,
    val contactFailed: Boolean = false,
)

class FormViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
    initialRecordId: Long,
) : AndroidViewModel(application) {

    var state by mutableStateOf(FormUiState())
        private set

    private var recordId: Long = initialRecordId

    /** Была ли запись помечена «не клиент» до открытия формы. */
    private var initiallySkipped: Boolean = false

    val nameFieldVisible: Boolean
        get() = state.record?.phoneNormalized != null && state.knownName.isNullOrBlank()

    val hasUnsavedInput: Boolean
        get() {
            val record = state.record ?: return false
            if (state.done || state.saving) return false
            // Сравниваем с сохранённой записью, иначе при правке уже
            // заполненного заказа закрытие всегда спрашивало бы о потере.
            return state.bottles.trim() != record.bottlesText.trim() ||
                state.address.trim() != record.addressText.trim() ||
                state.nameInput.isNotBlank() ||
                state.deliveryDay != record.deliveryDay ||
                state.skipEnabled != initiallySkipped
        }

    init {
        savedState.get<Long>(KEY_RECORD_ID)?.let { recordId = it }
        load(recordId)
    }

    fun previous() {
        val target = state.previous ?: return
        if (state.saving) return
        val current = state.record ?: return
        state = state.copy(saving = true, error = false, contactFailed = false)
        viewModelScope.launch {
            try {
                saveOrder(current)
                load(target.id)
            } catch (_: Exception) {
                // Без сохранения текущей карточки на неё нельзя переключиться:
                // введённые данные пропали бы.
                state = state.copy(saving = false, error = true)
            }
        }
    }

    fun updateBottles(value: String) {
        savedState[KEY_BOTTLES] = value
        state = state.copy(bottles = value, error = false)
    }

    fun updateAddress(value: String) {
        savedState[KEY_ADDRESS] = value
        state = state.copy(address = value, error = false)
    }

    fun updateName(value: String) {
        savedState[KEY_NAME] = value
        state = state.copy(nameInput = value, error = false)
    }

    fun setSkipEnabled(enabled: Boolean) {
        savedState[KEY_SKIP_ENABLED] = enabled
        state = state.copy(skipEnabled = enabled)
    }

    fun setSkipKind(kind: BlacklistKind) {
        savedState[KEY_SKIP_KIND] = kind.name
        state = state.copy(skipKind = kind)
    }

    fun setDeliveryDay(day: DeliveryDay?) {
        savedState[KEY_DELIVERY_DAY] = day?.name ?: NO_DELIVERY_DAY
        state = state.copy(deliveryDay = day)
    }

    /**
     * Сохраняет текущую карточку и открывает вторую для того же клиента:
     * заказ на другой адрес. Введённый адрес остаётся в первой карточке,
     * вторая начинается пустой.
     */
    fun addAnotherOrder() {
        val record = state.record ?: return
        if (state.saving) return
        state = state.copy(saving = true, error = false, contactFailed = false)
        viewModelScope.launch {
            try {
                val contactFailed = saveOrder(record)
                val newId = AppGraph.calls.addAnotherOrder(record.id)
                if (newId == null) {
                    state = state.copy(saving = false, error = true)
                    return@launch
                }
                WidgetRefresh.updateAll(getApplication())
                clearDraft()
                state = state.copy(saving = false, contactFailed = contactFailed)
                load(newId)
            } catch (_: Exception) {
                state = state.copy(saving = false, error = true)
            }
        }
    }

    fun save() {
        val record = state.record ?: return
        if (state.saving) return
        state = state.copy(saving = true, error = false, contactFailed = false)
        viewModelScope.launch {
            try {
                val contactFailed = saveOrder(record)
                WidgetRefresh.updateAll(getApplication())
                // Ошибка контакта не повод терять заказ: просто закрываем.
                clearDraft()
                state = state.copy(
                    saving = false,
                    done = true,
                    contactFailed = contactFailed,
                )
            } catch (_: Exception) {
                state = state.copy(saving = false, error = true)
            }
        }
    }

    /**
     * Сохраняет введённое в запись. Пустой адрес хранится пустым: адресом
     * доставки тогда считается контакт ([CallRecord.deliveryAddress]).
     *
     * @return `true`, если имя введено, но контакт создать не удалось.
     */
    private suspend fun saveOrder(record: CallRecord): Boolean = AppGraph.calls.saveOrder(
        recordId = record.id,
        bottles = state.bottles,
        address = state.address,
        nameInput = if (nameFieldVisible) state.nameInput else null,
        skip = resolveSkipUpdate(),
        deliveryDay = state.deliveryDay,
    )

    private fun resolveSkipUpdate(): SkipUpdate = when {
        state.skipEnabled -> SkipUpdate.Set(state.skipKind)
        initiallySkipped -> SkipUpdate.Clear
        else -> SkipUpdate.Unchanged
    }

    /** Сбрасывает несохранённый ввод перед переходом к другой карточке. */
    private fun clearDraft() {
        savedState.remove<String>(KEY_BOTTLES)
        savedState.remove<String>(KEY_ADDRESS)
        savedState.remove<String>(KEY_NAME)
        savedState.remove<Boolean>(KEY_SKIP_ENABLED)
        savedState.remove<String>(KEY_SKIP_KIND)
        savedState.remove<String>(KEY_DELIVERY_DAY)
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            try {
                val record = if (id > 0) {
                    AppGraph.calls.recordById(id)
                } else {
                    awaitLatestOpen()
                }
                if (record == null) {
                    state = FormUiState(loading = false)
                    return@launch
                }
                recordId = record.id
                savedState[KEY_RECORD_ID] = record.id

                val knownName = record.displayName
                    ?: record.phoneNormalized?.let { AppGraph.contacts.findName(it) }
                val activeSkipKind = AppGraph.calls.skipKindFor(record)
                initiallySkipped = if (record.phoneNormalized == null) {
                    record.isSkipped
                } else {
                    record.isSkipped && activeSkipKind != null
                }

                // Несохранённый ввод переживает поворот и смерть процесса.
                state = FormUiState(
                    loading = false,
                    record = record,
                    bottles = savedState.get<String>(KEY_BOTTLES) ?: record.bottlesText,
                    // Поле адреса всегда начинается пустым: контакт — это
                    // адрес по умолчанию, но подставлять его в поле незачем.
                    address = savedState.get<String>(KEY_ADDRESS) ?: record.addressText,
                    nameInput = savedState.get<String>(KEY_NAME) ?: "",
                    knownName = knownName,
                    deliveryDay = when (val savedDay = savedState.get<String>(KEY_DELIVERY_DAY)) {
                        null -> record.deliveryDay
                        NO_DELIVERY_DAY -> null
                        else -> runCatching { DeliveryDay.valueOf(savedDay) }.getOrNull()
                    },
                    skipEnabled = savedState.get<Boolean>(KEY_SKIP_ENABLED) ?: initiallySkipped,
                    skipKind = savedState.get<String>(KEY_SKIP_KIND)
                        ?.let { name -> runCatching { BlacklistKind.valueOf(name) }.getOrNull() }
                        ?: activeSkipKind
                        ?: BlacklistKind.PERMANENT,
                    previous = AppGraph.calls.previousOpenToday(record),
                )
            } catch (_: Exception) {
                state = FormUiState(loading = false)
            }
        }
    }

    /**
     * Последняя необработанная запись. Если её ещё нет — ждём: запись о
     * звонке создаётся в ресивере параллельно, и оператор открывает форму
     * почти сразу после звонка.
     */
    private suspend fun awaitLatestOpen(): CallRecord? {
        val deadline = System.currentTimeMillis() + RECORD_WAIT_MS
        while (true) {
            AppGraph.calls.latestOpen()?.let { return it }
            if (System.currentTimeMillis() >= deadline) return null
            delay(RECORD_POLL_MS)
        }
    }

    companion object {
        private const val KEY_RECORD_ID = "record_id"
        private const val KEY_BOTTLES = "bottles"
        private const val KEY_ADDRESS = "address"
        private const val KEY_NAME = "name"
        private const val KEY_SKIP_ENABLED = "skip_enabled"
        private const val KEY_SKIP_KIND = "skip_kind"
        private const val KEY_DELIVERY_DAY = "delivery_day"

        /** Значение сохранённого выбора «день не отмечен». */
        private const val NO_DELIVERY_DAY = "NONE"

        /** Сколько ждать появления записи после звонка. */
        private const val RECORD_WAIT_MS = 6_000L
        private const val RECORD_POLL_MS = 250L

        fun factory(recordId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Application is not available")
                FormViewModel(
                    application = application,
                    savedState = createSavedStateHandle(),
                    initialRecordId = recordId,
                )
            }
        }
    }
}
