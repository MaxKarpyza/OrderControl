package ru.ordercontrol.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CallRecord::class, BlacklistEntry::class, AddressAlias::class],
    version = 7,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun callRecordDao(): CallRecordDao

    abstract fun blacklistDao(): BlacklistDao

    abstract fun addressAliasDao(): AddressAliasDao

    companion object {
        private const val NAME = "ordercontrol.db"

        /** Колонки `call_records` в модели версии 2. */
        private val CALL_RECORD_COLUMNS = listOf(
            "id",
            "phoneNormalized",
            "displayName",
            "callLogId",
            "incomingAt",
            "bottlesText",
            "addressText",
            "isProcessed",
            "processedAt",
            "isSkipped",
            "isSilent",
            "closeReason",
            "sheetMatchKey",
        )

        /**
         * 1 → 2: ключ строки таблицы у записи и таблица синонимов адресов.
         *
         * В базе версии 1 была колонка `durationSec`, которой нет в модели
         * (длительность звонка приложению не нужна). Room сверяет схему после
         * миграции, поэтому лишняя колонка приводит к
         * `Migration didn't properly handle: call_records`.
         *
         * Таблица пересоздаётся, а не меняется через `ALTER TABLE … DROP COLUMN`:
         * этот синтаксис появился в SQLite 3.35, а на Android 11 (minSdk 30)
         * доступен SQLite 3.28.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingColumns = tableColumns(db, "call_records")
                val copied = CALL_RECORD_COLUMNS.filter { it in existingColumns }

                db.execSQL("DROP TABLE IF EXISTS call_records_new")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS call_records_new (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`phoneNormalized` TEXT, " +
                        "`displayName` TEXT, " +
                        "`callLogId` INTEGER, " +
                        "`incomingAt` INTEGER NOT NULL, " +
                        "`bottlesText` TEXT NOT NULL, " +
                        "`addressText` TEXT NOT NULL, " +
                        "`isProcessed` INTEGER NOT NULL, " +
                        "`processedAt` INTEGER, " +
                        "`isSkipped` INTEGER NOT NULL, " +
                        "`isSilent` INTEGER NOT NULL, " +
                        "`closeReason` TEXT, " +
                        "`sheetMatchKey` TEXT)",
                )

                if (copied.isNotEmpty()) {
                    val columns = copied.joinToString(", ") { "`$it`" }
                    db.execSQL(
                        "INSERT INTO call_records_new ($columns) SELECT $columns FROM call_records",
                    )
                }

                db.execSQL("DROP TABLE call_records")
                db.execSQL("ALTER TABLE call_records_new RENAME TO call_records")

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_call_records_phoneNormalized " +
                        "ON call_records(phoneNormalized)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_call_records_isProcessed " +
                        "ON call_records(isProcessed)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_call_records_callLogId " +
                        "ON call_records(callLogId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_call_records_sheetMatchKey " +
                        "ON call_records(sheetMatchKey)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS address_aliases (" +
                        "`aliasNormalized` TEXT NOT NULL, " +
                        "`canonicalNormalized` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`aliasNormalized`))",
                )
            }
        }

        /**
         * 2 → 3: у пары адресов появилось решение оператора — синоним или
         * отказ. Старые записи все были синонимами.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = tableColumns(db, "address_aliases")
                if ("kind" !in columns) {
                    db.execSQL(
                        "ALTER TABLE address_aliases ADD COLUMN kind TEXT NOT NULL " +
                            "DEFAULT 'MATCH'",
                    )
                }
            }
        }

        /** 3 → 4: у заказа появилась отметка «на следующий день». */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = tableColumns(db, "call_records")
                if ("isNextDay" !in columns) {
                    db.execSQL(
                        "ALTER TABLE call_records ADD COLUMN isNextDay INTEGER NOT NULL " +
                            "DEFAULT 0",
                    )
                }
            }
        }

        /**
         * 4 → 5: сохраняем, был звонок принят или пропущен. У старых записей
         * состояние неизвестно, поэтому колонка допускает `NULL`.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = tableColumns(db, "call_records")
                if ("isAnswered" !in columns) {
                    db.execSQL("ALTER TABLE call_records ADD COLUMN isAnswered INTEGER")
                }
            }
        }

        /**
         * 5 → 6: вместо галочки «на следующий день» — выбранный день доставки.
         * Старые отмеченные заказы считаются заказами на завтра.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = tableColumns(db, "call_records")
                if ("deliveryDay" !in columns) {
                    db.execSQL("ALTER TABLE call_records ADD COLUMN deliveryDay TEXT")
                }
                db.execSQL(
                    "UPDATE call_records SET deliveryDay = 'TOMORROW' " +
                        "WHERE isNextDay = 1 AND deliveryDay IS NULL",
                )
            }
        }

        /**
         * 6 → 7: комментарий оператора и отметка о записи в Google Таблицу.
         * Данные старых записей не переносим: количество и адрес остаются как
         * были, комментарий у них пустой.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = tableColumns(db, "call_records")
                if ("comment" !in columns) {
                    db.execSQL(
                        "ALTER TABLE call_records ADD COLUMN comment TEXT NOT NULL DEFAULT ''",
                    )
                }
                if ("withPhoneInComment" !in columns) {
                    db.execSQL(
                        "ALTER TABLE call_records ADD COLUMN withPhoneInComment INTEGER NOT NULL DEFAULT 0",
                    )
                }
                if ("sheetWrittenAt" !in columns) {
                    db.execSQL("ALTER TABLE call_records ADD COLUMN sheetWrittenAt INTEGER")
                }
            }
        }

        private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> =
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                .build()
    }
}
