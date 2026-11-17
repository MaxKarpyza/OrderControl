package ru.ordercontrol.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.ordercontrol.R
import ru.ordercontrol.data.db.AddressBaseEntry

/**
 * Поле адреса с подсказками из базы адресов.
 *
 * Подсказки — помощник, а не замена ввода: они стоят списком под полем,
 * не перехватывают фокус и не закрывают клавиатуру, поэтому оператор может
 * продолжать печатать. Выбранный адрес подставляется в то же поле, курсор
 * остаётся в конце — дописать можно.
 *
 * В подсказке видно район и количество бутылей из последнего заказа:
 * оператор выбирает адрес и сразу видит, что заказ повторный.
 *
 * Поле общее для обоих путей ввода заказа — по телефону и вручную, —
 * поэтому поведение у них одинаковое.
 */
@Composable
fun OrderAddressField(
    value: String,
    suggestions: List<AddressBaseEntry>,
    onValueChange: (String) -> Unit,
    onSelect: (AddressBaseEntry) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val visible = focused && !hidden && value.isNotBlank() && suggestions.isNotEmpty()

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                hidden = false
                onValueChange(it)
            },
            label = { Text(stringResource(R.string.form_address)) },
            placeholder = { Text(stringResource(R.string.form_address_hint)) },
            singleLine = true,
            supportingText = supportingText,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    // Esc убирает подсказки, но поле остаётся в фокусе.
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.Escape &&
                        visible
                    ) {
                        hidden = true
                        true
                    } else {
                        false
                    }
                },
        )

        if (visible) {
            SuggestionList(
                suggestions = suggestions,
                onSelect = {
                    onSelect(it)
                    focusRequester.requestFocus()
                },
            )
        }
    }
}

/** Список подсказок под полем адреса. */
@Composable
private fun SuggestionList(
    suggestions: List<AddressBaseEntry>,
    onSelect: (AddressBaseEntry) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            suggestions.forEach { entry ->
                val details = listOfNotNull(
                    entry.district,
                    entry.bottles?.let { stringResource(R.string.manual_bottles_short, it) },
                ).joinToString(" · ")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Подсказка не забирает фокус: поле остаётся
                        // активным, клавиатура не прячется.
                        .focusProperties { canFocus = false }
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = entry.address,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (details.isNotEmpty()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
