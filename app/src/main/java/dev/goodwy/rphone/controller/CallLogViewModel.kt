package dev.goodwy.rphone.controller

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.CallLogFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class CallLogViewModel(
    application: Application,
    private val callLogRepo: ICallLogRepository,
    private val contentResolver: ContentResolver,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application) {

    private val _allCallLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val allCallLogs: StateFlow<List<CallLogEntry>> = _allCallLogs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFilter = MutableStateFlow(CallLogFilter.All)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _filteredLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val filteredLogs: StateFlow<List<CallLogEntry>> = _filteredLogs.asStateFlow()
    private var filterJob: Job? = null

    // In-memory cache
    @Volatile private var cachedLogs: List<CallLogEntry> = emptyList()
    @Volatile private var isFetching = false
    private var debounceJob: Job? = null

    // Disk cache file
    private val cacheFile: File by lazy {
        File(application.cacheDir, "call_logs_cache.json")
    }

    private val callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            debounceJob?.cancel()
            debounceJob = viewModelScope.launch {
                delay(300.milliseconds)
                fetchLogs(forceRefresh = true)
            }
        }
    }

    private fun hasReadCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    init {
        if (hasReadCallLogPermission()) {
            contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                callLogObserver
            )

            // Step 1: serve disk cache immediately so UI is instant
            viewModelScope.launch {
                val diskCache = loadFromDisk()
                if (diskCache.isNotEmpty()) {
                    cachedLogs = diskCache
                    _allCallLogs.value = diskCache
                }
                // Step 2: refresh from provider in background
                fetchLogsInternal()
            }
        } else {
            _isLoading.value = false
        }

        viewModelScope.launch {
            combine(
                _allCallLogs,
                _selectedFilter,
                preferenceManager.settingsChanged
            ) { logs, filter, _ ->
                val blockVisibility = preferenceManager.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0)
                Triple(logs, filter, blockVisibility)
            }.collect { (logs, filter, blockVisibility) ->
                filterLogs(logs, filter, blockVisibility)
            }
        }
    }

    private fun filterLogs(logs: List<CallLogEntry>, filter: CallLogFilter, blockVisibility: Int) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val baseLogs = if (blockVisibility == 0) logs.filter { !it.isBlocked } else logs

            val result = when (filter) {
                CallLogFilter.All -> baseLogs
                CallLogFilter.Missed -> baseLogs.filter { it.type == CallLog.Calls.MISSED_TYPE }
                CallLogFilter.Incoming -> baseLogs.filter { it.type == CallLog.Calls.INCOMING_TYPE }
                CallLogFilter.Outgoing -> baseLogs.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
                CallLogFilter.Rejected -> baseLogs.filter { it.type == CallLog.Calls.REJECTED_TYPE }
                CallLogFilter.Contacts -> baseLogs.filter { it.contactId != null }
            }
            _filteredLogs.value = result
        }
    }

    fun setFilter(newFilter: CallLogFilter) {
        _selectedFilter.value = if (newFilter == _selectedFilter.value) {
            CallLogFilter.All
        } else {
            newFilter
        }
    }

    override fun onCleared() {
        debounceJob?.cancel()
        filterJob?.cancel()
        super.onCleared()
        try {
            contentResolver.unregisterContentObserver(callLogObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshLogs() {
        fetchLogs(forceRefresh = true)
    }

    private fun fetchLogs(forceRefresh: Boolean = false) {
        if (!hasReadCallLogPermission()) return
        if (!forceRefresh && cachedLogs.isNotEmpty()) {
            _allCallLogs.value = cachedLogs
            return
        }
        if (isFetching) return
        viewModelScope.launch {
            fetchLogsInternal()
        }
    }

    private suspend fun fetchLogsInternal() {
        if (isFetching) return
        isFetching = true
        _isLoading.value = true
        try {
            if (hasReadCallLogPermission()) {
                val result = callLogRepo.getCallLogs()
                // Only push an update to the UI if the data actually changed.
                // This prevents a visible "refresh flicker" when the disk cache
                // and the freshly-fetched data are identical (the common case on
                // every app open after the first one).
                val changed = result.size != cachedLogs.size ||
                        result.zip(cachedLogs).any { (a, b) ->
                            a.number != b.number || a.date != b.date || a.type != b.type || a.name != b.name || a.isBlocked != b.isBlocked
                        }
                cachedLogs = result
                saveToDisk(result)
                if (changed) {
                    _allCallLogs.value = result
                }
            }
        } finally {
            isFetching = false
            _isLoading.value = false
        }
    }

    // ── Disk cache helpers ────────────────────────────────────────────────────

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun saveToDisk(logs: List<CallLogEntry>) = withContext(Dispatchers.IO) {
        try {
            val jsonText = json.encodeToString(logs)
            cacheFile.writeText(jsonText)
        } catch (_: Exception) {}
    }

    private suspend fun loadFromDisk(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        try {
            if (!cacheFile.exists()) return@withContext emptyList()
            json.decodeFromString<List<CallLogEntry>>(cacheFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteCallLog(number: String) {
        viewModelScope.launch {
            callLogRepo.deleteCallLog(number)
            fetchLogs()
        }
    }

    fun deleteCallLogsByIds(ids: List<Long>) {
        viewModelScope.launch {
            callLogRepo.deleteCallLogsByIds(ids)
            fetchLogs()
        }
    }

    fun clearCallLogs() {
        viewModelScope.launch {
            callLogRepo.clearCallLogs()
            fetchLogs()
        }
    }
}