package ru.ordercontrol.ui.list

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.ordercontrol.R
import ru.ordercontrol.FormActivity
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.OpenCallItem
import ru.ordercontrol.domain.AmbiguousMatch
import ru.ordercontrol.ui.DisplayFormat
import ru.ordercontrol.ui.RecordCardShell
import ru.ordercontrol.ui.history.HistoryScreen
import ru.ordercontrol.ui.settings.SettingsScreen

private const val TAB_UNPROCESSED = 0
private const val TAB_HISTORY = 1
private const val TAB_COUNT = 2

@Composable
fun UnprocessedScreen() {
    val viewModel: UnprocessedViewModel = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    if (showSettings) {
        SettingsScreen(
            onBack = {
                showSettings = false
                viewModel.refresh()
            },
        )
        return
    }

    // safeDrawing включает IME: вкладки и кнопки остаются над клавиатурой,
    // а их положение меняется кадр в кадр вместе с анимацией инсетов.
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (pagerState.currentPage == TAB_HISTORY) {
                                R.string.tab_history
                            } else {
                                R.string.main_title
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!viewModel.loading && pagerState.currentPage == TAB_UNPROCESSED) {
                        Text(
                            text = stringResource(R.string.main_total, viewModel.items.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.main_settings),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                if (page == TAB_HISTORY) {
                    HistoryScreen(modifier = Modifier.fillMaxSize())
                } else {
                    UnprocessedTab(viewModel)
                }
            }

            // Заголовки вкладок внизу: до них проще дотянуться одной рукой,
            // а между вкладками можно перелистывать свайпом.
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == TAB_UNPROCESSED,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(TAB_UNPROCESSED) }
                    },
                    text = { Text(stringResource(R.string.tab_unprocessed)) },
                )
                Tab(
                    selected = pagerState.currentPage == TAB_HISTORY,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(TAB_HISTORY) }
                    },
                    text = { Text(stringResource(R.string.tab_history)) },
                )
            }
        }
    }
}

@Composable
private fun UnprocessedTab(viewModel: UnprocessedViewModel) {
    val context = LocalContext.current
    var showProcessDialog by rememberSaveable { mutableStateOf(false) }
    var processConfirmed by rememberSaveable { mutableStateOf(false) }
    var pendingProcessId by rememberSaveable { mutableStateOf(0L) }

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            viewModel.loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            viewModel.items.isEmpty() -> EmptyState(Modifier.weight(1f))

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(viewModel.items, key = { it.record.id }) { item ->
                    RecordCard(
                        item = item,
                        onEdit = {
                            context.startActivity(
                                Intent(context, FormActivity::class.java).apply {
                                    putExtra(FormActivity.EXTRA_RECORD_ID, item.record.id)
                                    putExtra(FormActivity.EXTRA_RETURN_TO_LIST, true)
                                },
                            )
                        },
                        onProcessed = { pendingProcessId = item.record.id },
                    )
                }
            }
        }

        if (!viewModel.loading) {
            OutlinedButton(
                onClick = viewModel::checkSheet,
                enabled = viewModel.sheetPhase != SheetCheckPhase.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                if (viewModel.sheetPhase == SheetCheckPhase.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sheet_check_loading))
                } else {
                    Text(stringResource(R.string.sheet_check))
                }
            }
        }

        if (viewModel.sheetPhase == SheetCheckPhase.Done ||
            viewModel.sheetPhase == SheetCheckPhase.Error
        ) {
            SheetCheckDialog(
                message = viewModel.sheetMessage.orEmpty(),
                hint = viewModel.sheetHint,
                bottleMismatchCount = viewModel.sheetBottleMismatchCount,
                ambiguous = viewModel.sheetAmbiguous,
                onConfirmAlias = viewModel::confirmSheetAlias,
                onRejectAlias = viewModel::rejectSheetAlias,
                onDismiss = viewModel::dismissSheetResult,
            )
        }

        if (!viewModel.loading && viewModel.items.isNotEmpty()) {
            Button(
                onClick = {
                    processConfirmed = false
                    showProcessDialog = true
                },
                enabled = !viewModel.processing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.main_mark_processed))
            }
        }

        if (showProcessDialog) {
            ProcessAllDialog(
                confirmed = processConfirmed,
                onConfirmedChange = { processConfirmed = it },
                onConfirm = {
                    showProcessDialog = false
                    viewModel.processAll()
                },
                onDismiss = {
                    showProcessDialog = false
                    processConfirmed = false
                },
            )
        }

        val pendingProcess = if (pendingProcessId != 0L) {
            viewModel.items.firstOrNull { it.record.id == pendingProcessId }
        } else {
            null
        }
        if (pendingProcessId != 0L) {
            ProcessOneDialog(
                address = pendingProcess?.record?.deliveryAddress.orEmpty(),
                onConfirm = {
                    val id = pendingProcessId
                    pendingProcessId = 0L
                    if (id != 0L) viewModel.markProcessed(id)
                },
                onDismiss = { pendingProcessId = 0L },
            )
        }
    }
}

@Composable
private fun ProcessOneDialog(
    address: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_confirm_one_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.main_confirm_one_text))
                if (address.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.main_confirm_one_address, address),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_no))
            }
        },
    )
}

@Composable
private fun ProcessAllDialog(
    confirmed: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_confirm_processed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.main_confirm_processed_text))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfirmedChange(!confirmed) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = onConfirmedChange,
                    )
                    Text(stringResource(R.string.main_confirm_processed_checkbox))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmed,
            ) {
                Text(stringResource(R.string.action_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_no))
            }
        },
    )
}

@Composable
private fun SheetCheckDialog(
    message: String,
    hint: String?,
    bottleMismatchCount: Int,
    ambiguous: List<AmbiguousMatch>,
    onConfirmAlias: (AmbiguousMatch) -> Unit,
    onRejectAlias: (AmbiguousMatch) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.sheet_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (bottleMismatchCount > 0) {
                    Text(
                        text = stringResource(R.string.sheet_bottles_mismatch, bottleMismatchCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (ambiguous.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.sheet_ambiguous_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    ambiguous.forEach { match ->
                        AmbiguousItem(
                            match = match,
                            onConfirm = { onConfirmAlias(match) },
                            onReject = { onRejectAlias(match) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun AmbiguousItem(
    match: AmbiguousMatch,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.sheet_record_address, match.recordAddress),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.sheet_row_address, match.row.address),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.sheet_same_address))
            }
            TextButton(onClick = onReject) {
                Text(stringResource(R.string.sheet_other_address))
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.main_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.main_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordCard(
    item: OpenCallItem,
    onEdit: () -> Unit,
    onProcessed: () -> Unit,
) {
    val context = LocalContext.current
    val phone = item.record.phoneNormalized

    RecordCardShell(
        record = item.record,
        headerTrailing = DisplayFormat.callTime(item.record.incomingAt),
        headerAction = phone?.let { number ->
            {
                IconButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")),
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = stringResource(R.string.action_call),
                    )
                }
            }
        },
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
