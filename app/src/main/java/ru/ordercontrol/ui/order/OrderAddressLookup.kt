package ru.ordercontrol.ui.order

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ordercontrol.AppGraph
import ru.ordercontrol.data.db.AddressBaseEntry

/**
 * Поиск адреса в базе адресов.
 *
 * Класс общий для обоих путей ввода заказа — по телефону и вручную, —
 * поэтому подсказки и подстановка работают одинаково.
 *
 * Поиск идёт с задержкой: жмём букву — ищем по целому слову, а не по
 * каждой букве. Пока искали, текст мог измениться, поэтому результат
 * отбрасывается, если поле уже изменилось.
 */
class OrderAddressLookup(
    private val scope: CoroutineScope,
    private val onSuggestions: (String, List<AddressBaseEntry>) -> Unit,
    private val onBase: (String, AddressBaseEntry?) -> Unit,
) {

    private var job: Job? = null

    /** Текст, по которому идёт поиск: нужен, чтобы отбросить устаревший ответ. */
    private var query: String = ""

    /** Оператор набирает адрес. */
    fun lookup(text: String) {
        job?.cancel()
        query = text
        if (text.isBlank()) {
            onSuggestions(text, emptyList())
            return
        }
        job = scope.launch {
            delay(LOOKUP_DELAY_MS)
            if (query != text) return@launch
            // Поиск подсказок и точное совпадение сканируют базу адресов
            // линейно — уводим их с главного потока; колбэки остаются
            // на нём.
            val suggestions = withContext(Dispatchers.Default) {
                AppGraph.addressBase.search(text)
            }
            if (query != text) return@launch
            onSuggestions(text, suggestions)
            val base = withContext(Dispatchers.Default) {
                AppGraph.addressBase.find(text)
            }
            if (query != text) return@launch
            onBase(text, base)
        }
    }

    /** Оператор выбрал адрес из подсказок. */
    fun select(entry: AddressBaseEntry) {
        // Отменяем поиск: иначе он вернёт подсказки по старому запросу и
        // список снова вылезет поверх ввода.
        job?.cancel()
        onSuggestions(entry.address, emptyList())
        onBase(entry.address, entry)
    }

    /** Поиск больше не нужен: адрес сменили или карточка закрылась. */
    fun cancel() {
        job?.cancel()
    }

    private companion object {
        /** Задержка перед поиском подсказок: жмём букву — ищем по целому слову. */
        const val LOOKUP_DELAY_MS = 250L
    }
}
