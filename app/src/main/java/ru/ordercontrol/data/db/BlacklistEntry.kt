package ru.ordercontrol.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.ordercontrol.domain.BlacklistKind

@Entity(tableName = "blacklist")
data class BlacklistEntry(
    @PrimaryKey val phoneNormalized: String,
    val kind: BlacklistKind,
    val createdAt: Long,
)
