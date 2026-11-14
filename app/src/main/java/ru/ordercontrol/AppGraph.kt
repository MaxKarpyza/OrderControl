package ru.ordercontrol

import android.content.Context
import ru.ordercontrol.data.CallRepository
import ru.ordercontrol.data.ContactRepository
import ru.ordercontrol.data.SettingsRepository
import ru.ordercontrol.data.SheetCheckRepository
import ru.ordercontrol.data.SheetWriteClient
import ru.ordercontrol.data.SheetWriteRepository
import ru.ordercontrol.data.SheetsClient
import ru.ordercontrol.data.db.AddressAliasDao
import ru.ordercontrol.data.db.AppDatabase
import ru.ordercontrol.update.UpdateRepository

/** Ручной контейнер зависимостей: приложение маленькое, DI-фреймворк не нужен. */
object AppGraph {

    private lateinit var appContext: Context

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val contacts: ContactRepository by lazy { ContactRepository(appContext, settings) }

    val addressAliases: AddressAliasDao by lazy { database.addressAliasDao() }

    val sheets: SheetsClient by lazy { SheetsClient() }

    val calls: CallRepository by lazy {
        CallRepository(
            callDao = database.callRecordDao(),
            blacklistDao = database.blacklistDao(),
            settings = settings,
            contacts = contacts,
        )
    }

    val sheetChecks: SheetCheckRepository by lazy {
        SheetCheckRepository(
            callDao = database.callRecordDao(),
            aliasDao = addressAliases,
            sheets = sheets,
        )
    }

    val sheetWrites: SheetWriteRepository by lazy {
        SheetWriteRepository(
            callDao = database.callRecordDao(),
            client = SheetWriteClient(),
            sheets = sheets,
        )
    }

    val updates: UpdateRepository by lazy { UpdateRepository(appContext, settings) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
