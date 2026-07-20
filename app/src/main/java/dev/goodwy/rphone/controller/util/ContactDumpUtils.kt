package dev.goodwy.rphone.controller.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds

// Retrieve all contact data for error analysis
object ContactDumpUtils {
    private const val TAG = "ContactDump"

    data class FullContactDump(
        val contactId: String,
        val rawContacts: List<RawContactDump>,
        val aggregatedData: Map<String, String>,
        val aggregationExceptions: List<AggregationExceptionDump>
    )

    data class RawContactDump(
        val rawContactId: String,
        val accountName: String?,
        val accountType: String?,
        val sourceId: String?,
        val version: String?,
        val dirty: Int,
        val deleted: Int,
        val contactId: String?,
        val dataRows: List<DataRowDump>
    )

    data class DataRowDump(
        val mimeType: String,
        val data1: String?,
        val data2: String?,
        val data3: String?,
        val data4: String?,
        val data5: String?,
        val data6: String?,
        val data7: String?,
        val data8: String?,
        val data9: String?,
        val data10: String?,
        val data11: String?,
        val data12: String?,
        val data13: String?,
        val data14: String?,
        val data15: String?,
        val isPrimary: Int,
        val isSuperPrimary: Int,
        val rawContactId: String?,
        val contactId: String?
    )

    data class AggregationExceptionDump(
        val rawContactId1: String,
        val rawContactId2: String,
        val type: Int,
        val typeName: String
    )

    fun dumpFullContact(
        contentResolver: ContentResolver,
        contactId: String
    ): String {
        if (contactId.startsWith("p")) {
            return "Private contact - cannot dump system data"
        }

        val dump = getFullContactDump(contentResolver, contactId)
        return formatDump(dump)
    }

    @SuppressLint("Range")
    private fun getFullContactDump(
        contentResolver: ContentResolver,
        contactId: String
    ): FullContactDump {
        val rawContacts = mutableListOf<RawContactDump>()
        val aggregationExceptions = mutableListOf<AggregationExceptionDump>()
        val aggregatedData = mutableMapOf<String, String>()

        // 1. Получаем все RawContacts
        val rawContactIds = mutableListOf<String>()
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.SOURCE_ID,
                ContactsContract.RawContacts.VERSION,
                ContactsContract.RawContacts.DIRTY,
                ContactsContract.RawContacts.DELETED,
                ContactsContract.RawContacts.CONTACT_ID
            ),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
            val accountNameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
            val accountTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val sourceIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts.SOURCE_ID)
            val versionIdx = cursor.getColumnIndex(ContactsContract.RawContacts.VERSION)
            val dirtyIdx = cursor.getColumnIndex(ContactsContract.RawContacts.DIRTY)
            val deletedIdx = cursor.getColumnIndex(ContactsContract.RawContacts.DELETED)
            val contactIdIdx = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)

            while (cursor.moveToNext()) {
                val rawId = cursor.getString(idIdx)
                rawContactIds.add(rawId)

                val dataRows = getDataRowsForRawContact(contentResolver, rawId)

                rawContacts.add(
                    RawContactDump(
                        rawContactId = rawId,
                        accountName = cursor.getString(accountNameIdx),
                        accountType = cursor.getString(accountTypeIdx),
                        sourceId = cursor.getString(sourceIdIdx),
                        version = cursor.getString(versionIdx),
                        dirty = cursor.getInt(dirtyIdx),
                        deleted = cursor.getInt(deletedIdx),
                        contactId = cursor.getString(contactIdIdx),
                        dataRows = dataRows
                    )
                )
            }
        }

        // 2. Получаем агрегированные данные
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val columnNames = cursor.columnNames
            if (cursor.moveToFirst()) {
                columnNames.forEach { columnName ->
                    val value = cursor.getString(cursor.getColumnIndex(columnName))
                    aggregatedData[columnName] = value ?: "NULL"
                }
            }
        }

        // 3. Получаем AggregationExceptions
        if (rawContactIds.size > 1) {
            val ids = rawContactIds.map { it.toLong() }
            for (i in 0 until ids.size) {
                for (j in i + 1 until ids.size) {
                    val id1 = minOf(ids[i], ids[j])
                    val id2 = maxOf(ids[i], ids[j])

                    contentResolver.query(
                        ContactsContract.AggregationExceptions.CONTENT_URI,
                        arrayOf(
                            ContactsContract.AggregationExceptions.RAW_CONTACT_ID1,
                            ContactsContract.AggregationExceptions.RAW_CONTACT_ID2,
                            ContactsContract.AggregationExceptions.TYPE
                        ),
                        "${ContactsContract.AggregationExceptions.RAW_CONTACT_ID1} = ? AND " +
                                "${ContactsContract.AggregationExceptions.RAW_CONTACT_ID2} = ?",
                        arrayOf(id1.toString(), id2.toString()),
                        null
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val typeIdx = cursor.getColumnIndex(ContactsContract.AggregationExceptions.TYPE)
                            val type = cursor.getInt(typeIdx)

                            aggregationExceptions.add(
                                AggregationExceptionDump(
                                    rawContactId1 = id1.toString(),
                                    rawContactId2 = id2.toString(),
                                    type = type,
                                    typeName = getAggregationTypeName(type)
                                )
                            )
                        }
                    }
                }
            }
        }

        return FullContactDump(
            contactId = contactId,
            rawContacts = rawContacts,
            aggregatedData = aggregatedData,
            aggregationExceptions = aggregationExceptions
        )
    }

    private fun getDataRowsForRawContact(
        contentResolver: ContentResolver,
        rawContactId: String
    ): List<DataRowDump> {
        val rows = mutableListOf<DataRowDump>()

        val projection = arrayOf(
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA5,
            ContactsContract.Data.DATA6,
            ContactsContract.Data.DATA7,
            ContactsContract.Data.DATA8,
            ContactsContract.Data.DATA9,
            ContactsContract.Data.DATA10,
            ContactsContract.Data.DATA11,
            ContactsContract.Data.DATA12,
            ContactsContract.Data.DATA13,
            ContactsContract.Data.DATA14,
            ContactsContract.Data.DATA15,
            ContactsContract.Data.IS_PRIMARY,
            ContactsContract.Data.IS_SUPER_PRIMARY,
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.CONTACT_ID
        )

        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
            arrayOf(rawContactId),
            null
        )?.use { cursor ->
            val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
            val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
            val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
            val data4Idx = cursor.getColumnIndex(ContactsContract.Data.DATA4)
            val data5Idx = cursor.getColumnIndex(ContactsContract.Data.DATA5)
            val data6Idx = cursor.getColumnIndex(ContactsContract.Data.DATA6)
            val data7Idx = cursor.getColumnIndex(ContactsContract.Data.DATA7)
            val data8Idx = cursor.getColumnIndex(ContactsContract.Data.DATA8)
            val data9Idx = cursor.getColumnIndex(ContactsContract.Data.DATA9)
            val data10Idx = cursor.getColumnIndex(ContactsContract.Data.DATA10)
            val data11Idx = cursor.getColumnIndex(ContactsContract.Data.DATA11)
            val data12Idx = cursor.getColumnIndex(ContactsContract.Data.DATA12)
            val data13Idx = cursor.getColumnIndex(ContactsContract.Data.DATA13)
            val data14Idx = cursor.getColumnIndex(ContactsContract.Data.DATA14)
            val data15Idx = cursor.getColumnIndex(ContactsContract.Data.DATA15)
            val isPrimaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_PRIMARY)
            val isSuperPrimaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_SUPER_PRIMARY)
            val rawContactIdIdx = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
            val contactIdIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)

            while (cursor.moveToNext()) {
                rows.add(
                    DataRowDump(
                        mimeType = cursor.getString(mimeIdx) ?: "",
                        data1 = cursor.getString(data1Idx),
                        data2 = cursor.getString(data2Idx),
                        data3 = cursor.getString(data3Idx),
                        data4 = cursor.getString(data4Idx),
                        data5 = cursor.getString(data5Idx),
                        data6 = cursor.getString(data6Idx),
                        data7 = cursor.getString(data7Idx),
                        data8 = cursor.getString(data8Idx),
                        data9 = cursor.getString(data9Idx),
                        data10 = cursor.getString(data10Idx),
                        data11 = cursor.getString(data11Idx),
                        data12 = cursor.getString(data12Idx),
                        data13 = cursor.getString(data13Idx),
                        data14 = cursor.getString(data14Idx),
                        data15 = cursor.getString(data15Idx),
                        isPrimary = cursor.getInt(isPrimaryIdx),
                        isSuperPrimary = cursor.getInt(isSuperPrimaryIdx),
                        rawContactId = cursor.getString(rawContactIdIdx),
                        contactId = cursor.getString(contactIdIdx)
                    )
                )
            }
        }

        return rows
    }

    private fun getAggregationTypeName(type: Int): String {
        return when (type) {
            ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER -> "KEEP_TOGETHER"
            ContactsContract.AggregationExceptions.TYPE_KEEP_SEPARATE -> "KEEP_SEPARATE"
            ContactsContract.AggregationExceptions.TYPE_AUTOMATIC -> "AUTOMATIC"
            else -> "UNKNOWN($type)"
        }
    }

    private fun formatDump(dump: FullContactDump): String {
        val sb = StringBuilder()

        sb.appendLine("═══════════════════════════════════════════════════════════")
        sb.appendLine("📋 FULL CONTACT DUMP")
        sb.appendLine("═══════════════════════════════════════════════════════════")
        sb.appendLine("Contact ID: ${dump.contactId}")
        sb.appendLine("Raw Contacts: ${dump.rawContacts.size}")
        sb.appendLine()

        // Aggregated data
        sb.appendLine("📊 AGGREGATED DATA:")
        sb.appendLine("─────────────────────────────────────────────────────────")
        dump.aggregatedData.forEach { (key, value) ->
            sb.appendLine("  $key = $value")
        }
        sb.appendLine()

        // Aggregation Exceptions
        if (dump.aggregationExceptions.isNotEmpty()) {
            sb.appendLine("🔗 AGGREGATION EXCEPTIONS:")
            sb.appendLine("─────────────────────────────────────────────────────────")
            dump.aggregationExceptions.forEach { exception ->
                sb.appendLine("  ${exception.rawContactId1} ↔ ${exception.rawContactId2}: ${exception.typeName}")
            }
            sb.appendLine()
        }

        // Raw Contacts
        dump.rawContacts.forEachIndexed { index, rawContact ->
            sb.appendLine("📱 RAW CONTACT #${index + 1} (ID: ${rawContact.rawContactId})")
            sb.appendLine("─────────────────────────────────────────────────────────")
            sb.appendLine("  Account: ${rawContact.accountName} (${rawContact.accountType})")
            sb.appendLine("  Source ID: ${rawContact.sourceId ?: "NULL"}")
            sb.appendLine("  Version: ${rawContact.version ?: "NULL"}")
            sb.appendLine("  Dirty: ${rawContact.dirty}")
            sb.appendLine("  Deleted: ${rawContact.deleted}")
            sb.appendLine("  Contact ID: ${rawContact.contactId ?: "NULL"}")
            sb.appendLine()
            sb.appendLine("  📝 DATA ROWS:")

            rawContact.dataRows.forEachIndexed { rowIndex, row ->
                val mimeType = row.mimeType
                val mimeName = getMimeTypeName(mimeType)
                sb.appendLine("    ${rowIndex + 1}. $mimeName ($mimeType)")
                sb.appendLine("       DATA1: ${row.data1 ?: "NULL"}")
                sb.appendLine("       DATA2: ${row.data2 ?: "NULL"}")
                sb.appendLine("       DATA3: ${row.data3 ?: "NULL"}")
                sb.appendLine("       DATA4: ${row.data4 ?: "NULL"}")
                sb.appendLine("       DATA5: ${row.data5 ?: "NULL"}")
                sb.appendLine("       DATA6: ${row.data6 ?: "NULL"}")
                sb.appendLine("       DATA7: ${row.data7 ?: "NULL"}")
                sb.appendLine("       DATA8: ${row.data8 ?: "NULL"}")
                sb.appendLine("       DATA9: ${row.data9 ?: "NULL"}")
                sb.appendLine("       DATA10: ${row.data10 ?: "NULL"}")
                sb.appendLine("       DATA11: ${row.data11 ?: "NULL"}")
                sb.appendLine("       DATA12: ${row.data12 ?: "NULL"}")
                sb.appendLine("       DATA13: ${row.data13 ?: "NULL"}")
                sb.appendLine("       DATA14: ${row.data14 ?: "NULL"}")
                sb.appendLine("       DATA15: ${row.data15 ?: "NULL"}")
                sb.appendLine("       IS_PRIMARY: ${row.isPrimary}")
                sb.appendLine("       IS_SUPER_PRIMARY: ${row.isSuperPrimary}")
                sb.appendLine()
            }
        }

        sb.appendLine("═══════════════════════════════════════════════════════════")
        return sb.toString()
    }

    private fun getMimeTypeName(mimeType: String): String {
        return when (mimeType) {
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> "STRUCTURED_NAME"
            CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> "PHONE"
            CommonDataKinds.Email.CONTENT_ITEM_TYPE -> "EMAIL"
            CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> "STRUCTURED_POSTAL"
            CommonDataKinds.Event.CONTENT_ITEM_TYPE -> "EVENT"
            CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> "ORGANIZATION"
            CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> "NICKNAME"
            CommonDataKinds.Photo.CONTENT_ITEM_TYPE -> "PHOTO"
            CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> "GROUP_MEMBERSHIP"
            CommonDataKinds.Relation.CONTENT_ITEM_TYPE -> "RELATION"
            CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE -> "SIP_ADDRESS"
            CommonDataKinds.Im.CONTENT_ITEM_TYPE -> "IM"
            CommonDataKinds.Note.CONTENT_ITEM_TYPE -> "NOTE"
            CommonDataKinds.Website.CONTENT_ITEM_TYPE -> "WEBSITE"
            else -> "UNKNOWN"
        }
    }
}