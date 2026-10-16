package dev.goodwy.rphone.controller.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.goodwy.rphone.modal.data.Contact
import java.io.File
import java.util.EnumMap

object QrCodeUtils {
    fun generateQrCode(content: String, size: Int): Bitmap? {
        return try {
            val hints: MutableMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1
            
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateVCard(name: String, phone: String?, email: String?): String {
        return buildString {
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("FN:$name\r\n")
            if (!phone.isNullOrBlank()) append("TEL;TYPE=CELL:$phone\r\n")
            if (!email.isNullOrBlank()) append("EMAIL:$email\r\n")
            append("END:VCARD\r\n")
        }
    }

    fun generateVCard(contact: Contact): String {
        return buildString {
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            
            val fn = contact.displayName.ifBlank { "Contact" }
            append("FN:$fn\r\n")
            val n = "${contact.familyName};${contact.givenName};${contact.middleName};${contact.namePrefix};${contact.nameSuffix}"
            append("N:$n\r\n")
            
            if (contact.nickname.isNotBlank()) {
                append("NICKNAME:${contact.nickname}\r\n")
            }
            
            if (contact.company.isNotBlank()) {
                append("ORG:${contact.company}\r\n")
            }
            if (contact.jobTitle.isNotBlank()) {
                append("TITLE:${contact.jobTitle}\r\n")
            }
            
            if (contact.phoneDetails.isNotEmpty()) {
                for (p in contact.phoneDetails) {
                    val typeStr = when (p.type) {
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "HOME"
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "WORK"
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "CELL"
                        else -> "CELL"
                    }
                    append("TEL;TYPE=$typeStr:${p.number}\r\n")
                }
            } else {
                for (phone in contact.phoneNumbers) {
                    append("TEL;TYPE=CELL:$phone\r\n")
                }
            }
            
            for (e in contact.emails) {
                val typeStr = when (e.type) {
                    android.provider.ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "HOME"
                    android.provider.ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "WORK"
                    else -> "INTERNET"
                }
                append("EMAIL;TYPE=$typeStr:${e.value}\r\n")
            }
            
            for (a in contact.addresses) {
                val typeStr = when (a.type) {
                    android.provider.ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> "HOME"
                    android.provider.ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> "WORK"
                    else -> "POSTAL"
                }
                val formatted = a.formattedAddress.replace("\n", " ")
                append("ADR;TYPE=$typeStr:;;$formatted;;;;\r\n")
            }
            
            if (!contact.notes.isNullOrBlank()) {
                val cleanNote = contact.notes.replace("\n", "\\n")
                append("NOTE:$cleanNote\r\n")
            }
            
            append("END:VCARD\r\n")
        }
    }

    fun shareVCardFile(context: Context, contact: Contact) {
        try {
            val vCardStr = generateVCard(contact)
            val sanitizedName = contact.displayName.ifBlank { "Contact" }.replace("\\s+".toRegex(), "_")
            val fileName = "${sanitizedName}.vcf"
            val vcfFile = File(context.cacheDir, fileName)
            vcfFile.writeText(vCardStr, Charsets.UTF_8)
            
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                vcfFile
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/x-vcard"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, contact.displayName)
                clipData = ClipData.newRawUri("vCard", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(intent, context.getString(dev.goodwy.rphone.R.string.share))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "vCard") {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
