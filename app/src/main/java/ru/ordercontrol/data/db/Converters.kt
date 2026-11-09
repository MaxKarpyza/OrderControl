package ru.ordercontrol.data.db

import androidx.room.TypeConverter
import ru.ordercontrol.domain.AliasKind
import ru.ordercontrol.domain.BlacklistKind
import ru.ordercontrol.domain.CloseReason
import ru.ordercontrol.domain.DeliveryDay

class Converters {

    @TypeConverter
    fun aliasKindToString(value: AliasKind?): String? = value?.name

    @TypeConverter
    fun stringToAliasKind(value: String?): AliasKind? =
        value?.let { runCatching { AliasKind.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun closeReasonToString(value: CloseReason?): String? = value?.name

    @TypeConverter
    fun stringToCloseReason(value: String?): CloseReason? =
        value?.let { runCatching { CloseReason.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun kindToString(value: BlacklistKind?): String? = value?.name

    @TypeConverter
    fun stringToKind(value: String?): BlacklistKind? =
        value?.let { runCatching { BlacklistKind.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun deliveryDayToString(value: DeliveryDay?): String? = value?.name

    @TypeConverter
    fun stringToDeliveryDay(value: String?): DeliveryDay? =
        value?.let { runCatching { DeliveryDay.valueOf(it) }.getOrNull() }
}
