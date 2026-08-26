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

    val pattern = if (isSameYear(timestamp, System.currentTimeMillis())) "d MMMM" else "d MMMM yyyy"
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
