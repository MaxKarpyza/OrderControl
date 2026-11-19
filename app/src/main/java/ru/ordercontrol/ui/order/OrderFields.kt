package ru.ordercontrol.ui.order

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.ordercontrol.R
import ru.ordercontrol.data.db.AddressBaseEntry
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.ui.form.DeliveryDayPicker
import ru.ordercontrol.ui.form.QuickBottles

/**
 * Общий набор полей заказа: адрес с подсказками из базы адресов, количество
 * бутылей, район, день доставки и комментарий.
 *
 * Один и тот же набор работает и в форме заказа по телефону, и в карточке
 * ручного ввода — подписи и поведение полей совпадают. [at] — момент звонка
 * (для ручного заказа момент ввода): от него отсчитываются варианты дня
 * доставки.
 */
@Composable
fun OrderFields(
    address: String,
    onAddressChange: (String) -> Unit,
    suggestions: List<AddressBaseEntry>,
    onSuggestionSelect: (AddressBaseEntry) -> Unit,
    bottles: String,
    onBottlesChange: (String) -> Unit,
    district: String,
    districts: List<String>,
    onDistrictSelect: (String) -> Unit,
    deliveryDay: DeliveryDay?,
    onDeliveryDaySelect: (DeliveryDay?) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    at: Long,
    addressSupportingText: (@Composable () -> Unit)? = null,
) {
    OrderAddressField(
        value = address,
        suggestions = suggestions,
        onValueChange = onAddressChange,
        onSelect = onSuggestionSelect,
        supportingText = addressSupportingText,
    )

    QuickBottles(
        selected = bottles.trim(),
        onSelect = onBottlesChange,
    )

    OutlinedTextField(
        value = bottles,
        onValueChange = onBottlesChange,
        label = { Text(stringResource(R.string.form_bottles)) },
        placeholder = { Text(stringResource(R.string.form_bottles_hint)) },
        singleLine = true,
        // Без автозамены у клавиатуры нет полосы подсказок: её высота
        // не меняется после привязки и не дёргает форму.
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )

    DistrictField(
        value = district,
        districts = districts,
        onSelect = onDistrictSelect,
    )

    DeliveryDayPicker(
        options = DeliveryDay.optionsAt(at),
        selected = deliveryDay,
        onSelect = onDeliveryDaySelect,
    )

    OrderDeliveryHint(
        day = deliveryDay,
        at = at,
    )

    OutlinedTextField(
        value = comment,
        onValueChange = onCommentChange,
        label = { Text(stringResource(R.string.form_comment)) },
        placeholder = { Text(stringResource(R.string.form_comment_hint)) },
        minLines = 1,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
