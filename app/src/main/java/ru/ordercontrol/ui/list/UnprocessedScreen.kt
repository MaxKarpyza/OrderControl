package ru.ordercontrol.ui.list

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.ordercontrol.R
import ru.ordercontrol.FormActivity
import ru.ordercontrol.ui.RecordCard
import ru.ordercontrol.ui.history.HistoryScreen
import ru.ordercontrol.ui.manual.ManualOrderDialog
import ru.ordercontrol.ui.manual.ManualOrderViewModel
import ru.ordercontrol.ui.settings.SettingsScreen

private const val TAB_UNPROCESSED = 0
private const val TAB_HISTORY = 1
private const val TAB_COUNT = 2

@Composable
fun UnprocessedScreen() {
    val viewModel: UnprocessedViewModel = viewModel()
    val manualViewModel: ManualOrderViewModel = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showManualOrder by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Первое открытие покрывает ON_RESUME: наблюдатель сразу доводится
    // до текущего состояния активности, отдельный запуск не нужен —
    // он дал бы две параллельные синхронизации журнала звонков.
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

    if (showManualOrder) {
        ManualOrderDialog(
            viewModel = manualViewModel,
            onDismiss = { showManualOrder = false },
            onSaved = {
                showManualOrder = false
                viewModel.notifyManualAdded()
            },
        )
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
                IconButton(
                    onClick = {
                        // Карточка живёт дольше одного открытия — чистим её.
                        manualViewModel.reset()
                        showManualOrder = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.manual_add),
                    )
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
    var pendingAnsweredId by rememberSaveable { mutableStateOf(0L) }

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
                        onMarkAnswered = { pendingAnsweredId = item.record.id },
                        onClearWritten = { viewModel.clearWrittenBefore(item.record.id) },
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
                onClick = viewModel::sendToSheet,
                enabled = viewModel.writePhase != SheetWritePhase.Writing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                Text(stringResource(R.string.write_button))
            }
            OutlinedButton(
                onClick = {
                    processConfirmed = false
                    showProcessDialog = true
                },
                enabled = !viewModel.processing && viewModel.writePhase != SheetWritePhase.Writing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.main_mark_processed))
            }
        }

        if (viewModel.writePhase == SheetWritePhase.Writing) {
            WriteProgressDialog(
                progress = viewModel.writeProgress,
                onStop = viewModel::stopSheetWrite,
            )
        }

        if (viewModel.writePhase == SheetWritePhase.Done ||
            viewModel.writePhase == SheetWritePhase.Error
        ) {
            WriteResultDialog(
                outcome = viewModel.writeOutcome,
                error = viewModel.writeError,
                onDismiss = viewModel::dismissWriteResult,
            )
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

        if (pendingAnsweredId != 0L) {
            MarkAnsweredDialog(
                onConfirm = {
                    val id = pendingAnsweredId
                    pendingAnsweredId = 0L
                    if (id != 0L) viewModel.markAnswered(id)
                },
                onDismiss = { pendingAnsweredId = 0L },
            )
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
