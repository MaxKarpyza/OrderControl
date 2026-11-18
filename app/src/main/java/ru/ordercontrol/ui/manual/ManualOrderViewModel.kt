package ru.ordercontrol.ui.manual

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.data.db.AddressBaseEntry
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.ui.order.BaseAutoFill
import ru.ordercontrol.ui.order.OrderAddressLookup
import ru.ordercontrol.ui.order.districtOptions
import ru.ordercontrol.widget.WidgetRefresh

/** Состояние карточки ручного ввода заказа. */
data class ManualOrderUiState(
    val address: String = "",
    val bottles: String = "",
    val district: String = "",
    val comment: String = "",
    val deliveryDay: DeliveryDay? = null,
    val suggestions: List<AddressBaseEntry> = emptyList(),
    /** Районы для выпадающего списка. */
    val districts: List<String> = emptyList(),
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

    /** Подстановка района и количества из базы адресов. */
    private val baseFill = BaseAutoFill()

    /** Поиск адреса в базе адресов: подсказки и точное совпадение. */
    private val lookup = OrderAddressLookup(
        scope = viewModelScope,
        onSuggestions = suggestions@{ text, list ->
            if (state.address != text) return@suggestions
            state = state.copy(suggestions = list)
        },
        onBase = base@{ text, entry ->
            if (state.address != text) return@base
            applyBase(entry)
        },
    )

    init {
        // Список районов не зависит от введённого заказа: читаем один раз,
        // чтобы не трогать настройки при каждом обновлении состояния.
        viewModelScope.launch {
            val districts = districtOptions(
                AppGraph.settings.addressBaseDistricts(),
                AppGraph.addressBase.districtUsage(),
            )
            state = state.copy(districts = districts)
        }
    }

    /**
     * Очищает карточку перед новым вводом.
     *
     * Карточка живёт дольше одного открытия, поэтому без сброса следующий
     * заказ начинался бы с адреса прошлого.
     */
    fun reset() {
        lookup.cancel()
        baseFill.reset()
        // Список районов не сбрасываем: он не относится к введённому заказу.
        state = ManualOrderUiState(districts = state.districts)
    }

    /** Событие «заказ сохранён» читается один раз. */
    fun consumeSaved() {
        state = state.copy(saved = false)
    }

    fun updateAddress(value: String) {
        state = state.copy(address = value, error = false)
        lookup.lookup(value)
    }

    /** Оператор выбрал адрес из подсказок. */
    fun selectSuggestion(entry: AddressBaseEntry) {
        state = state.copy(address = entry.address, suggestions = emptyList())
        lookup.select(entry)
    }

    fun updateBottles(value: String) {
        state = state.copy(bottles = value, error = false)
    }

    fun updateDistrict(value: String) {
        // Район поправили руками — больше не подставляемый, а свой.
        baseFill.onDistrictEdited()
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
        val (district, bottles) = baseFill.apply(entry, state.district, state.bottles)
        state = state.copy(district = district, bottles = bottles)
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
}
