package dev.goodwy.rphone.controller

import android.annotation.SuppressLint
import android.provider.CallLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.model.`interface`.ICallLogRepository
import dev.goodwy.rphone.model.data.CallLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class AnalyticsTimeRange(val stringRes: Int) {
    TODAY(R.string.today),
    THIS_WEEK(R.string.this_week),
    THIS_MONTH(R.string.this_month),
    ALL_TIME(R.string.all_time)
}

data class TopContactStat(
    val number: String,
    val name: String,
    val photoUri: String?,
    val totalDurationSeconds: Long,
    val totalCalls: Int,
    val incomingCount: Int,
    val outgoingCount: Int,
    val missedCount: Int
)

data class ChartDataPoint(
    val label: String,
    val value: Int
)

data class CallAnalyticsSummary(
    val totalTalkTimeSeconds: Long = 0L,
    val totalCalls: Int = 0,
    val incomingCalls: Int = 0,
    val outgoingCalls: Int = 0,
    val missedCalls: Int = 0,
    val rejectedCalls: Int = 0,
    val avgDurationSeconds: Long = 0L,
    val topContacts: List<TopContactStat> = emptyList(),
    val hourlyDistribution: Map<Int, Int> = emptyMap(),
    val simUsage: Map<String, Long> = emptyMap(),
    val chartData: List<ChartDataPoint> = emptyList()
)

class CallAnalyticsViewModel(
    private val callLogRepo: ICallLogRepository,
    private val prefs: PreferenceManager
) : ViewModel() {

    private val _isTrackingEnabled = MutableStateFlow(prefs.isCallAnalyticsTrackingEnabled())
    val isTrackingEnabled: StateFlow<Boolean> = _isTrackingEnabled.asStateFlow()

    private val _selectedRange = MutableStateFlow(AnalyticsTimeRange.TODAY)
    val selectedRange: StateFlow<AnalyticsTimeRange> = _selectedRange.asStateFlow()

    private val _analytics = MutableStateFlow(CallAnalyticsSummary())
    val analytics: StateFlow<CallAnalyticsSummary> = _analytics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var cachedCallLogs: List<CallLogEntry>? = null

    init {
        viewModelScope.launch {
            prefs.settingsChanged.collect {
                val enabled = prefs.isCallAnalyticsTrackingEnabled()
                if (_isTrackingEnabled.value != enabled) {
                    _isTrackingEnabled.value = enabled
                    if (enabled) {
                        loadAnalytics(forceRefresh = true)
                    } else {
                        cachedCallLogs = null
                        _analytics.value = CallAnalyticsSummary()
                    }
                }
            }
        }
        if (_isTrackingEnabled.value) {
            loadAnalytics(forceRefresh = true)
        }
    }

    fun setAnalyticsTrackingEnabled(enabled: Boolean) {
        prefs.setCallAnalyticsTrackingEnabled(enabled)
        _isTrackingEnabled.value = enabled
        if (enabled) {
            loadAnalytics(forceRefresh = true)
        } else {
            cachedCallLogs = null
            _analytics.value = CallAnalyticsSummary()
        }
    }

    fun setTimeRange(range: AnalyticsTimeRange) {
        if (_selectedRange.value != range) {
            _selectedRange.value = range
            computeAnalytics()
        }
    }

    fun loadAnalytics(forceRefresh: Boolean = false) {
        if (!prefs.isCallAnalyticsTrackingEnabled()) {
            _analytics.value = CallAnalyticsSummary()
            _isLoading.value = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            if (forceRefresh || cachedCallLogs == null) {
                cachedCallLogs = callLogRepo.getCallLogs()
            }
            computeAnalyticsInternal()
            _isLoading.value = false
        }
    }

    private fun computeAnalytics() {
        if (!prefs.isCallAnalyticsTrackingEnabled()) {
            _analytics.value = CallAnalyticsSummary()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (cachedCallLogs == null) {
                _isLoading.value = true
                cachedCallLogs = callLogRepo.getCallLogs()
                _isLoading.value = false
            }
            computeAnalyticsInternal()
        }
    }

    private fun computeAnalyticsInternal() {
        if (!prefs.isCallAnalyticsTrackingEnabled()) {
            _analytics.value = CallAnalyticsSummary()
            return
        }
        val logs = cachedCallLogs ?: emptyList()
        val cutoff = calculateCutoffTime(_selectedRange.value)
        val filtered = if (cutoff > 0L) logs.filter { it.date >= cutoff } else logs

        var totalTalkTime = 0L
        var totalCalls = 0
        var incoming = 0
        var outgoing = 0
        var missed = 0
        var rejected = 0

        val hourly = mutableMapOf<Int, Int>()
        val simMap = mutableMapOf<String, Long>()
        val contactStatsMap = mutableMapOf<String, MutableContactAccumulator>()

        val chartMap = mutableMapOf<String, Int>()
        val cal = Calendar.getInstance()

        for (entry in filtered) {
            val callCount = entry.count
            totalCalls += callCount
            totalTalkTime += entry.duration

            // Call types
            val typesToCheck = if (entry.types.isNotEmpty()) entry.types else listOf(entry.type)
            for (t in typesToCheck) {
                when (t) {
                    CallLog.Calls.INCOMING_TYPE -> incoming++
                    CallLog.Calls.OUTGOING_TYPE -> outgoing++
                    CallLog.Calls.MISSED_TYPE -> missed++
                    CallLog.Calls.REJECTED_TYPE -> rejected++
                }
            }

            // Hourly distribution
            cal.timeInMillis = entry.date
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hourly[hour] = (hourly[hour] ?: 0) + callCount

            // SIM usage
            val sim = entry.simLabel ?: "SIM_?"
            simMap[sim] = (simMap[sim] ?: 0L) + entry.duration

            // Contact aggregation
            val contactKey = entry.number //entry.contactId ?: entry.number
            val acc = contactStatsMap.getOrPut(contactKey) {
                MutableContactAccumulator(
                    number = entry.number,
                    name = entry.name ?: entry.number,
                    photoUri = entry.photoUri
                )
            }
            acc.totalDuration += entry.duration
            acc.totalCalls += callCount
            for (t in typesToCheck) {
                when (t) {
                    CallLog.Calls.INCOMING_TYPE -> acc.incoming++
                    CallLog.Calls.OUTGOING_TYPE -> acc.outgoing++
                    CallLog.Calls.MISSED_TYPE -> acc.missed++
                }
            }

            // Aggregation for charts
            val chartKey = getChartBucketKey(_selectedRange.value, entry.date, cal)
            chartMap[chartKey] = (chartMap[chartKey] ?: 0) + callCount
        }

        // Prepare chart data with fixed buckets and correct ordering
        val chartData = prepareChartData(_selectedRange.value, chartMap)

        val topContacts = contactStatsMap.values
            .sortedByDescending { it.totalDuration }
            .take(20)
            .map { acc ->
                TopContactStat(
                    number = acc.number,
                    name = acc.name,
                    photoUri = acc.photoUri,
                    totalDurationSeconds = acc.totalDuration,
                    totalCalls = acc.totalCalls,
                    incomingCount = acc.incoming,
                    outgoingCount = acc.outgoing,
                    missedCount = acc.missed
                )
            }

        val avgDuration = if (totalCalls > 0) totalTalkTime / totalCalls else 0L

        _analytics.value = CallAnalyticsSummary(
            totalTalkTimeSeconds = totalTalkTime,
            totalCalls = totalCalls,
            incomingCalls = incoming,
            outgoingCalls = outgoing,
            missedCalls = missed,
            rejectedCalls = rejected,
            avgDurationSeconds = avgDuration,
            topContacts = topContacts,
            hourlyDistribution = hourly,
            simUsage = simMap,
            chartData = chartData
        )
    }

    @SuppressLint("DefaultLocale")
    private fun getChartBucketKey(range: AnalyticsTimeRange, date: Long, cal: Calendar): String {
        cal.timeInMillis = date
        return when (range) {
            // Group them in 2-hour intervals (0–1, 2–3, 4–5, etc.)
            AnalyticsTimeRange.TODAY -> "${cal.get(Calendar.HOUR_OF_DAY) / 2}"
            AnalyticsTimeRange.THIS_WEEK, AnalyticsTimeRange.THIS_MONTH -> {
                "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}-${String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))}"
            }
            AnalyticsTimeRange.ALL_TIME -> {
                "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun prepareChartData(range: AnalyticsTimeRange, dataMap: Map<String, Int>): List<ChartDataPoint> {
        val list = mutableListOf<ChartDataPoint>()
        val cal = Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())

        when (range) {
            AnalyticsTimeRange.TODAY -> {
                // 12 columns (2 hours each)
                for (h in 0..11) {
                    val key = "$h"
                    val label = String.format("%02d", h * 2)
                    list.add(ChartDataPoint(label = label, value = dataMap[key] ?: 0))
                }
            }
            AnalyticsTimeRange.THIS_WEEK -> {
                val dayFormat = java.text.SimpleDateFormat("EE", java.util.Locale.getDefault())
                for (i in 6 downTo 0) {
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    val key = "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}-${String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))}"
                    val label = dayFormat.format(cal.time)
                    list.add(ChartDataPoint(label = label, value = dataMap[key] ?: 0))
                    cal.add(Calendar.DAY_OF_YEAR, i)
                }
            }
            AnalyticsTimeRange.THIS_MONTH -> {
                val daysInBucket = 6
                val calEnd = Calendar.getInstance()

                // Let's move from the past to the present (5 segments of 6 days each = 30 days)
                for (b in 4 downTo 0) {
                    calEnd.timeInMillis = System.currentTimeMillis()
                    calEnd.add(Calendar.DAY_OF_YEAR, -(b * daysInBucket))

                    val calStart = calEnd.clone() as Calendar
                    calStart.add(Calendar.DAY_OF_YEAR, -daysInBucket)

                    var sum = 0
                    dataMap.forEach { (key, value) ->
                        val parts = key.split("-")
                        if (parts.size == 3) {
                            val y = parts[0].toInt()
                            val m = parts[1].toInt() - 1
                            val d = parts[2].toInt()
                            val c = Calendar.getInstance().apply { set(y, m, d) }
                            if (c.timeInMillis in calStart.timeInMillis..calEnd.timeInMillis) {
                                sum += value
                            }
                        }
                    }

                    // We create a signature in the format "1-7," "8-14," and so on.
                    val startDay = calStart.get(Calendar.DAY_OF_MONTH)
                    val endDay = calEnd.get(Calendar.DAY_OF_MONTH)
                    val label = "$startDay-$endDay"
                    list.add(ChartDataPoint(label = label, value = sum))
                }
            }
            AnalyticsTimeRange.ALL_TIME -> {
                val monthFormat = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
                for (i in 5 downTo 0) {
                    cal.add(Calendar.MONTH, -i)
                    val key = "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"
                    list.add(ChartDataPoint(label = monthFormat.format(cal.time), value = dataMap[key] ?: 0))
                    cal.add(Calendar.MONTH, i)
                }
            }
        }
        return list
    }

    private fun calculateCutoffTime(range: AnalyticsTimeRange): Long {
        val cal = Calendar.getInstance()
        return when (range) {
            AnalyticsTimeRange.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            AnalyticsTimeRange.THIS_WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            }
            AnalyticsTimeRange.THIS_MONTH -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.timeInMillis
            }
            AnalyticsTimeRange.ALL_TIME -> 0L
        }
    }

    private class MutableContactAccumulator(
        val number: String,
        val name: String,
        val photoUri: String?,
        var totalDuration: Long = 0L,
        var totalCalls: Int = 0,
        var incoming: Int = 0,
        var outgoing: Int = 0,
        var missed: Int = 0
    )
}