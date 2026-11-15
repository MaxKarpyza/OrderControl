package ru.ordercontrol.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Быстрый выбор количества: 1–5 одной кнопкой. Своё значение оператор
 * по-прежнему вводит в поле рядом.
 */
@Composable
fun QuickBottles(
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
