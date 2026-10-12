package dev.goodwy.rphone.controller.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class LogLevel(val priority: Int, val code: String, val label: String) {
    VERBOSE(Log.VERBOSE, "V", "Verbose"),
    DEBUG(Log.DEBUG, "D", "Debug"),
    INFO(Log.INFO, "I", "Info"),
    WARN(Log.WARN, "W", "Warn"),
    ERROR(Log.ERROR, "E", "Error");

    companion object {
        fun fromCode(code: String): LogLevel {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: INFO
        }
    }
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwableDetails: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            return sdf.format(Date(timestamp))
        }

    fun toFormattedLine(): String {
        val details = if (!throwableDetails.isNull_orEmpty()) "\n$throwableDetails" else ""
        return "$formattedTime [${level.code}/$tag] $message$details"
    }
}

private fun String?.isNull_orEmpty(): Boolean = this == null || this.trim().isEmpty()

object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_MEMORY_ENTRIES = 2000
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2MB

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val memoryLogs = CopyOnWriteArrayList<LogEntry>()
    private val _logsState = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsState: StateFlow<List<LogEntry>> = _logsState.asStateFlow()

    private var logFile: File? = null
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        val logsDir = File(appContext.filesDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        logFile = File(logsDir, "app_logs.txt")
        isInitialized = true

        setupUncaughtExceptionHandler()
        loadLogsFromFile()
        i(TAG, "In-app Logger initialized")
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("UncaughtException", "Fatal crash in thread '${thread.name}'", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, tag, message, throwable)
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        // Forward to system Logcat
        when (level) {
            LogLevel.VERBOSE -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
            LogLevel.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
            LogLevel.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
            LogLevel.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            LogLevel.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }

        val throwableDetails = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            throwableDetails = throwableDetails
        )

        memoryLogs.add(0, entry) // Newest first
        while (memoryLogs.size > MAX_MEMORY_ENTRIES) {
            memoryLogs.removeAt(memoryLogs.size - 1)
        }
        _logsState.value = memoryLogs.toList()

        scope.launch {
            appendLogToFile(entry)
        }
    }

    @Synchronized
    private fun appendLogToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
                trimLogFile(file)
            }
            FileWriter(file, true).use { writer ->
                writer.appendLine(entry.toFormattedLine())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing log entry to file", e)
        }
    }

    private fun trimLogFile(file: File) {
        try {
            val lines = file.readLines()
            val keepLines = lines.takeLast(lines.size / 2)
            file.writeText(keepLines.joinToString("\n") + "\n")
        } catch (_: Exception) {}
    }

    private fun loadLogsFromFile() {
        scope.launch {
            val file = logFile ?: return@launch
            if (!file.exists()) return@launch
            try {
                val loaded = mutableListOf<LogEntry>()
                val lines = file.readLines()
                var currentEntry: MutableList<String>? = null

                for (line in lines) {
                    if (line.matches(Regex("""^\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3}\s\[[VDIWE]/.*?].*"""))) {
                        currentEntry?.let { parseAndAddEntry(it, loaded) }
                        currentEntry = mutableListOf(line)
                    } else if (currentEntry != null) {
                        currentEntry.add(line)
                    }
                }
                currentEntry?.let { parseAndAddEntry(it, loaded) }

                withContext(Dispatchers.Main) {
                    memoryLogs.clear()
                    memoryLogs.addAll(loaded.take(MAX_MEMORY_ENTRIES))
                    _logsState.value = memoryLogs.toList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load logs from file", e)
            }
        }
    }

    private fun parseAndAddEntry(lines: List<String>, targetList: MutableList<LogEntry>) {
        if (lines.isEmpty()) return
        val header = lines[0]
        val regex = Regex("""^(\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s\[([VDIWE])/(.*?)\]\s(.*)$""")
        val match = regex.find(header)
        if (match != null) {
            val (timeStr, levelCode, tag, message) = match.destructured
            val level = LogLevel.fromCode(levelCode)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            val timestamp = try { sdf.parse(timeStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
            val throwableDetails = if (lines.size > 1) lines.drop(1).joinToString("\n") else null

            targetList.add(0, LogEntry(
                timestamp = timestamp,
                level = level,
                tag = tag,
                message = message,
                throwableDetails = throwableDetails
            ))
        } else {
            targetList.add(0, LogEntry(
                level = LogLevel.INFO,
                tag = "System",
                message = lines.joinToString("\n")
            ))
        }
    }

    fun getLogFile(context: Context): File {
        init(context)
        val file = logFile ?: File(context.filesDir, "logs/app_logs.txt")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        return file
    }

    fun getFormattedLogsText(): String {
        val currentLogs = _logsState.value
        return if (currentLogs.isEmpty()) {
            "No logs available."
        } else {
            currentLogs.reversed().joinToString("\n") { it.toFormattedLine() }
        }
    }

    suspend fun getSystemLogcatText(): String = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val maxLines = 5000
            val linesList = mutableListOf<String>()

            while (reader.readLine().also { line = it } != null) {
                linesList.add(line!!)
                if (linesList.size > maxLines) {
                    linesList.removeAt(0)
                }
            }
            process.destroy()
            linesList.joinToString("\n")
        } catch (e: Exception) {
            "Failed to capture system logcat: ${e.localizedMessage}"
        }
    }

    @Synchronized
    fun clearLogs() {
        memoryLogs.clear()
        _logsState.value = emptyList()
        scope.launch {
            val file = logFile ?: return@launch
            try {
                if (file.exists()) {
                    file.writeText("")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing log file", e)
            }
        }
    }
}
