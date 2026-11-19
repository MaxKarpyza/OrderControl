package ru.ordercontrol.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.ordercontrol.AppGraph
import ru.ordercontrol.R
import ru.ordercontrol.data.SHEET_FETCH_LIMIT
import ru.ordercontrol.ui.DisplayFormat

/**
 * Раздел настроек: состояние базы адресов и её обновление.
 *
 * База нужна для подсказок адресов, поэтому оператор должен видеть, что она
 * собрана и когда обновлялась.
 */
@Composable
fun AddressBaseSection(modifier: Modifier = Modifier) {
    val viewModel: AddressBaseViewModel = viewModel()
    val state = viewModel.state
    var lastRefresh by remember { mutableLongStateOf(0L) }
    var fetches by remember { mutableIntStateOf(0) }

    // Перечитываем по счётчику обновлений, а не по фазе и количеству
    // адресов: фоновое обновление без новых адресов их не меняет, а время
    // последнего разбора обновляется.
    LaunchedEffect(state.refreshCount) {
        lastRefresh = AppGraph.settings.addressBaseLastRefresh()
        fetches = AppGraph.settings.sheetFetchCount()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.base_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.base_section_count, state.addresses),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (lastRefresh > 0) {
            Text(
                text = stringResource(
                    R.string.base_section_updated,
                    DisplayFormat.fullDateTime(lastRefresh),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.base_section_fetches, fetches, SHEET_FETCH_LIMIT),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedButton(
            onClick = { viewModel.refreshNow() },
            enabled = state.phase != AddressBasePhase.Building,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.phase == AddressBasePhase.Building) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 4.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(stringResource(R.string.base_section_refresh))
        }
    }
}
