package ru.ordercontrol.ui.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.ordercontrol.R
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.domain.BlacklistKind
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.ui.CallStatusBadge
import ru.ordercontrol.ui.DisplayFormat

@Composable
fun OrderFormScreen(recordId: Long, onClose: () -> Unit) {
    val viewModel: FormViewModel = viewModel(factory = FormViewModel.factory(recordId))
    val state = viewModel.state
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    val requestClose = {
        if (viewModel.hasUnsavedInput) showDiscardDialog = true else onClose()
    }

    LaunchedEffect(state.done) {
        if (state.done) onClose()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.form_discard_title)) },
            text = { Text(stringResource(R.string.form_discard_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onClose()
                    },
                ) {
                    Text(stringResource(R.string.form_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.form_discard_cancel))
                }
            },
        )
    }

    // Свой фон, а не фон окна: в тёмной теме окно остаётся светлым, и форма
    // выглядела бы светлой. Material-фон всегда совпадает с темой.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.record == null -> NoRecords(onClose = onClose)

            else -> FormContent(
                state = state,
                nameFieldVisible = viewModel.nameFieldVisible,
                onBottlesChange = viewModel::updateBottles,
                onAddressChange = viewModel::updateAddress,
                onCommentChange = viewModel::updateComment,
                onWithPhoneChange = viewModel::setWithPhoneInComment,
                onNameChange = viewModel::updateName,
                onSkipEnabledChange = viewModel::setSkipEnabled,
                onSkipKindChange = viewModel::setSkipKind,
                onDeliveryDayChange = viewModel::setDeliveryDay,
                onPrevious = viewModel::previous,
                onAddAnotherOrder = viewModel::addAnotherOrder,
                onSave = viewModel::save,
                onClose = requestClose,
            )
        }
    }
}

@Composable
private fun FormContent(
    state: FormUiState,
    nameFieldVisible: Boolean,
    onBottlesChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onWithPhoneChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onSkipEnabledChange: (Boolean) -> Unit,
    onSkipKindChange: (BlacklistKind) -> Unit,
    onDeliveryDayChange: (DeliveryDay?) -> Unit,
    onPrevious: () -> Unit,
    onAddAnotherOrder: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val record = state.record ?: return

    // Один источник отступов: safeDrawing уже включает IME (через объединение,
    // а не сумму), поэтому отдельный imePadding не нужен и ничего не дёргает.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title(state.knownName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val callTime = stringResource(R.string.form_time)
                    val phone = record.phoneNormalized
                    val timeText = "$callTime: ${DisplayFormat.callTime(record.incomingAt)}"
                    Text(
                        text = if (phone != null && state.knownName != null) {
                            "${DisplayFormat.phone(phone)} · $timeText"
                        } else {
                            timeText
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CallStatusBadge(
                    answered = record.isAnswered,
                    modifier = Modifier.padding(end = 4.dp),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }

            HorizontalDivider()

            QuickBottles(
                selected = state.bottles.trim(),
                onSelect = onBottlesChange,
            )

            OutlinedTextField(
                value = state.bottles,
                onValueChange = onBottlesChange,
                label = { Text(stringResource(R.string.form_bottles)) },
                placeholder = { Text(stringResource(R.string.form_bottles_hint)) },
                singleLine = true,
                // Без автозамены у клавиатуры нет полосы подсказок: её высота
                // не меняется после привязки и не дёргает форму.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )

            DeliveryDayPicker(
                options = DeliveryDay.optionsAt(record.incomingAt),
                selected = state.deliveryDay,
                onSelect = onDeliveryDayChange,
            )

            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChange,
                label = { Text(stringResource(R.string.form_address)) },
                placeholder = { Text(stringResource(R.string.form_address_hint)) },
                supportingText = if (state.knownName != null && state.address.isBlank()) {
                    { Text(stringResource(R.string.form_address_from_contact, state.knownName)) }
                } else {
                    null
                },
                minLines = 1,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.comment,
                onValueChange = onCommentChange,
                label = { Text(stringResource(R.string.form_comment)) },
                placeholder = { Text(stringResource(R.string.form_comment_hint)) },
                minLines = 1,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )

            if (record.phoneNormalized != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.form_phone_needed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.form_phone_needed_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.withPhoneInComment,
                        onCheckedChange = onWithPhoneChange,
                    )
                }
            }

            // Клиент заказывает на два адреса: сохраняем этот заказ и открываем
            // вторую карточку, чтобы адрес не потерялся.
            OutlinedButton(
                onClick = onAddAnotherOrder,
                enabled = !state.saving &&
                    (state.address.isNotBlank() || state.bottles.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.form_another_address))
            }

            if (nameFieldVisible) {
                OutlinedTextField(
                    value = state.nameInput,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.form_name)) },
                    placeholder = { Text(stringResource(R.string.form_name_hint)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.form_name_note)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.form_skip),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.skipEnabled,
                    onCheckedChange = onSkipEnabledChange,
                )
            }

            if (state.skipEnabled) {
                SkipOption(
                    title = stringResource(R.string.form_skip_permanent),
                    selected = state.skipKind == BlacklistKind.PERMANENT,
                    onClick = { onSkipKindChange(BlacklistKind.PERMANENT) },
                )
                SkipOption(
                    title = stringResource(R.string.form_skip_once),
                    selected = state.skipKind == BlacklistKind.ONCE,
                    onClick = { onSkipKindChange(BlacklistKind.ONCE) },
                )
            }

            OutlinedButton(
                onClick = onPrevious,
                enabled = state.previous != null && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.form_previous))
            }
        }

        // Ошибки и сохранение закреплены внизу: видны без прокрутки и всегда
        // остаются над клавиатурой.
        if (state.contactFailed) {
            Text(
                text = stringResource(R.string.form_contact_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        if (state.error) {
            Text(
                text = stringResource(R.string.form_save_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider()

        Button(
            onClick = onSave,
            enabled = !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.form_save))
        }
    }
}

/**
 * Быстрый выбор количества: 1–5 одной кнопкой. Своё значение оператор
 * по-прежнему вводит в поле выше.
 */
@Composable
private fun QuickBottles(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (count in 1..5) {
            val value = count.toString()
            val active = selected == value
            OutlinedButton(
                onClick = { onSelect(value) },
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.weight(1f),
                colors = if (active) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeliveryDayPicker(    options: List<DeliveryDay>,
    selected: DeliveryDay?,
    onSelect: (DeliveryDay?) -> Unit,
) {
    Text(
        text = stringResource(R.string.form_delivery_day),
        style = MaterialTheme.typography.bodyMedium,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, day ->
            SegmentedButton(
                selected = selected == day,
                onClick = { onSelect(if (selected == day) null else day) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
                label = {
                    Text(
                        text = stringResource(day.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

private fun DeliveryDay.labelRes(): Int = when (this) {
    DeliveryDay.TOMORROW -> R.string.delivery_tomorrow
    DeliveryDay.DAY_AFTER_TOMORROW -> R.string.delivery_day_after_tomorrow
    DeliveryDay.MONDAY -> R.string.delivery_monday
    DeliveryDay.OTHER_DAY -> R.string.delivery_other_day
}

@Composable
private fun SkipOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NoRecords(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.form_no_records),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.form_no_records_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClose) {
                Text(stringResource(R.string.form_close))
            }
        }
    }
}

@Composable
private fun CallRecord.title(knownName: String?): String =
    knownName ?: displayName ?: phoneNormalized ?: stringResource(R.string.hidden_number)
