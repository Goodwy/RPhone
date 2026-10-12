package dev.goodwy.rphone.controller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.AppLogger
import dev.goodwy.rphone.controller.util.LogEntry
import dev.goodwy.rphone.controller.util.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class AppLogsUiState(
    val logs: List<LogEntry> = emptyList(),
    val totalCount: Int = 0,
    val searchQuery: String = "",
    val selectedLogLevel: LogLevel? = null,
    val isSystemLogcatMode: Boolean = false,
    val systemLogcatText: String = "",
    val isLoadingSystemLogcat: Boolean = false
)

class AppLogsViewModel : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    private val _selectedLogLevel = MutableStateFlow<LogLevel?>(null)
    private val _isSystemLogcatMode = MutableStateFlow(false)
    private val _systemLogcatText = MutableStateFlow("")
    private val _isLoadingSystemLogcat = MutableStateFlow(false)

    private val _filterState = combine(_searchQuery, _selectedLogLevel, _isSystemLogcatMode) { query, level, isSystem ->
        Triple(query, level, isSystem)
    }

    private val _systemState = combine(_systemLogcatText, _isLoadingSystemLogcat) { text, loading ->
        Pair(text, loading)
    }

    val uiState: StateFlow<AppLogsUiState> = combine(
        AppLogger.logsState,
        _filterState,
        _systemState
    ) { rawLogs, filter, system ->
        val (query, levelFilter, isSystemMode) = filter
        val (logcatText, isLoadingLogcat) = system

        val filteredLogs = rawLogs.filter { entry ->
            val matchesLevel = levelFilter == null || entry.level == levelFilter
            val matchesSearch = query.isBlank() ||
                    entry.tag.contains(query, ignoreCase = true) ||
                    entry.message.contains(query, ignoreCase = true) ||
                    (entry.throwableDetails?.contains(query, ignoreCase = true) == true)
            matchesLevel && matchesSearch
        }

        AppLogsUiState(
            logs = filteredLogs,
            totalCount = rawLogs.size,
            searchQuery = query,
            selectedLogLevel = levelFilter,
            isSystemLogcatMode = isSystemMode,
            systemLogcatText = logcatText,
            isLoadingSystemLogcat = isLoadingLogcat
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppLogsUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLogLevelFilter(level: LogLevel?) {
        _selectedLogLevel.value = level
    }

    fun setSystemLogcatMode(enabled: Boolean) {
        _isSystemLogcatMode.value = enabled
        if (enabled && _systemLogcatText.value.isBlank()) {
            refreshSystemLogcat()
        }
    }

    fun refreshSystemLogcat() {
        viewModelScope.launch {
            _isLoadingSystemLogcat.value = true
            _systemLogcatText.value = AppLogger.getSystemLogcatText()
            _isLoadingSystemLogcat.value = false
        }
    }

    fun clearLogs() {
        AppLogger.clearLogs()
        _systemLogcatText.value = ""
    }

    fun copyLogsToClipboard(context: Context) {
        val textToCopy = if (_isSystemLogcatMode.value) {
            _systemLogcatText.value
        } else {
            val filtered = uiState.value.logs
            if (filtered.isEmpty()) AppLogger.getFormattedLogsText()
            else filtered.reversed().joinToString("\n") { it.toFormattedLine() }
        }

        if (textToCopy.isBlank()) {
            Toast.makeText(context, context.getString(R.string.app_logs_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("RPhone App Logs", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.app_logs_copied), Toast.LENGTH_SHORT).show()
    }

    fun shareLogs(context: Context) {
        try {
            val file = AppLogger.getLogFile(context)
            if (_isSystemLogcatMode.value) {
                val logcatFile = File(context.cacheDir, "system_logcat.txt")
                logcatFile.writeText(_systemLogcatText.value)
                shareFile(context, logcatFile, context.getString(R.string.app_logs_share))
            } else {
                shareFile(context, file, context.getString(R.string.app_logs_share))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share logs: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, title))
    }
}
