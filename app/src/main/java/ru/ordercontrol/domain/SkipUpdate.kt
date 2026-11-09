package ru.ordercontrol.domain

/**
 * Что делать с пометкой «не клиент» при сохранении формы.
 *
 * `Unchanged` важен: оператор мог не трогать переключатель, и тогда
 * существующая пометка не должна исчезнуть.
 */
sealed interface SkipUpdate {

    data object Unchanged : SkipUpdate

    data object Clear : SkipUpdate

    data class Set(val kind: BlacklistKind) : SkipUpdate
}
