package ru.ordercontrol

import android.content.Context
import ru.ordercontrol.data.AddressBaseRepository
import ru.ordercontrol.data.CallRepository
import ru.ordercontrol.data.ContactRepository
import ru.ordercontrol.data.HistorySyncClient
import ru.ordercontrol.data.HistorySyncRepository
import ru.ordercontrol.data.SettingsRepository
import ru.ordercontrol.data.SheetCheckRepository
import ru.ordercontrol.data.SheetWriteClient
import ru.ordercontrol.data.SheetWriteRepository
import ru.ordercontrol.data.SheetsClient
import ru.ordercontrol.data.db.AddressAliasDao
import ru.ordercontrol.data.db.AppDatabase
import ru.ordercontrol.data.db.SharedHistoryDao
import ru.ordercontrol.update.UpdateDownloadManager
import ru.ordercontrol.update.UpdateRepository

/** Ручной контейнер зависимостей: приложение маленькое, DI-фреймворк не нужен. */
object AppGraph {

    private lateinit var appContext: Context

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val contacts: ContactRepository by lazy { ContactRepository(appContext, settings) }

    val addressAliases: AddressAliasDao by lazy { database.addressAliasDao() }

    val sharedHistory: SharedHistoryDao by lazy { database.sharedHistoryDao() }

    /** Чужие обработанные заказы из вкладки `history` Google Таблицы. */
    val historySync: HistorySyncRepository by lazy {
        HistorySyncRepository(
            callDao = database.callRecordDao(),
            sharedDao = sharedHistory,
            client = HistorySyncClient(),
            operators = settings,
        )
    }

    val sheets: SheetsClient by lazy { SheetsClient() }

    val calls: CallRepository by lazy {
        CallRepository(
            callDao = database.callRecordDao(),
            blacklistDao = database.blacklistDao(),
            settings = settings,
            contacts = contacts,
            addressBase = addressBase,
        )
    }

    val sheetChecks: SheetCheckRepository by lazy {
        SheetCheckRepository(
            callDao = database.callRecordDao(),
            aliasDao = addressAliases,
            sheets = sheets,
            // Таблицу скачали: растёт счётчик, и на десятое скачивание
            // обновляется база адресов.
            onTableFetched = { addressBase.onTableFetched() },
        )
    }

    val sheetWrites: SheetWriteRepository by lazy {
        SheetWriteRepository(
            callDao = database.callRecordDao(),
            client = SheetWriteClient(),
            sheets = sheets,
            addressBase = addressBase,
            history = historySync,
        )
    }

    val addressBase: AddressBaseRepository by lazy {
        AddressBaseRepository(
            sheets = sheets,
            dao = database.addressBaseDao(),
            tabDao = database.sheetTabDao(),
            settings = settings,
        )
    }

    val updates: UpdateRepository by lazy { UpdateRepository(appContext, settings) }

    /** Скачивание обновления переживает уход с экрана и перезапуск приложения. */
    val updateDownloads: UpdateDownloadManager by lazy {
        UpdateDownloadManager(appContext, updates, settings)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
