package ru.ordercontrol.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import ru.ordercontrol.ui.order.OrderFields

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
                    // Время ручного заказа — момент ввода: от него отсчитываются
                    // варианты дня доставки.
                    OrderFields(
                        address = state.address,
                        onAddressChange = viewModel::updateAddress,
                        suggestions = state.suggestions,
                        onSuggestionSelect = viewModel::selectSuggestion,
                        bottles = state.bottles,
                        onBottlesChange = viewModel::updateBottles,
                        district = state.district,
                        districts = state.districts,
                        onDistrictSelect = viewModel::updateDistrict,
                        deliveryDay = state.deliveryDay,
                        onDeliveryDaySelect = viewModel::setDeliveryDay,
                        comment = state.comment,
                        onCommentChange = viewModel::updateComment,
                        at = System.currentTimeMillis(),
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
