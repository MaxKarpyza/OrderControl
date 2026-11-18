package ru.ordercontrol.ui.order

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.ordercontrol.R

/**
 * Поле района — выбор из списка районов, без ручного ввода.
 *
 * Список — районы, которые приложение распознало в таблице. Пока база
 * адресов не собрана, список пуст: поле неактивно и подсказывает, почему.
 * Район, которого нет в списке (старая запись, новый район), показывается
 * как есть — его видно, но заменить можно только значением из списка.
 *
 * Поле общее для обоих путей ввода заказа — по телефону и вручную, —
 * поэтому поведение у них одинаковое.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistrictField(
    value: String,
    districts: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val enabled = districts.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.manual_district)) },
            placeholder = { Text(stringResource(R.string.district_select)) },
            supportingText = if (enabled) {
                null
            } else {
                { Text(stringResource(R.string.district_list_empty)) }
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            districts.forEach { district ->
                DropdownMenuItem(
                    text = { Text(district) },
                    onClick = {
                        expanded = false
                        onSelect(district)
                    },
                )
            }
        }
    }
}

/**
 * Районы для списка: сначала частые, при равенстве и нулях — по алфавиту.
 *
 * @param usage число адресов по районам; районов без значения считаются
 *   нулевыми и уходят в конец.
 */
internal fun districtOptions(
    districts: Set<String>,
    usage: Map<String, Int> = emptyMap(),
): List<String> =
    districts
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedWith(
            compareByDescending<String> { usage[it] ?: 0 }.thenBy { it },
        )
