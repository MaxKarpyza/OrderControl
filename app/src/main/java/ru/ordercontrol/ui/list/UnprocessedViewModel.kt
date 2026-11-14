package ru.ordercontrol.ui.list

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.R
import ru.ordercontrol.data.OpenCallItem
import ru.ordercontrol.data.SheetCheckOutcome
import ru.ordercontrol.data.SheetFetchResult
import ru.ordercontrol.data.SheetTab
import ru.ordercontrol.data.SheetWriteOutcome
import ru.ordercontrol.data.SheetWriteProgress
import ru.ordercontrol.domain.AmbiguousMatch
import ru.ordercontrol.phone.CallSync
import ru.ordercontrol.widget.WidgetRefresh

/** Состояние ручной проверки необработанных записей по Google Таблице. */
enum class SheetCheckPhase { Idle, Loading, Done, Error }

/** Состояние отправки заказов в таблицу. */
enum class SheetWritePhase { Idle, Writing, Done, Error }

class UnprocessedViewModel(application: Application) : AndroidViewModel(application) {

    var items by mutableStateOf<List<OpenCallItem>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var processing by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var sheetPhase by mutableStateOf(SheetCheckPhase.Idle)
        private set

    /** Итог проверки или текст ошибки для диалога. */
    var sheetMessage by mutableStateOf<String?>(null)
        private set

    /** Неполные совпадения, ждущие подтверждения оператором. */
    var sheetAmbiguous by mutableStateOf<List<AmbiguousMatch>>(emptyList())
        private set

    /** Сколько записей закрыто при расхождении количества с таблицей. */
    var sheetBottleMismatchCount by mutableIntStateOf(0)
        private set

    /** Пояснение, почему ничего не закрылось (нет адресов, адреса не совпали). */
    var sheetHint by mutableStateOf<String?>(null)
        private set

    /** Скачанные вкладки: повторная сверка после подтверждения синонима идёт без сети. */
    private var sheetTabs: List<SheetTab> = emptyList()

    var writePhase by mutableStateOf(SheetWritePhase.Idle)
        private set

    /** Прогресс записи: сколько сделано из скольких. */
    var writeProgress by mutableStateOf<SheetWriteProgress?>(null)
        private set

    var writeOutcome by mutableStateOf<SheetWriteOutcome?>(null)
        private set

    /** Текст ошибки, когда запись не удалась целиком. */
    var writeError by mutableStateOf<String?>(null)
        private set

    private var writeJob: Job? = null

    init {
        observeItems()
    }

    private fun observeItems() {
        viewModelScope.launch {
            try {
                AppGraph.calls.observeOpenItems().collectLatest { records ->
                    items = records
                    loading = false
                }
            } catch (_: Exception) {
                loading = false
                message = getApplication<Application>().getString(R.string.main_error)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                // Восстановление пропущенных звонков делает App при старте
                // процесса; здесь только сверка уже имеющихся записей.
                CallSync.sync(context)
                AppGraph.calls.refreshNamesFromContacts()
            } catch (_: Exception) {
                message = context.getString(R.string.main_error)
            } finally {
                // Сверка могла закрыть записи, удалённые из журнала звонков, —
                // счётчик на виджетах нужно пересчитать даже при ошибке выше.
                WidgetRefresh.updateAll(context)
                loading = false
            }
        }
    }

    fun processAll() {
        if (processing) return
        viewModelScope.launch {
            processing = true
            val context = getApplication<Application>()
            try {
                AppGraph.calls.markAllProcessed()
                WidgetRefresh.updateAll(context)
                message = context.getString(R.string.main_processed_toast)
            } catch (_: Exception) {
                message = context.getString(R.string.main_error)
            } finally {
                processing = false
            }
        }
    }

    /** Отмечает обработанной одну запись из списка. */
    fun markProcessed(recordId: Long) {        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                AppGraph.calls.markProcessed(recordId)
                WidgetRefresh.updateAll(context)
                message = context.getString(R.string.main_one_processed_toast)
            } catch (_: Exception) {
                message = context.getString(R.string.main_error)
            }
        }
    }

    /** Снимает отметку «ранее обработана»: адрес снова пойдёт в таблицу. */
    fun clearWrittenBefore(recordId: Long) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                AppGraph.calls.clearWrittenMark(recordId)
                message = context.getString(R.string.main_written_mark_cleared)
            } catch (_: Exception) {
                message = context.getString(R.string.main_error)
            }
        }
    }

    /** Скачивает вкладки таблицы и сразу сверяет с ними необработанные записи. */
    fun checkSheet() {
        if (sheetPhase == SheetCheckPhase.Loading) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            sheetPhase = SheetCheckPhase.Loading
            sheetMessage = null
            sheetAmbiguous = emptyList()
            sheetBottleMismatchCount = 0
            sheetHint = null
            // Прошлые вкладки больше не нужны: они держатся в памяти только
            // до конца текущей проверки.
            sheetTabs = emptyList()
            try {
                when (val result = AppGraph.sheetChecks.check()) {
                    is SheetFetchResult.Success -> {
                        sheetTabs = result.tabs
                        showOutcome(AppGraph.sheetChecks.apply(result.tabs))
                    }

                    is SheetFetchResult.NoNetwork -> {
                        Log.w(TAG, "Проверка таблицы: нет сети")
                        fail(R.string.sheet_no_network)
                    }

                    is SheetFetchResult.Failure -> {
                        Log.w(TAG, "Проверка таблицы: ${result.reason}")
                        fail(R.string.sheet_failure, result.reason)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Проверка таблицы упала", e)
                fail(R.string.sheet_failure, e.message)
            }
            WidgetRefresh.updateAll(context)
        }
    }

    /**
     * Запоминает пару адресов как синоним и сразу повторяет проверку по тем же
     * вкладкам: теперь адрес считается точным и запись закроется.
     */
    fun confirmSheetAlias(match: AmbiguousMatch) {
        if (sheetPhase == SheetCheckPhase.Loading) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            sheetPhase = SheetCheckPhase.Loading
            try {
                AppGraph.sheetChecks.confirmAlias(match.recordAddress, match.row.address)
                message = context.getString(R.string.sheet_alias_saved)
                showOutcome(AppGraph.sheetChecks.apply(sheetTabs))
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось сохранить синоним", e)
                fail(R.string.sheet_alias_error)
            }
            WidgetRefresh.updateAll(context)
        }
    }

    /** Запоминает, что это разные адреса, и убирает пару из списка. */
    fun rejectSheetAlias(match: AmbiguousMatch) {
        if (sheetPhase == SheetCheckPhase.Loading) return
        viewModelScope.launch {
            try {
                AppGraph.sheetChecks.rejectAlias(match.recordAddress, match.row.address)
                sheetAmbiguous = sheetAmbiguous.filterNot {
                    it.recordId == match.recordId && it.row == match.row
                }
                message = getApplication<Application>()
                    .getString(R.string.sheet_alias_rejected)
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось запомнить отказ", e)
                fail(R.string.sheet_alias_error)
            }
        }
    }

    /** Закрывает диалог результата проверки и освобождает скачанные вкладки. */
    fun dismissSheetResult() {
        sheetPhase = SheetCheckPhase.Idle
        sheetMessage = null
        sheetAmbiguous = emptyList()
        sheetHint = null
        // Скачанные таблицы нужны были только для подтверждения синонимов,
        // пока открыт диалог. Дальше они не нужны.
        sheetTabs = emptyList()
    }

    override fun onCleared() {
        writeJob?.cancel()
        writeJob = null
        sheetTabs = emptyList()
        super.onCleared()
    }

    fun consumeMessage() {
        message = null
    }

    /**
     * Записывает заказы в таблицу: адреса → скрипт → перечитывание листа →
     * закрытие подтверждённых. Прогресс виден оператору, действие можно
     * остановить — тогда записанное уже подтверждённое всё равно закроется,
     * а остальное останется в необработанных.
     */
    fun sendToSheet() {
        if (writePhase == SheetWritePhase.Writing) return
        writeJob = viewModelScope.launch {
            val context = getApplication<Application>()
            writePhase = SheetWritePhase.Writing
            writeOutcome = null
            writeError = null
            writeProgress = SheetWriteProgress(done = 0, total = items.size, address = "")
            try {
                writeOutcome = AppGraph.sheetWrites.send { progress ->
                    writeProgress = progress
                }
                writePhase = SheetWritePhase.Done
            } catch (e: CancellationException) {
                Log.i(TAG, "Запись в таблицу остановлена оператором")
                writePhase = SheetWritePhase.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Запись в таблицу упала", e)
                writeError = e.message
                writePhase = SheetWritePhase.Error
            } finally {
                writeProgress = null
                WidgetRefresh.updateAll(context)
            }
        }
    }

    /** Останавливает запись: уже закрытые записи остаются закрытыми. */
    fun stopSheetWrite() {
        writeJob?.cancel()
        writeJob = null
        writePhase = SheetWritePhase.Idle
        writeProgress = null
    }

    fun dismissWriteResult() {
        writePhase = SheetWritePhase.Idle
        writeProgress = null
        writeOutcome = null
        writeError = null
    }

    private fun showOutcome(outcome: SheetCheckOutcome) {
        val context = getApplication<Application>()
        sheetMessage = context.getString(
            R.string.sheet_result,
            outcome.tabsChecked.joinToString(", ").ifEmpty { "—" },
            outcome.rowsRead,
            outcome.closedCount,
        )
        sheetBottleMismatchCount = outcome.exactWithBottleMismatch.size
        sheetAmbiguous = outcome.ambiguous
        sheetHint = when {
            outcome.recordsWithAddress == 0 -> context.getString(R.string.sheet_no_address)
            outcome.closedCount == 0 && outcome.ambiguous.isEmpty() ->
                context.getString(R.string.sheet_no_match)

            else -> null
        }
        sheetPhase = SheetCheckPhase.Done
        Log.i(
            TAG,
            "Проверка таблицы: вкладки=${outcome.tabsChecked}, адресов=${outcome.rowsRead}, " +
                "записей с адресом=${outcome.recordsWithAddress}, закрыто=${outcome.closedCount}, " +
                "неполных=${outcome.ambiguous.size}",
        )
    }

    private fun fail(@StringRes messageRes: Int, detail: String? = null) {
        val base = getApplication<Application>().getString(messageRes)
        sheetMessage = if (detail.isNullOrBlank()) base else "$base ($detail)"
        sheetAmbiguous = emptyList()
        sheetBottleMismatchCount = 0
        sheetHint = null
        sheetPhase = SheetCheckPhase.Error
        sheetTabs = emptyList()
    }

    private companion object {
        const val TAG = "SheetCheck"
    }
}
