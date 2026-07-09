package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.BaseTypes
import android.provider.ContactsContract.CommonDataKinds.Event
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.text.Html
import android.text.format.DateUtils
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import dev.goodwy.rphone.DAY_SECONDS
import dev.goodwy.rphone.HOUR_SECONDS
import dev.goodwy.rphone.MINUTE_SECONDS
import dev.goodwy.rphone.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun isYesterday(timestamp: Long): Boolean {
    return DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS)
}

private fun isSameYear(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
}

private fun Context.getRelativeDay(timestamp: Long): String? {
    return when {
        DateUtils.isToday(timestamp) -> getString(R.string.today)
        isYesterday(timestamp) -> getString(R.string.yesterday)
        else -> null
    }
}

fun Context.formatDateHeader(timestamp: Long): String {
    val relative = getRelativeDay(timestamp)
    if (relative != null) return relative

    val pattern = if (isSameYear(timestamp, System.currentTimeMillis())) "MMMM d" else "MMMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

fun Context.formatDate(timestamp: Long, onlyTime: Boolean = false): String {
    val relative = getRelativeDay(timestamp)
    val isJustNow = isJustNow(timestamp)
    val time = isJustNow ?: android.text.format.DateFormat.getTimeFormat(this).format(Date(timestamp))
    return if (onlyTime) time
            else if (isJustNow != null) time
            else if (relative != null) "$relative, $time"
            else "${formatDateHeader(timestamp)}, $time"
}

private fun Context.isJustNow(timestamp: Long): String? {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < DateUtils.MINUTE_IN_MILLIS -> getString(R.string.just_now)
        else -> null
    }
}

fun formatDuration(durationSeconds: Long): String {
    return DateUtils.formatElapsedTime(durationSeconds)
}

fun formatPhoneNumber(number: String): String {
    return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
}

fun normalizePhoneNumber(number: String): String {
    return PhoneNumberUtils.normalizeNumber(number)
}

fun areNumbersEqual(num1: String?, num2: String?): Boolean {
    if (num1 == null || num2 == null) return false
    return PhoneNumberUtils.compare(num1, num2)
}

fun deduplicateNumbers(numbers: List<String>): List<String> {
    val unique = mutableListOf<String>()
    numbers.forEach { number ->
        val existingIndex = unique.indexOfFirst { areNumbersEqual(it, number) }
        if (existingIndex == -1) {
            unique.add(number)
        } else {
            // Prefer the number with a '+' or the longer one (usually more complete)
            val existing = unique[existingIndex]
            if (number.contains("+") && !existing.contains("+")) {
                unique[existingIndex] = number
            } else if (number.length > existing.length && (number.contains("+") == existing.contains("+"))) {
                unique[existingIndex] = number
            }
        }
    }
    return unique
}

fun getSystemVoicemailNumber(context: Context): String? {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
        try {
            val accounts = telecomManager.callCapablePhoneAccounts
            val defaultHandle =
                telecomManager.getDefaultOutgoingPhoneAccount(Uri.fromParts("tel", "123", null).scheme)

            val handle = defaultHandle ?: accounts.firstOrNull()
            if (handle != null) {
                val num = telecomManager.getVoiceMailNumber(handle)
                if (!num.isNullOrEmpty()) return num
            }
        } catch (e: SecurityException) {
        } catch (e: Exception) {}

        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val num = tm.voiceMailNumber
            if (!num.isNullOrEmpty()) return num
        } catch (e: SecurityException) {
        } catch (e: Exception) {}
    }
    return null
}

fun makeCall(context: Context, number: String, accountHandle: PhoneAccountHandle? = null, contactId: String? = null) {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    val uri = if (number.startsWith("voicemail:")) {
        number.toUri()
    } else {
        Uri.fromParts("tel", number, null)
    }
    val extras = Bundle()

    val prefs = PreferenceManager(context)
    if (contactId != null) {
        prefs.setLastUsedNumber(contactId, number)
    }

    var preferredHandle = accountHandle
    if (preferredHandle == null) {
        val accounts = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try { telecomManager.callCapablePhoneAccounts } catch (e: SecurityException) { emptyList() }
        } else emptyList()

        val favSim = contactId?.let { prefs.getFavoriteSim(it) }
        val favNum = contactId?.let { prefs.getFavoriteNumber(it) }

        preferredHandle = if (favSim != null && areNumbersEqual(number, favNum)) {
            accounts.find { it.id == favSim }
        } else null

        if (preferredHandle == null) {
            val defaultSim = prefs.getInt("default_sim", 0)
            if (defaultSim > 0 && accounts.size >= defaultSim) {
                preferredHandle = accounts[defaultSim - 1]
            }
        }
    }

    if (preferredHandle != null) {
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, preferredHandle)
    }

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        telecomManager.placeCall(uri, extras)
    } else {

        val intent = Intent(Intent.ACTION_DIAL, uri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}

/**
 * Places a call respecting the user's default SIM preference.
 * simPref: 0 = ask, 1 = SIM1 (index 0), 2 = SIM2 (index 1)
 * Returns true if a direct call was placed, false if sim picker should be shown.
 */
fun placeCallWithSimPreference(
    context: Context,
    number: String,
    simPref: Int,
    onShowSimPicker: () -> Unit
) {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    if (hasPhoneState) {
        val accounts = telecomManager.callCapablePhoneAccounts
        if (accounts.size > 1) {
            when (simPref) {
                1 -> makeCall(context, number, accounts[0])
                2 -> makeCall(context, number, accounts[1])
                else -> onShowSimPicker()
            }
        } else {
            makeCall(context, number)
        }
    } else {
        makeCall(context, number)
    }
}

fun openInContacts(context: Context, contactId: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
    }
    context.startActivity(intent)
}

fun openLink(context: Context, link: String) {
    val intent = Intent(Intent.ACTION_VIEW,
        link.toUri())
    context.startActivity(intent)
}

fun getAppVersion(context: Context): Pair<String, Long> {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

        val versionName = packageInfo.versionName ?: "Unknown"
        // PackageInfoCompat handles retrieving long version codes safely across old/new API levels
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

        Pair(versionName, versionCode)
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        Pair("Unknown", -1L)
    }
}

fun isAlreadyDefaultDialer(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    } else {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val defaultDialerPackage = telecomManager.defaultDialerPackage
        defaultDialerPackage == context.packageName
    }
}

fun getDefaultDialerIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    } else {
        Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        }
    }
}

// Goodwy
fun Context.formatSecondsToShortTimeString(totalSeconds: Int): String {
    val days = totalSeconds / DAY_SECONDS
    val hours = (totalSeconds % DAY_SECONDS) / HOUR_SECONDS
    val minutes = (totalSeconds % HOUR_SECONDS) / MINUTE_SECONDS
    val seconds = totalSeconds % MINUTE_SECONDS
    val timesString = StringBuilder()
    if (days > 0) {
        val daysString = String.format(resources.getString(R.string.days_letter), days)
        timesString.append("$daysString ")
    }

    if (hours > 0) {
        val hoursString = String.format(resources.getString(R.string.hours_letter), hours)
        timesString.append("$hoursString ")
    }

    if (minutes > 0) {
        val minutesString = String.format(resources.getString(R.string.minutes_letter), minutes)
        timesString.append("$minutesString ")
    }

    if (seconds > 0) {
        val secondsString = String.format(resources.getString(R.string.seconds_letter), seconds)
        timesString.append(secondsString)
    }

    var result = timesString.toString().trim()
    if (result.isEmpty()) {
        result = String.format(resources.getString(R.string.minutes_letter), 0)
    }
    return result
}

fun String.isLetter(): Boolean {
    return this.length == 1 && this[0].isLetter()
}

fun String.isEmoji(): Boolean {
    return matches(
        ("(?:[\uD83C\uDF00-\uD83D\uDDFF]|[\uD83E\uDD00-\uD83E\uDDFF]|" +
                "[\uD83D\uDE00-\uD83D\uDE4F]|[\uD83D\uDE80-\uD83D\uDEFF]|" +
                "[\u2600-\u26FF]\uFE0F?|[\u2700-\u27BF]\uFE0F?|\u24C2\uFE0F?|" +
                "[\uD83C\uDDE6-\uD83C\uDDFF]{1,2}|" +
                "[\uD83C\uDD70\uD83C\uDD71\uD83C\uDD7E\uD83C\uDD7F\uD83C\uDD8E\uD83C\uDD91-\uD83C\uDD9A]\uFE0F?|" +
                "[\u0023\u002A\u0030-\u0039]\uFE0F?\u20E3|[\u2194-\u2199\u21A9-\u21AA]\uFE0F?|[\u2B05-\u2B07\u2B1B\u2B1C\u2B50\u2B55]\uFE0F?|" +
                "[\u2934\u2935]\uFE0F?|[\u3030\u303D]\uFE0F?|[\u3297\u3299]\uFE0F?|" +
                "[\uD83C\uDE01\uD83C\uDE02\uD83C\uDE1A\uD83C\uDE2F\uD83C\uDE32-\uD83C\uDE3A\uD83C\uDE50\uD83C\uDE51]\uFE0F?|" +
                "[\u203C\u2049]\uFE0F?|[\u25AA\u25AB\u25B6\u25C0\u25FB-\u25FE]\uFE0F?|" +
                "[\u00A9\u00AE]\uFE0F?|[\u2122\u2139]\uFE0F?|\uD83C\uDC04\uFE0F?|\uD83C\uDCCF\uFE0F?|" +
                "[\u231A\u231B\u2328\u23CF\u23E9-\u23F3\u23F8-\u23FA]\uFE0F?)+").toRegex()
    )
}

fun getPhoneTypeText(context: Context, type: Int?, label: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> label ?: context.resources.getString(R.string.no_label)
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> context.resources.getString(R.string.home)
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> context.resources.getString(R.string.mobile)
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> context.resources.getString(R.string.main_number)
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> context.resources.getString(R.string.work)
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> context.resources.getString(R.string.work_fax)
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> context.resources.getString(R.string.home_fax)
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> context.resources.getString(R.string.pager)
        else -> context.resources.getString(R.string.other)
    }
}

fun getEventTypeText(context: Context, type: Int, label: String?): String {
    return when (type) {
        Event.TYPE_CUSTOM -> label ?: context.resources.getString(R.string.no_label)
        Event.TYPE_ANNIVERSARY -> context.resources.getString(R.string.anniversary)
        Event.TYPE_BIRTHDAY -> context.resources.getString(R.string.birthday)
        else -> context.resources.getString(R.string.other)
    }
}

fun getAddressTypeText(context: Context, type: Int, label: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM -> label ?: context.resources.getString(R.string.no_label)
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> context.resources.getString(R.string.home)
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> context.resources.getString(R.string.work)
        else -> context.resources.getString(R.string.other)
    }
}

fun getEmailTypeText(context: Context, type: Int, label: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> label ?: context.resources.getString(R.string.no_label)
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> context.resources.getString(R.string.home)
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> context.resources.getString(R.string.work)
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> context.resources.getString(R.string.mobile)
        else -> context.resources.getString(R.string.other)
    }
}

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    try {
        if (isOnMainThread()) {
            doToast(this, msg, length)
        } else {
            Handler(Looper.getMainLooper()).post {
                doToast(this, msg, length)
            }
        }
    } catch (_: Exception) {
    }
}

fun isOnMainThread() = Looper.myLooper() == Looper.getMainLooper()

private fun doToast(context: Context, message: String, length: Int) {
    if (context is Activity) {
        if (!context.isFinishing && !context.isDestroyed) {
            Toast.makeText(context, message, length).show()
        }
    } else {
        Toast.makeText(context, message, length).show()
    }
}

fun Context.copyToClipboard(text: String) {
    val clip = ClipData.newPlainText("Phone", text)
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

fun Context.getTextFromClipboard(): CharSequence? {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip
    return if (clip != null && clip.itemCount > 0) {
        clip.getItemAt(0).coerceToText(this)
    } else null
}

fun Color.darken(amount: Float = 0.2f): Color {
    return Color(
        red = this.red * (1 - amount),
        green = this.green * (1 - amount),
        blue = this.blue * (1 - amount),
        alpha = this.alpha
    )
}

//fun stringToMillis(dateString: String): Long? {
//    return try {
//        LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
//            .atStartOfDay()
//            .toInstant(java.time.ZoneOffset.UTC)
//            .toEpochMilli()
//    } catch (_: Exception) {
//        null
//    }
//}
fun stringToMillis(dateString: String): Long? {
    if (dateString.isBlank()) return null

    return try {
        when {
            // Date format: YYYY-MM-DD
            dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.parse(dateString)?.time
            }
            // Date format without the year: --MM-DD
            dateString.matches(Regex("--\\d{2}-\\d{2}")) -> {
                // We use `split` instead of `substring` for security reasons
                val datePart = dateString.substring(2) // "10-22"
                val parts = datePart.split("-")
                if (parts.size == 2) {
                    val month = parts[0]
                    val day = parts[1]
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    val dateWithYear = "$currentYear-$month-$day"
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdf.parse(dateWithYear)?.time
                } else {
                    null
                }
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

//fun millisToString(dateMillis: Long?): String {
//    if (dateMillis == null) return ""
//    return try {
//        val date = Date(dateMillis)
//        val instant = date.toInstant()
//        val localDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
//        localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
//    } catch (_: Exception) {
//        ""
//    }
//}
fun millisToString(dateMillis: Long?, originalFormat: String? = null): String {
    if (dateMillis == null) return ""

    // If the original format did not include the year, we return it in the format --MM-DD
    if (originalFormat?.startsWith("--") == true) {
        val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
        return "--${sdf.format(Date(dateMillis))}"
    }

    // Standard format with the year
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(dateMillis))
}

fun Context.getBlockedNumbers(): ArrayList<String> {
    val blockedNumbers = ArrayList<String>()
    if (!isAlreadyDefaultDialer(this)) {
        return blockedNumbers
    }

    val uri = BlockedNumbers.CONTENT_URI
    val projection = arrayOf(
        BlockedNumbers.COLUMN_ID,
        BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
        BlockedNumbers.COLUMN_E164_NUMBER
    )

    queryCursor(uri, projection) { cursor ->
//        val id = cursor.getLongValue(BlockedNumbers.COLUMN_ID)
        val number = cursor.getStringValue(BlockedNumbers.COLUMN_ORIGINAL_NUMBER) ?: ""
        val normalizedNumber = cursor.getStringValue(BlockedNumbers.COLUMN_E164_NUMBER) ?: number
        val comparableNumber = normalizedNumber.trimToComparableNumber()
        blockedNumbers.add(comparableNumber)
    }

    return blockedNumbers
}

// checks if string is a phone number
fun String.isPhoneNumber(): Boolean {
    return this.matches("^[0-9+\\-\\)\\( *#]+\$".toRegex())
}

// if we are comparing phone numbers, compare just the last 9 digits
fun String.trimToComparableNumber(): String {
    // don't trim if it's not a phone number
    if (!this.isPhoneNumber()) {
        return this
    }
    val normalizedNumber = normalizePhoneNumber(this)
    val startIndex = 0.coerceAtLeast(normalizedNumber.length - 9)
    return normalizedNumber.substring(startIndex)
}

fun Context.queryCursor(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    showErrors: Boolean = false,
    callback: (cursor: Cursor) -> Unit
) {
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    callback(cursor)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        if (showErrors) {
            toast(e.toString())
        }
    }
}

fun Cursor.getLongValue(key: String) = getLong(getColumnIndexOrThrow(key))

fun Cursor.getStringValue(key: String) = getString(getColumnIndexOrThrow(key))

fun Context.isPackageInstalled(packageName: String?): Boolean {
    if (packageName == null) return false
    return try {
        val packageManager = packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent != null && packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    } catch (e: Exception) {
        false
    }
}

@Composable
fun HtmlTextView(
    html: String,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                gravity = Gravity.CENTER
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                setTextColor(textColor)
            }
        },
        modifier = modifier,
        update = { view ->
            view.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            view.gravity = Gravity.CENTER
            view.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            view.setTextColor(textColor)
        }
    )
}