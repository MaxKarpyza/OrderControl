package ru.ordercontrol.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import ru.ordercontrol.R
import ru.ordercontrol.data.db.AddressBaseEntry
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.domain.SheetTarget
import ru.ordercontrol.ui.form.DeliveryDayPicker
import ru.ordercontrol.ui.form.QuickBottles

/**
 * Карточка ручного ввода заказа: адрес с подсказками из базы адресов,
 * количество бутылей, район, день доставки и комментарий.
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
                    AddressField(
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

                    OutlinedTextField(
                        value = state.district,
                        onValueChange = viewModel::updateDistrict,
                        label = { Text(stringResource(R.string.manual_district)) },
                        placeholder = { Text(stringResource(R.string.manual_district_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    DeliveryDayPicker(
                        options = DeliveryDay.optionsAt(System.currentTimeMillis()),
                        selected = state.deliveryDay,
                        onSelect = viewModel::setDeliveryDay,
                    )

                    Text(
                        text = deliveryHint(state.deliveryDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * Поле адреса с подсказками из базы адресов.
 *
 * В подсказке видно район и количество бутылей из последнего заказа:
 * оператор выбирает адрес и сразу видит, что заказ повторный.
 */
@Composable
private fun AddressField(
    value: String,
    suggestions: List<AddressBaseEntry>,
    onValueChange: (String) -> Unit,
    onSelect: (AddressBaseEntry) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.form_address)) },
            placeholder = { Text(stringResource(R.string.form_address_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )

        DropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEach { entry ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = entry.address,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val details = listOfNotNull(
                                entry.district,
                                entry.bottles?.let {
                                    stringResource(R.string.manual_bottles_short, it)
                                },
                            ).joinToString(" · ")
                            if (details.isNotEmpty()) {
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(entry)
                    },
                )
            }
        }
    }
}

/**
 * Что будет считаться днём доставки.
 *
 * Неотмеченный день — это сегодня, а по воскресеньям доставки нет:
 * заказ уезжает в понедельник.
 */
@Composable
private fun deliveryHint(day: DeliveryDay?): String {
    val date = SheetTarget.date(System.currentTimeMillis(), day)
    val weekday = stringResource(dayOfWeekRes(date.dayOfWeek))
    return stringResource(
        R.string.manual_delivery_hint,
        "${date.format(DATE_FORMAT)} ($weekday)",
    )
}

private fun dayOfWeekRes(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.day_monday_short
    DayOfWeek.TUESDAY -> R.string.day_tuesday_short
    DayOfWeek.WEDNESDAY -> R.string.day_wednesday_short
    DayOfWeek.THURSDAY -> R.string.day_thursday_short
    DayOfWeek.FRIDAY -> R.string.day_friday_short
    DayOfWeek.SATURDAY -> R.string.day_saturday_short
    DayOfWeek.SUNDAY -> R.string.day_sunday_short
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
