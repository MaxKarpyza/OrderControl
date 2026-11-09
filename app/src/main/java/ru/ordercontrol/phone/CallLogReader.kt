package ru.ordercontrol.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

/**
 * Чтение системного журнала: добор номера и `callLogId` после звонка,
 * а также сверка записей, удалённых оператором из истории.
 */
class CallLogReader(private val context: Context) : CallLogSource {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    /** Последние входящие/пропущенные/сброшенные звонки не раньше [sinceMillis]. */
    override fun latestIncomingSince(sinceMillis: Long): List<CallLogEntry> =
        incomingBetween(sinceMillis, Long.MAX_VALUE)

    /** Входящие звонки в интервале `[fromMillis, toMillis]`. */
    override fun incomingBetween(fromMillis: Long, toMillis: Long): List<CallLogEntry> {
        if (!hasPermission()) return emptyList()
        val selection = "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ? " +
            "AND ${CallLog.Calls.TYPE} IN (?, ?, ?)"
        val args = arrayOf(
            fromMillis.toString(),
            toMillis.toString(),
            CallLog.Calls.INCOMING_TYPE.toString(),
            CallLog.Calls.MISSED_TYPE.toString(),
            CallLog.Calls.REJECTED_TYPE.toString(),
        )
        val result = ArrayList<CallLogEntry>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                PROJECTION,
                selection,
                args,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (cursor.moveToNext()) {
                    result += CallLogEntry(
                        id = cursor.getLong(idIndex),
                        number = cursor.getString(numberIndex),
                        date = cursor.getLong(dateIndex),
                        type = cursor.getInt(typeIndex),
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return result
    }

    /**
     * Какие из переданных id ещё существуют в журнале.
     * `null` — нет разрешения, сверять нельзя.
     */
    override fun existingIds(ids: Collection<Long>): Set<Long>? {
        if (!hasPermission()) return null
        if (ids.isEmpty()) return emptySet()
        val existing = HashSet<Long>(ids.size)
        for (chunk in ids.distinct().chunked(QUERY_CHUNK)) {
            val placeholders = chunk.joinToString(separator = ",") { "?" }
            try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls._ID),
                    "${CallLog.Calls._ID} IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray(),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        existing += cursor.getLong(0)
                    }
                }
            } catch (_: SecurityException) {
                return null
            }
        }
        return existing
    }

    private companion object {
        val PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
        )
        const val QUERY_CHUNK = 400
    }
}
