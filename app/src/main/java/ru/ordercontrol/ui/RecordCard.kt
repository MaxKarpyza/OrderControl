package ru.ordercontrol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.ordercontrol.R
import ru.ordercontrol.data.db.CallRecord

/** Заголовок карточки: имя контакта, номер или «Скрытый номер». */
@Composable
fun CallRecord.cardTitle(): String =
    displayName?.takeIf { it.isNotBlank() }
        ?: phoneNormalized?.let { DisplayFormat.phone(it) }
        ?: stringResource(R.string.hidden_number)

/** Строка «подпись — значение» в карточке. */
@Composable
fun InfoLine(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun RecordCardShell(
    record: CallRecord,
    headerTrailing: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val futureDelivery = record.deliveryDay != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (futureDelivery) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border = if (futureDelivery) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.cardTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (headerTrailing != null) {
                    Text(
                        text = headerTrailing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                headerAction?.invoke()
            }

            BadgeRow(
                skipped = record.isSkipped,
                silent = record.isSilent,
                deliveryDay = record.deliveryDay,
                answered = record.isAnswered,
            )

            InfoLine(
                label = stringResource(R.string.main_phone),
                value = record.phoneNormalized?.let { DisplayFormat.phone(it) }
                    ?: stringResource(R.string.main_phone_unknown),
            )
            InfoLine(
                label = stringResource(R.string.main_bottles),
                value = record.bottlesText.ifBlank { stringResource(R.string.main_not_filled) },
            )
            InfoLine(
                label = stringResource(R.string.main_address),
                value = record.deliveryAddress.ifBlank {
                    stringResource(R.string.main_not_filled)
                },
            )

            content()
        }
    }
}
