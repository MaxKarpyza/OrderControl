package ru.ordercontrol.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.ordercontrol.R
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.db.SharedHistoryEntry
import ru.ordercontrol.ui.DisplayFormat
import ru.ordercontrol.ui.InfoLine
import ru.ordercontrol.ui.RecordCardBody
import ru.ordercontrol.ui.RecordCardShell
import ru.ordercontrol.ui.dial

/**
 * Обработанные заявки: свои и чужие (общая история операторов), поиск по
 * контактам и адресам, звонок из карточки.
 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val viewModel: HistoryViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val text = viewModel.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!viewModel.loading && viewModel.items.isNotEmpty()) {
                SearchField(
                    query = viewModel.query,
                    onQueryChange = viewModel::updateQuery,
                    onClear = viewModel::clearQuery,
                )
            }

            FilterRow(
                filter = viewModel.filter,
                syncing = viewModel.syncing,
                onFilterChange = viewModel::applyFilter,
                onRefresh = viewModel::refresh,
            )

            when {
                viewModel.loading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                viewModel.visibleItems.isEmpty() -> when {
                    viewModel.query.isNotBlank() -> EmptySearch(Modifier.weight(1f))

                    // Своих записей нет, а у других уже есть: подсказываем
                    // про переключатель, иначе история выглядит пустой.
                    viewModel.filter == HistoryFilter.MINE && viewModel.hasOthers ->
                        EmptyOnlyMine(Modifier.weight(1f)) {
                            viewModel.applyFilter(HistoryFilter.ALL)
                        }

                    else -> EmptyHistory(Modifier.weight(1f))
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(viewModel.visibleItems, key = { it.key }) { item ->
                        when (item) {
                            is HistoryItem.Mine -> HistoryCard(
                                record = item.record,
                                onRestore = { viewModel.restore(item.record.id) },
                            )

                            is HistoryItem.Other -> SharedHistoryCard(item.entry)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        placeholder = { Text(stringResource(R.string.history_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.history_search_clear),
                    )
                }
            }
        },
        singleLine = true,
    )
}

/** Переключатель «Все / Мои / Не мои» и обновление общей истории. */
@Composable
private fun FilterRow(
    filter: HistoryFilter,
    syncing: Boolean,
    onFilterChange: (HistoryFilter) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Переключатель прокручивается, если кнопки не помещаются на узком экране.
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == HistoryFilter.ALL,
                onClick = { onFilterChange(HistoryFilter.ALL) },
                label = { Text(stringResource(R.string.history_filter_all)) },
            )
            FilterChip(
                selected = filter == HistoryFilter.MINE,
                onClick = { onFilterChange(HistoryFilter.MINE) },
                label = { Text(stringResource(R.string.history_filter_mine)) },
            )
            FilterChip(
                selected = filter == HistoryFilter.OTHERS,
                onClick = { onFilterChange(HistoryFilter.OTHERS) },
                label = { Text(stringResource(R.string.history_filter_not_mine)) },
            )
        }
        IconButton(onClick = onRefresh, enabled = !syncing) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.history_refresh),
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(record: CallRecord, onRestore: () -> Unit) {
    val context = LocalContext.current
    RecordCardShell(
        record = record,
        highlightDelivery = false,
        showDeliveryBadge = false,
        onDial = record.phoneNormalized?.let { phone -> { context.dial(phone) } },
    ) {
        InfoLine(
            label = stringResource(R.string.history_call_time),
            value = DisplayFormat.fullDateTime(record.incomingAt),
        )
        // Длительность есть не у всех звонков: у пропущенных её не бывает,
        // поэтому строку без неё не рисуем.
        DisplayFormat.duration(record.durationSec)?.let { duration ->
            InfoLine(
                label = stringResource(R.string.history_call_duration),
                value = duration,
            )
        }
        val processedAt = record.processedAt
        InfoLine(
            label = stringResource(R.string.history_processed_time),
            value = processedAt?.let { DisplayFormat.fullDateTime(it) }
                ?: stringResource(R.string.main_not_filled),
        )
        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.history_restore))
        }
    }
}

/**
 * Карточка чужой записи: читается и обзванивается, но менять её нельзя —
 * запись принадлежит другому оператору и живёт в таблице до истечения срока.
 */
@Composable
private fun SharedHistoryCard(entry: SharedHistoryEntry) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RecordCardBody(
                title = entry.address.ifBlank { DisplayFormat.phone(entry.phone) },
                phone = DisplayFormat.phone(entry.phone),
                bottles = entry.bottles,
                address = entry.address,
                district = entry.district,
                onDial = { context.dial(entry.phone) },
                addressInTitle = entry.address.isNotBlank(),
                comment = entry.comment,
                hideEmptyLines = true,
            ) {
                InfoLine(
                    label = stringResource(R.string.history_author),
                    value = entry.author,
                )
                InfoLine(
                    label = stringResource(R.string.history_processed_time),
                    value = DisplayFormat.fullDateTime(entry.processedAt),
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier) {
    EmptyMessage(
        modifier = modifier,
        title = stringResource(R.string.history_empty),
        hint = stringResource(R.string.history_empty_hint),
    )
}

@Composable
private fun EmptyOnlyMine(modifier: Modifier, onShowAll: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.history_empty_others),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onShowAll) {
                Text(stringResource(R.string.history_show_all))
            }
        }
    }
}

@Composable
private fun EmptySearch(modifier: Modifier) {
    EmptyMessage(
        modifier = modifier,
        title = stringResource(R.string.history_search_empty),
        hint = stringResource(R.string.history_search_empty_hint),
    )
}

@Composable
private fun EmptyMessage(modifier: Modifier, title: String, hint: String) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
