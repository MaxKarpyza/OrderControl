package ru.ordercontrol.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ordercontrol.R

/**
 * Вопрос перед снятием отметки «пропущен».
 *
 * Отметку снимают нечаянно: звонок может быть пропущен по-настоящему, и
 * тогда заказ в таблицу попасть не должен.
 */
@Composable
internal fun MarkAnsweredDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_confirm_answered_title)) },
        text = { Text(stringResource(R.string.main_confirm_answered_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_no))
            }
        },
    )
}

/** Вопрос перед отметкой одной записи обработанной. */
@Composable
internal fun ProcessOneDialog(
    address: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_confirm_one_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.main_confirm_one_text))
                if (address.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.main_confirm_one_address, address),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_no))
            }
        },
    )
}

/**
 * Вопрос перед отметкой всех записей обработанными: подтверждение
 * защищает от случайного нажатия — действие закрывает весь список.
 */
@Composable
internal fun ProcessAllDialog(
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_confirm_processed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.main_confirm_processed_text))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfirmedChange(!confirmed) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = onConfirmedChange,
                    )
                    Text(stringResource(R.string.main_confirm_processed_checkbox))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmed,
            ) {
                Text(stringResource(R.string.action_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_no))
            }
        },
    )
}
