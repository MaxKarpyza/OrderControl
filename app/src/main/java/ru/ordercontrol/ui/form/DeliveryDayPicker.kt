package ru.ordercontrol.ui.form

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import ru.ordercontrol.R
import ru.ordercontrol.domain.DeliveryDay

/**
 * Переключатель дня доставки.
 *
 * Повторное нажатие снимает отметку: неотмеченный заказ считается
 * сегодняшним.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryDayPicker(
    options: List<DeliveryDay>,
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

/** Подпись варианта дня доставки. */
fun DeliveryDay.labelRes(): Int = when (this) {
    DeliveryDay.TOMORROW -> R.string.delivery_tomorrow
    DeliveryDay.DAY_AFTER_TOMORROW -> R.string.delivery_day_after_tomorrow
    DeliveryDay.MONDAY -> R.string.delivery_monday
    DeliveryDay.OTHER_DAY -> R.string.delivery_other_day
}
