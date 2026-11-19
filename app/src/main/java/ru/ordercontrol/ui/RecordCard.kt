package ru.ordercontrol.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.ordercontrol.R
import ru.ordercontrol.data.OpenCallItem
import ru.ordercontrol.data.db.CallRecord

/**
 * Заголовок карточки: имя контакта, номер или «Скрытый номер».
 *
 * У ручного заказа заголовком служит адрес: заказ ввели не по звонку,
 * контакта у него нет, а адрес — главное, что о нём нужно видеть.
 */
@Composable
fun CallRecord.cardTitle(): String =
    displayName?.takeIf { it.isNotBlank() }
        ?: phoneNormalized?.let { DisplayFormat.phone(it) }
        ?: if (isManual) {
            deliveryAddress.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.manual_record_title)
        } else {
            stringResource(R.string.hidden_number)
        }

/** Звонок из карточки: открывает наборщик, ничего не набирая сам. */
fun Context.dial(phone: String) {
    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
}

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

/**
 * Общее тело карточки заказа: заголовок «адрес, иначе телефон», кнопка
 * звонка и строки «подпись — значение» — телефон, бутыли, адрес, район,
 * комментарий.
 *
 * [addressInTitle] — адрес уже стоит заголовком, отдельной строкой его
 * не повторяем. [hideEmptyLines] прячет пустые бутыли и комментарий
 * вместо заглушки «не заполнено» — так показывает чужие записи история:
 * у них пустого просто нет.
 *
 * [headerContent] встаёт между заголовком и строками (бейджи у своей
 * карточки), [content] — после строк (кнопки, время обработки).
 */
@Composable
fun RecordCardBody(
    title: String,
    phone: String,
    bottles: String,
    address: String,
    district: String?,
    onDial: (() -> Unit)? = null,
    headerTrailing: String? = null,
    addressInTitle: Boolean = false,
    comment: String? = null,
    hideEmptyLines: Boolean = false,
    headerContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
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
        if (onDial != null) {
            IconButton(onClick = onDial) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = stringResource(R.string.action_call),
                )
            }
        }
    }

    headerContent?.invoke()

    InfoLine(
        label = stringResource(R.string.main_phone),
        value = phone,
    )
    if (!hideEmptyLines || bottles.isNotBlank()) {
        InfoLine(
            label = stringResource(R.string.main_bottles),
            value = bottles.ifBlank { stringResource(R.string.main_not_filled) },
        )
    }
    if (!addressInTitle) {
        InfoLine(
            label = stringResource(R.string.main_address),
            value = address.ifBlank { stringResource(R.string.main_not_filled) },
        )
    }
    district?.takeIf { it.isNotBlank() }?.let { districtValue ->
        InfoLine(
            label = stringResource(R.string.main_district),
            value = districtValue,
        )
    }
    comment?.takeIf { it.isNotBlank() }?.let { commentValue ->
        InfoLine(
            label = stringResource(R.string.form_comment),
            value = commentValue,
        )
    }

    content()
}

@Composable
fun RecordCardShell(
    record: CallRecord,
    headerTrailing: String? = null,
    onDial: (() -> Unit)? = null,
    highlightDelivery: Boolean = true,
    showDeliveryBadge: Boolean = true,
    previouslyWritten: Boolean = false,
    onPreviouslyWrittenClick: (() -> Unit)? = null,
    onMissedClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val futureDelivery = highlightDelivery && record.deliveryDay != null
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
            RecordCardBody(
                title = record.cardTitle(),
                phone = record.phoneNormalized?.let { DisplayFormat.phone(it) }
                    ?: stringResource(R.string.main_phone_unknown),
                bottles = record.bottlesText,
                address = record.deliveryAddress,
                district = record.district,
                onDial = onDial,
                headerTrailing = headerTrailing,
                addressInTitle = record.isManual && record.deliveryAddress.isNotBlank(),
                headerContent = {
                    BadgeRow(
                        skipped = record.isSkipped,
                        silent = record.isSilent,
                        deliveryDay = if (showDeliveryBadge) record.deliveryDay else null,
                        answered = record.isAnswered,
                        manual = record.isManual,
                        previouslyWritten = previouslyWritten,
                        onPreviouslyWrittenClick = onPreviouslyWrittenClick,
                        onMissedClick = onMissedClick,
                    )
                },
            ) {
                content()
            }
        }
    }
}

/**
 * Карточка необработанного звонка: время звонка, предыдущий заказ этого
 * клиента и действия — изменить и отметить обработанной.
 */
@Composable
internal fun RecordCard(
    item: OpenCallItem,
    onEdit: () -> Unit,
    onProcessed: () -> Unit,
    onMarkAnswered: () -> Unit,
    onClearWritten: () -> Unit,
) {
    val context = LocalContext.current
    val phone = item.record.phoneNormalized

    RecordCardShell(
        record = item.record,
        previouslyWritten = item.writtenBefore,
        onPreviouslyWrittenClick = if (item.writtenBefore) onClearWritten else null,
        // Тап по «пропущен» отвечает на звонок задним числом.
        onMissedClick = if (item.record.isAnswered == false) onMarkAnswered else null,
        headerTrailing = DisplayFormat.callTime(item.record.incomingAt),
        onDial = phone?.let { number -> { context.dial(number) } },
    ) {
        item.previousOrder?.let { previous ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildPreviousText(previous),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f),
            ) {
                CardActionText(stringResource(R.string.main_edit))
            }
            Button(
                onClick = onProcessed,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f),
            ) {
                CardActionText(stringResource(R.string.main_mark_one_processed))
            }
        }
    }
}

/** Подпись кнопки карточки: одна строка без переноса. */
@Composable
private fun CardActionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
}

@Composable
private fun buildPreviousText(previous: CallRecord): String {
    val prefix = stringResource(R.string.main_previous_prefix)
    val address = previous.deliveryAddress.takeIf { it.isNotBlank() }
    return if (address != null) {
        "$prefix: ${previous.bottlesText} — $address"
    } else {
        "$prefix: ${previous.bottlesText}"
    }
}
