package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.data.CallerMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton manager that holds the state of the current call.
 * This acts as the "Source of Truth" for the presentation layer.
 */
class CallStateManager(private val getCallerNameUseCase: GetCallerNameUseCase) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    private val _callerMetadataMap = MutableStateFlow<Map<String, CallerMetadata>>(emptyMap())
    val callerMetadataMap: StateFlow<Map<String, CallerMetadata>> = _callerMetadataMap.asStateFlow()

    companion object {
        private const val MAX_ENTRIES = 50
    }

    fun onNewCallReceived(number: String, cnam: String?) {
        if (number.isBlank()) return
        activeJobs[number]?.cancel()
        val job = scope.launch {
            val metadata = getCallerNameUseCase(number, cnam)
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
            _callerMetadataMap.value = emptyMap()
        } else {
            activeJobs.remove(number)?.cancel()
            _callerMetadataMap.update { current -> current - number }
        }
    }
}
