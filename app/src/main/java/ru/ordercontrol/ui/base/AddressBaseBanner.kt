package ru.ordercontrol.ui.base

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ordercontrol.R

/**
 * Плашка на главном экране: состояние базы адресов.
 *
 * База собирается в фоне и не блокирует работу: заказ по звонку базу
 * адресов не требует, не работают только подсказки при ручном вводе
 * адреса. Плашка показывает, что база собирается или что собрать её не
 * удалось, и позволяет повторить.
 */
@Composable
fun AddressBaseBanner(
    state: AddressBaseUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.phase) {
        // Фоновое обновление уже собранной базы оператора не волнует:
        // подсказки и так работают по старым адресам.
        AddressBasePhase.Building -> if (!state.empty) {
            BuildingBanner(state, modifier)
        }

        AddressBasePhase.Failed -> FailedBanner(state, onRetry, modifier)

        AddressBasePhase.Idle, AddressBasePhase.Ready -> Unit
    }
}

@Composable
private fun BuildingBanner(state: AddressBaseUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.base_building),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (state.total > 0) {
                Text(
                    text = stringResource(R.string.base_progress, state.done, state.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun FailedBanner(
    state: AddressBaseUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.base_banner_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                state.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.base_retry),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
