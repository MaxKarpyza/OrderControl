package ru.ordercontrol.data

import ru.ordercontrol.data.db.AddressAlias
import ru.ordercontrol.data.db.AddressAliasDao

/** In-memory реализация [AddressAliasDao] для JVM-тестов. */
class FakeAddressAliasDao : AddressAliasDao {

    private val aliases = linkedMapOf<String, AddressAlias>()

    override suspend fun all(): List<AddressAlias> = aliases.values.toList()

    override suspend fun put(alias: AddressAlias) {
        aliases[alias.aliasNormalized] = alias
    }

    override suspend fun find(alias: String): AddressAlias? = aliases[alias]

    override suspend fun clear() {
        aliases.clear()
    }
}
