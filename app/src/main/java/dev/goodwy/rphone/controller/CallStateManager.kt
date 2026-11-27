package dev.goodwy.rphone.controller

import dev.goodwy.rphone.controller.util.BusyModeExceptionsHelper
import dev.goodwy.rphone.modal.data.CallerMetadata
import dev.goodwy.rphone.modal.data.SpamEvaluationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton manager that holds the state of the current call.
 * This acts as the "Source of Truth" for the presentation layer.
 */
class CallStateManager(private val getCallerNameUseCase: GetCallerNameUseCase) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val activeJobs = ConcurrentHashMap<String, Job>()

    private data class ScreeningCacheEntry(
        val result: SpamEvaluationResult,
        val timestampMs: Long,
        val screenedByService: Boolean
    )

    private val screeningResults = ConcurrentHashMap<String, ScreeningCacheEntry>()

    private val _callerMetadataMap = MutableStateFlow<Map<String, CallerMetadata>>(emptyMap())
    val callerMetadataMap: StateFlow<Map<String, CallerMetadata>> = _callerMetadataMap.asStateFlow()

    private val _spamEvaluationMap = MutableStateFlow<Map<String, SpamEvaluationResult>>(emptyMap())
    val spamEvaluationMap: StateFlow<Map<String, SpamEvaluationResult>> = _spamEvaluationMap.asStateFlow()

    companion object {
        private const val MAX_ENTRIES = 50
        private const val SCREENING_TTL_MS = 15_000L
    }

    fun recordSpamEvaluation(
        number: String,
        result: SpamEvaluationResult,
        screenedByService: Boolean = false
    ) {
        val key = screeningKey(number)
        if (key.isEmpty()) return
        screeningResults[key] = ScreeningCacheEntry(
            result = result,
            timestampMs = System.currentTimeMillis(),
            screenedByService = screenedByService
        )
        _spamEvaluationMap.update { current ->
            val next = current + (number to result) + (key to result)
            if (next.size > MAX_ENTRIES * 2) {
                val keysToRemove = next.keys.take(next.size - MAX_ENTRIES * 2)
                next - keysToRemove.toSet()
            } else {
                next
            }
        }
        _callerMetadataMap.update { current ->
            val existing = current[number] ?: return@update current
            current + (number to existing.copy(spamEvaluation = result))
        }
    }

    fun getRecentScreeningResult(number: String, requireServiceScreened: Boolean = false): SpamEvaluationResult? {
        val key = screeningKey(number)
        if (key.isEmpty()) return null
        val entry = screeningResults[key] ?: return null
        if (System.currentTimeMillis() - entry.timestampMs > SCREENING_TTL_MS) {
            screeningResults.remove(key, entry)
            return null
        }
        if (requireServiceScreened && !entry.screenedByService) return null
        return entry.result
    }

    fun onNewCallReceived(number: String, cnam: String?) {
        if (number.isBlank()) return
        activeJobs[number]?.cancel()
        val job = scope.launch {
            val baseMetadata = getCallerNameUseCase(number, cnam)
            val cachedSpam = getRecentScreeningResult(number)
            val metadata = if (cachedSpam != null) {
                baseMetadata.copy(spamEvaluation = cachedSpam)
            } else {
                baseMetadata
            }
            _callerMetadataMap.update { current ->
                val next = current + (number to metadata)
                if (next.size > MAX_ENTRIES) {
                    val keysToRemove = next.keys.take(next.size - MAX_ENTRIES)
                    next - keysToRemove.toSet()
                } else {
                    next
                }
            }
        }
        job.invokeOnCompletion { activeJobs.remove(number, job) }
        activeJobs[number] = job
    }

    fun onCallEnded(number: String? = null) {
        if (number == null) {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
            screeningResults.clear()
            _callerMetadataMap.value = emptyMap()
            _spamEvaluationMap.value = emptyMap()
        } else {
            activeJobs.remove(number)?.cancel()
            val key = screeningKey(number)
            screeningResults.remove(key)
            _callerMetadataMap.update { current -> current - number }
            _spamEvaluationMap.update { current -> current - number - key }
        }
    }

    private fun screeningKey(number: String): String {
        val normalized = BusyModeExceptionsHelper.normalizeManualNumber(number)
        return normalized.ifEmpty { number.trim() }
    }
}
