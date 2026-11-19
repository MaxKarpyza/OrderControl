package ru.ordercontrol.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ru.ordercontrol.R
import ru.ordercontrol.domain.AmbiguousMatch

/** Итог сверки необработанных записей с Google Таблицей. */
@Composable
internal fun SheetCheckDialog(
    message: String,
    hint: String?,
    bottleMismatchCount: Int,
    ambiguous: List<AmbiguousMatch>,
    onConfirmAlias: (AmbiguousMatch) -> Unit,
    onRejectAlias: (AmbiguousMatch) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.sheet_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (bottleMismatchCount > 0) {
                    Text(
                        text = stringResource(R.string.sheet_bottles_mismatch, bottleMismatchCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (ambiguous.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.sheet_ambiguous_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    ambiguous.forEach { match ->
                        AmbiguousItem(
                            match = match,
                            onConfirm = { onConfirmAlias(match) },
                            onReject = { onRejectAlias(match) },
                        )
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

/** Неполное совпадение адресов: оператор решает, один это адрес или разные. */
@Composable
private fun AmbiguousItem(
    match: AmbiguousMatch,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.sheet_record_address, match.recordAddress),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.sheet_row_address, match.row.address),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.sheet_same_address))
            }
            TextButton(onClick = onReject) {
                Text(stringResource(R.string.sheet_other_address))
            }
        }
    }
}
