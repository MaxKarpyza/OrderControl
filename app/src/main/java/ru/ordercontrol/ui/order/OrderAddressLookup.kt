package ru.ordercontrol.ui.order

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            onSuggestions(text, AppGraph.addressBase.search(text))
            onBase(text, AppGraph.addressBase.find(text))
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
