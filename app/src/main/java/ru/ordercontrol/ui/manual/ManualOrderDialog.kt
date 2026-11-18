package ru.ordercontrol.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.ordercontrol.R
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.ui.form.DeliveryDayPicker
import ru.ordercontrol.ui.form.QuickBottles
import ru.ordercontrol.ui.order.DistrictField
import ru.ordercontrol.ui.order.OrderAddressField
import ru.ordercontrol.ui.order.OrderDeliveryHint

/**
 * Карточка ручного ввода заказа: адрес с подсказками из базы адресов,
 * количество бутылей, район, день доставки и комментарий.
 *
 * Набор полей и их поведение общие с формой заказа по телефону —
 * см. [ru.ordercontrol.ui.order.OrderAddressField].
 */
@Composable
fun ManualOrderDialog(
    viewModel: ManualOrderViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val state = viewModel.state

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .fillMaxHeight(0.9f)
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.manual_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OrderAddressField(
                        value = state.address,
                        suggestions = state.suggestions,
                        onValueChange = viewModel::updateAddress,
                        onSelect = viewModel::selectSuggestion,
                    )

                    QuickBottles(
                        selected = state.bottles.trim(),
                        onSelect = viewModel::updateBottles,
                    )

                    OutlinedTextField(
                        value = state.bottles,
                        onValueChange = viewModel::updateBottles,
                        label = { Text(stringResource(R.string.form_bottles)) },
                        placeholder = { Text(stringResource(R.string.form_bottles_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    DistrictField(
                        value = state.district,
                        districts = state.districts,
                        onSelect = viewModel::updateDistrict,
                    )

                    DeliveryDayPicker(
                        options = DeliveryDay.optionsAt(System.currentTimeMillis()),
                        selected = state.deliveryDay,
                        onSelect = viewModel::setDeliveryDay,
                    )

                    OrderDeliveryHint(
                        day = state.deliveryDay,
                        at = System.currentTimeMillis(),
                    )

                    OutlinedTextField(
                        value = state.comment,
                        onValueChange = viewModel::updateComment,
                        label = { Text(stringResource(R.string.form_comment)) },
                        placeholder = { Text(stringResource(R.string.form_comment_hint)) },
                        minLines = 1,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.error) {
                    Text(
                        text = stringResource(R.string.manual_save_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = viewModel::save,
                    enabled = state.address.isNotBlank() && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.manual_save))
                }
            }
        }
    }
}
