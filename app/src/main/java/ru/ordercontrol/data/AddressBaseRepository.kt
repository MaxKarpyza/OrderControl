package ru.ordercontrol.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ru.ordercontrol.data.db.AddressBaseDao
import ru.ordercontrol.data.db.AddressBaseEntry
import ru.ordercontrol.data.db.SheetTabDao
import ru.ordercontrol.data.db.SheetTabEntity
import ru.ordercontrol.domain.AddressBaseVocabulary
import ru.ordercontrol.domain.AddressNormalizer
import ru.ordercontrol.domain.SheetAddressBase
import ru.ordercontrol.domain.SheetAddressBlock
import ru.ordercontrol.domain.SheetAddressEntry

/** Итог обновления базы адресов. */
sealed interface AddressBaseResult {

    /** Разбор закончен. `failedTabs` — вкладки, которые не удалось прочитать. */
    data class Success(
        val parsedTabs: Int,
        val failedTabs: Int,
        val addresses: Int,
    ) : AddressBaseResult

    /** Разбор не состоялся: нет сети или таблица недоступна. */
    data class Failure(val reason: String) : AddressBaseResult
}

/**
 * База адресов: справочник адресов, районов и количества бутылей.
 *
 * Источник — вкладки Google Таблицы: в каждой вкладке за день адреса
 * лежат в трёх блоках колонок. Первый раз разбираются все вкладки, дальше —
 * только те, которых ещё не было в базе.
 */
class AddressBaseRepository(
    private val sheets: SheetsClient,
    private val dao: AddressBaseDao,
    private val tabDao: SheetTabDao,
    private val settings: SettingsRepository,
) {

    /** Разобранная вкладка: имя и адреса по блокам колонок. */
    private data class ParsedTab(
        val name: String,
        val blocks: List<SheetAddressBlock>,
    )

    /** База ещё не собрана — подсказок пока не будет. */
    suspend fun isEmpty(): Boolean = dao.count() == 0

    suspend fun count(): Int = dao.count()

    /** Подсказки по подстроке адреса. */
    suspend fun search(query: String, limit: Int = SUGGESTION_LIMIT): List<AddressBaseEntry> {
        val key = AddressNormalizer.normalize(query)
        if (key.isEmpty()) return emptyList()
        return dao.search(key, limit)
    }

    /** Точное совпадение адреса: район и количество из последнего заказа. */
    suspend fun find(address: String): AddressBaseEntry? {
        val key = AddressNormalizer.normalize(address)
        if (key.isEmpty()) return null
        return dao.find(key)
    }

    /**
     * Обновляет базу: разбирает вкладки, которых в ней ещё нет.
     *
     * Вкладки идут по датам, поэтому «последний заказ» — это самая свежая
     * строка по адресу. Если в ней район не указан, остаётся район из
     * предыдущей строки: пустой район не значит, что адрес сменил район.
     */
    suspend fun refresh(
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): AddressBaseResult {
        val names = sheets.fetchAllTabNames()
        if (names.isEmpty()) {
            return AddressBaseResult.Failure("не удалось получить список вкладок")
        }

        val parsed = tabDao.parsedNames().toSet()
        val pending = names
            .filter { it !in parsed }
            .mapNotNull { name -> tabDate(name)?.let { name to it } }
            .sortedBy { it.second }
            .map { it.first }

        if (pending.isEmpty()) {
            return AddressBaseResult.Success(
                parsedTabs = 0,
                failedTabs = 0,
                addresses = dao.count(),
            )
        }

        val now = System.currentTimeMillis()
        val merged = HashMap(dao.all().associateBy { it.key })
        val touched = mutableSetOf<String>()
        val doneTabs = mutableListOf<String>()
        val failedTabs = mutableListOf<String>()
        var done = 0

        // Сначала читаем вкладки целиком: словарь районов нужен, чтобы
        // отличить район от комментария, а он собирается по вкладкам сразу.
        val parsedTabs = mutableListOf<ParsedTab>()
        coroutineScope {
            for (chunk in pending.chunked(PARALLELISM)) {
                val results = chunk.map { name ->
                    async { name to sheets.fetchTabCsv(name) }
                }.awaitAll()

                for ((name, csv) in results) {
                    if (csv == null) {
                        // Вкладку не прочитали — она не считается разобранной,
                        // в следующий раз её попробуют снова.
                        failedTabs += name
                        done++
                        onProgress(done, pending.size)
                        continue
                    }
                    parsedTabs += ParsedTab(name, SheetAddressBase.parse(csv))
                    doneTabs += name
                    done++
                    onProgress(done, pending.size)
                }
            }
        }

        // Вкладки идут по датам, поэтому самая свежая разбирается последней —
        // по ней и определяется, где в таблице лежит район.
        val vocabulary = AddressBaseVocabulary(settings.addressBaseDistricts())
        if (vocabulary.isEmpty) {
            vocabulary.bootstrap(parsedTabs.takeLast(BOOTSTRAP_TABS).map { it.blocks })
        }

        for (tab in parsedTabs) {
            val entries = tab.blocks.flatMap { block ->
                val columns = SheetAddressBase.BLOCKS[block.index].districtColumns
                val column = vocabulary.districtColumn(block, columns)
                block.rows.map { row ->
                    SheetAddressEntry(
                        address = row.address,
                        bottles = row.bottles,
                        district = column?.let { row.candidates[it] }
                            ?.takeIf { value -> value.none { it.isDigit() } },
                    )
                }
            }
            merge(entries, merged, touched, now)
        }

        if (doneTabs.isNotEmpty()) {
            dao.upsertAll(merged.values.filter { it.key in touched })
            tabDao.markParsed(doneTabs.map { SheetTabEntity(name = it, parsedAt = now) })
            settings.setAddressBaseDistricts(vocabulary.districts())
        }

        return AddressBaseResult.Success(
            parsedTabs = doneTabs.size,
            failedTabs = failedTabs.size,
            addresses = dao.count(),
        )
    }

    /** Обновляет адреса разобранной вкладки, не затирая прежние значения. */
    private fun merge(
        entries: List<SheetAddressEntry>,
        merged: MutableMap<String, AddressBaseEntry>,
        touched: MutableSet<String>,
        now: Long,
    ) {
        for (entry in entries) {
            val key = AddressNormalizer.normalize(entry.address)
            if (key.isEmpty()) continue
            merged[key] = mergeAddressEntry(merged[key], entry, now)
            touched += key
        }
    }

    private companion object {

        /** Сколько вкладок качаем одновременно. */
        const val PARALLELISM = 4

        /** Сколько подсказок показываем в поле адреса. */
        const val SUGGESTION_LIMIT = 20

        /**
         * Сколько самых свежих вкладок показывают, где лежит район.
         *
         * Раскладка со временем менялась, но в свежих вкладках она одна,
         * а по ним видно, какая колонка с районами.
         */
        const val BOOTSTRAP_TABS = 30

        private val TAB_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd.MM.uu", Locale.ROOT)

        /**
         * Дата вкладки по её имени.
         *
         * Кроме дат в документе есть служебные вкладки (`_tg_queue`),
         * они разбору не подлежат.
         */
        fun tabDate(name: String): LocalDate? = try {
            LocalDate.parse(name.trim(), TAB_DATE)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Обновляет адрес в базе по строке таблицы.
 *
 * Пустые значения не затирают прежние: в таблице район пишут не в каждой
 * строке, и «район не указан» не значит «адрес сменил район». Непустой
 * район и количество из последней строки — наоборот, побеждают.
 */
internal fun mergeAddressEntry(
    old: AddressBaseEntry?,
    entry: SheetAddressEntry,
    now: Long,
): AddressBaseEntry {
    val address = entry.address.trim()
    return AddressBaseEntry(
        key = AddressNormalizer.normalize(address),
        address = address,
        district = entry.district ?: old?.district,
        bottles = entry.bottles ?: old?.bottles,
        updatedAt = now,
    )
}
