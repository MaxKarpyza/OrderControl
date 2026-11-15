package ru.ordercontrol.ui.manual

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.data.db.AddressBaseEntry
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.widget.WidgetRefresh

/** Состояние карточки ручного ввода заказа. */
data class ManualOrderUiState(
    val address: String = "",
    val bottles: String = "",
    val district: String = "",
    val comment: String = "",
    val deliveryDay: DeliveryDay? = null,
    val suggestions: List<AddressBaseEntry> = emptyList(),
    val saving: Boolean = false,
    /** Заказ сохранён: событие, которое карточка читает и сразу сбрасывает. */
    val saved: Boolean = false,
    val error: Boolean = false,
)

/**
 * Карточка ручного ввода заказа.
 *
 * Адрес подсказывается из базы адресов: вместе с адресом подставляются
 * район и количество бутылей из последнего заказа по этому адресу.
 */
class ManualOrderViewModel(application: Application) : AndroidViewModel(application) {

    var state by mutableStateOf(ManualOrderUiState())
        private set

    /** Район, подставленный из базы: пока его не правили вручную, он обновляется. */
    private var autoDistrict: String? = null

    private var lookupJob: Job? = null

    /**
     * Очищает карточку перед новым вводом.
     *
     * Карточка живёт дольше одного открытия, поэтому без сброса следующий
     * заказ начинался бы с адреса прошлого.
     */
    fun reset() {
        lookupJob?.cancel()
        autoDistrict = null
        state = ManualOrderUiState()
    }

    /** Событие «заказ сохранён» читается один раз. */
    fun consumeSaved() {
        state = state.copy(saved = false)
    }

    fun updateAddress(value: String) {
        state = state.copy(address = value, error = false)
        lookupJob?.cancel()
        if (value.isBlank()) {
            state = state.copy(suggestions = emptyList())
            return
        }
        lookupJob = viewModelScope.launch {
            // Ждём, пока оператор допечатает: иначе ищем по каждой букве.
            delay(LOOKUP_DELAY_MS)
            val suggestions = AppGraph.addressBase.search(value)
            // Пока искали, текст мог измениться: показываем подсказки только
            // для того, что реально напечатано.
            if (state.address != value) return@launch
            state = state.copy(suggestions = suggestions)
            applyBase(AppGraph.addressBase.find(value))
        }
    }

    /** Оператор выбрал адрес из подсказок. */
    fun selectSuggestion(entry: AddressBaseEntry) {
        // Отменяем поиск: иначе он вернёт подсказки по старому запросу и
        // список снова вылезет поверх ввода.
        lookupJob?.cancel()
        state = state.copy(address = entry.address, suggestions = emptyList())
        applyBase(entry)
    }

    fun updateBottles(value: String) {
        state = state.copy(bottles = value, error = false)
    }

    fun updateDistrict(value: String) {
        // Район поправили руками — больше не подставляемый, а свой.
        autoDistrict = null
        state = state.copy(district = value)
    }

    fun updateComment(value: String) {
        state = state.copy(comment = value)
    }

    fun setDeliveryDay(day: DeliveryDay?) {
        state = state.copy(deliveryDay = day)
    }

    /**
     * Подставляет район и количество из базы адресов.
     *
     * Количество заменяется, только если оператор его не вводил: свой
     * выбор он вправе оставить. Район обновляется, пока он не правлен руками.
     */
    private fun applyBase(entry: AddressBaseEntry?) {
        if (entry == null) return

        val district = entry.district
        val current = state.district
        val newDistrict = if (district != null && (current.isBlank() || current == autoDistrict)) {
            autoDistrict = district
            district
        } else {
            current
        }

        val newBottles = if (state.bottles.isBlank()) {
            entry.bottles?.toString().orEmpty()
        } else {
            state.bottles
        }

        state = state.copy(district = newDistrict, bottles = newBottles)
    }

    /**
     * Сохраняет заказ.
     *
     * Без адреса заказ не создаётся: оператор должен указать, куда везти.
     */
    fun save() {
        val address = state.address.trim()
        if (address.isEmpty() || state.saving) return
        state = state.copy(saving = true, error = false)
        viewModelScope.launch {
            try {
                AppGraph.calls.createManualOrder(
                    address = address,
                    bottles = state.bottles,
                    comment = state.comment,
                    district = state.district,
                    deliveryDay = state.deliveryDay,
                )
                WidgetRefresh.updateAll(getApplication())
                state = state.copy(saving = false, saved = true)
            } catch (_: Exception) {
                state = state.copy(saving = false, error = true)
            }
        }
    }

    private companion object {
        /** Задержка перед поиском подсказок: жмём букву — ищем по целому слову. */
        const val LOOKUP_DELAY_MS = 250L
    }
}
