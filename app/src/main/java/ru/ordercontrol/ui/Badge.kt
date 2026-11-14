package ru.ordercontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.ordercontrol.R
import ru.ordercontrol.domain.DeliveryDay

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Принят звонок или пропущен: короткая цветная метка для карточек и формы. */
@Composable
fun CallStatusBadge(
    answered: Boolean?,
    modifier: Modifier = Modifier,
) {
    when (answered) {
        true -> Badge(
            text = stringResource(R.string.badge_answered),
            modifier = modifier,
            containerColor = Color(0xFFD7F5DD),
            contentColor = Color(0xFF14532D),
        )

        false -> Badge(
            text = stringResource(R.string.badge_missed),
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )

        null -> Unit
    }
}

@Composable
fun BadgeRow(
    skipped: Boolean,
    silent: Boolean,
    deliveryDay: DeliveryDay? = null,
    answered: Boolean? = null,
    previouslyWritten: Boolean = false,
) {
    if (!skipped && !silent && deliveryDay == null && answered == null && !previouslyWritten) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (previouslyWritten) {
            Badge(
                text = stringResource(R.string.badge_written_before),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (deliveryDay != null) {
            DeliveryBadge(deliveryDay)
        }
        CallStatusBadge(answered)
        if (skipped) {
            Badge(text = stringResource(R.string.badge_skipped))
        }
        if (silent) {
            Badge(text = stringResource(R.string.badge_silent))
        }
    }
}

/**
 * День доставки: заметная залитая плашка. Заказ на будущий день не должен
 * теряться среди сегодняшних.
 */
@Composable
fun DeliveryBadge(deliveryDay: DeliveryDay, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = stringResource(deliveryDay.badgeRes()),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

private fun DeliveryDay.badgeRes(): Int = when (this) {
    DeliveryDay.TOMORROW -> R.string.badge_delivery_tomorrow
    DeliveryDay.DAY_AFTER_TOMORROW -> R.string.badge_delivery_day_after_tomorrow
    DeliveryDay.MONDAY -> R.string.badge_delivery_monday
    DeliveryDay.OTHER_DAY -> R.string.badge_delivery_other_day
}
