package ru.ordercontrol.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.ordercontrol.R
import ru.ordercontrol.data.SheetWriteOutcome
import ru.ordercontrol.data.SheetWriteProgress

/** Сколько адресов и проблем показывать в итоге записи списком. */
private const val MAX_LISTED_ISSUES = 5

/** Идёт запись заказов в таблицу: прогресс и остановка. */
@Composable
internal fun WriteProgressDialog(
    progress: SheetWriteProgress?,
    onStop: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.write_dialog_title)) },
        text = {
            val total = progress?.total ?: 0
            val done = progress?.done ?: 0
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = if (progress == null || total == 0) {
                        stringResource(R.string.write_verifying)
                    } else if (done >= total) {
                        stringResource(R.string.write_verifying)
                    } else {
                        stringResource(R.string.write_progress, done + 1, total)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val address = progress?.address.orEmpty()
                if (address.isNotBlank()) {
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onStop) {
                Text(stringResource(R.string.write_stop))
            }
        },
    )
}

/** Итог записи: сколько записано, подтверждено и что пошло не так. */
@Composable
internal fun WriteResultDialog(
    outcome: SheetWriteOutcome?,
    error: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.write_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (error != null) {
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }
                if (outcome != null) {
                    if (outcome.total == 0) {
                        Text(stringResource(R.string.write_result_no_orders))
                    } else {
                        Text(
                            text = stringResource(
                                R.string.write_result,
                                outcome.written,
                                outcome.confirmed,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (outcome.duplicates > 0) {
                            Text(
                                text = stringResource(
                                    R.string.write_result_duplicates,
                                    outcome.duplicates,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (outcome.skipped > 0) {
                            Text(
                                text = stringResource(R.string.write_result_skipped, outcome.skipped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (outcome.notConfirmed.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.write_result_not_confirmed,
                                    outcome.notConfirmed.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            outcome.notConfirmed.take(MAX_LISTED_ISSUES).forEach { address ->
                                Text(
                                    text = "• $address",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        outcome.errors.forEach { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
