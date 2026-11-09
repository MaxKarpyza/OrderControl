package ru.ordercontrol.data

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ordercontrol.domain.ContactEntry
import ru.ordercontrol.domain.ContactMatcher
import ru.ordercontrol.domain.PhoneNormalizer

/**
 * Телефонная книга: поиск имени по нормализованному номеру и создание
 * контакта без дубликатов в выбранном аккаунте.
 */
class ContactRepository(
    private val context: Context,
    private val settings: SettingsRepository,
) : ContactLookup {

    fun canReadContacts(): Boolean = hasPermission(Manifest.permission.READ_CONTACTS)

    fun canWriteContacts(): Boolean = hasPermission(Manifest.permission.WRITE_CONTACTS)

    override suspend fun findName(number: String): String? = withContext(Dispatchers.IO) {
        if (!canReadContacts()) return@withContext null
        if (PhoneNormalizer.normalize(number) == null) return@withContext null
        val resolver = context.contentResolver
        val projection = arrayOf(
            Phone.DISPLAY_NAME,
            Phone.NUMBER,
        )
        try {
            resolver.query(
                Phone.CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndexOrThrow(Phone.NUMBER)
                val entries = generateSequence {
                    if (cursor.moveToNext()) {
                        ContactEntry(
                            name = cursor.getString(nameIndex),
                            number = cursor.getString(numberIndex),
                        )
                    } else {
                        null
                    }
                }
                return@withContext ContactMatcher.findName(number, entries)
            }
        } catch (_: Exception) {
            return@withContext null
        }
        null
    }

    /**
     * Создаёт контакт, если такого номера ещё нет.
     * @return `true`, если контакт был создан.
     */
    override suspend fun createContactIfAbsent(name: String, number: String): Boolean =
        withContext(Dispatchers.IO) {
            val trimmedName = name.trim()
            val normalized = PhoneNormalizer.normalize(number) ?: return@withContext false
            if (!canWriteContacts()) return@withContext false
            val existing = findName(normalized)
            if (!ContactMatcher.shouldCreate(trimmedName, existing)) return@withContext false

            val account = settings.currentContactAccount()
            val operations = ArrayList<ContentProviderOperation>(3)
            operations += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_NAME, account?.name)
                .withValue(RawContacts.ACCOUNT_TYPE, account?.type)
                .build()
            operations += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, trimmedName)
                .build()
            operations += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, normalized)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()

            try {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
                true
            } catch (_: Exception) {
                false
            }
        }

    /** Google-аккаунты, доступные для записи контактов. */
    suspend fun googleAccounts(): List<ContactAccount> = withContext(Dispatchers.IO) {
        try {
            AccountManager.get(context)
                .getAccountsByType(GOOGLE_ACCOUNT_TYPE)
                .map { ContactAccount(name = it.name, type = it.type) }
                .distinctBy { it.name }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
