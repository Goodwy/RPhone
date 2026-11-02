package dev.goodwy.rphone.controller.util

import android.content.Context
import android.text.format.DateUtils
import dev.goodwy.rphone.DAY_SECONDS
import dev.goodwy.rphone.HOUR_SECONDS
import dev.goodwy.rphone.MINUTE_SECONDS
import dev.goodwy.rphone.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val timeFormatCache = ThreadLocal<Pair<Boolean, java.text.DateFormat>>()

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

fun Context.formatDateHeader(timestamp: Long, includeDayOfWeek: Boolean = false): String {
    val relative = getRelativeDay(timestamp)
    if (relative != null) return relative

    val locale = Locale.getDefault()
    val hasSameYear = isSameYear(timestamp, System.currentTimeMillis())
    val skeleton = if (hasSameYear) "MMMMd" else "yMMMMd"
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)

    val date = Date(timestamp)
    val datePart = SimpleDateFormat(pattern, locale).format(date)

    if (!includeDayOfWeek) return datePart

    val dayOfWeek = SimpleDateFormat("EEEE", locale).format(date)
    return "$datePart ($dayOfWeek)"
}

fun Context.formatTime(timestamp: Long): String {
    val is24 = android.text.format.DateFormat.is24HourFormat(this)
    var cached = timeFormatCache.get()
    if (cached == null || cached.first != is24) {
        cached = Pair(is24, android.text.format.DateFormat.getTimeFormat(this))
        timeFormatCache.set(cached)
    }
    return cached.second.format(Date(timestamp))
}

fun Context.formatDate(timestamp: Long, onlyTime: Boolean = false): String {
    val relative = getRelativeDay(timestamp)
    val isJustNow = isJustNow(timestamp)
    val time = isJustNow ?: formatTime(timestamp)
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

/**
 * Formats the event date from ContactsContract (YYYY-MM-DD or --MM-DD)
 * into a format that matches the device's locale.
 *
 * @param rawDate the raw event string
 * @param withYear whether to display the year (set to false for birthdays without a year)
 * @param showAge whether to display the age in parentheses (only for birthdays with a year)
 */
fun formatContactEventDate(
    rawDate: String,
    withYear: Boolean = true,
    showAge: Boolean = false
): String {
    val millis = stringToUtcMillis(rawDate) ?: return rawDate   // ← UTC
    val hasYear = !rawDate.startsWith("--") && withYear

    val pattern = if (hasYear) {
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMMd")
    } else {
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMd")
    }
    val formatted = SimpleDateFormat(pattern, Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")                   // ← UTC
    }.format(Date(millis))

    if (showAge && !rawDate.startsWith("--")) {
        val age = calculateAge(millis)
        if (age >= 0) return "$formatted ($age)"
    }
    return formatted
}

/**
 * Age as of the current date, taking into account whether the person's birthday has already occurred this year.
 * Returns -1 if the date is in the future.
 */
private fun calculateAge(birthMillis: Long): Int {
    val utc = TimeZone.getTimeZone("UTC")
    val today = Calendar.getInstance(utc)
    val birth = Calendar.getInstance(utc).apply { timeInMillis = birthMillis }

    var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
    val hadBirthdayThisYear =
        today.get(Calendar.DAY_OF_YEAR) >= birth.get(Calendar.DAY_OF_YEAR)
    if (!hadBirthdayThisYear) age--
    return if (age < 0) -1 else age
}

/**
 * Parses the ISO date from ContactsContract (YYYY-MM-DD or --MM-DD)
 * into milliseconds since UTC midnight - the format expected by the Material3 DatePicker.
 */
fun stringToUtcMillis(dateString: String): Long? {
    if (dateString.isBlank()) return null
    return try {
        when {
            dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(dateString)?.time
            }
            dateString.matches(Regex("--\\d{2}-\\d{2}")) -> {
                val datePart = dateString.substring(2) // "10-22"
                val year = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse("$year-$datePart")?.time
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Converts milliseconds since UTC midnight (from DatePicker) back into an ISO string
 * for ContactsContract. Preserves "no year" if originalFormat starts with "--".
 */
fun utcMillisToString(dateMillis: Long?, originalFormat: String? = null): String {
    if (dateMillis == null) return ""
    val sdf = SimpleDateFormat(
        if (originalFormat?.startsWith("--") == true) "MM-dd" else "yyyy-MM-dd",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val formatted = sdf.format(Date(dateMillis))
    return if (originalFormat?.startsWith("--") == true) "--$formatted" else formatted
}