package dev.goodwy.rphone.data.manager

import dev.goodwy.rphone.domain.model.CallerMetadata
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Singleton manager that holds the state of the current call.
 * This acts as the "Source of Truth" for the presentation layer.
 */
class CallStateManager(private val getCallerNameUseCase: GetCallerNameUseCase) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lookupJob: Job? = null
    
    private val _callerMetadata = MutableStateFlow<CallerMetadata?>(null)
    val callerMetadata: StateFlow<CallerMetadata?> = _callerMetadata.asStateFlow()

    fun onNewCallReceived(number: String, cnam: String?) {
        // Immediately emit a fallback so UI/Notification isn't empty/Unknown if we have CNAM or just the number.
        // Doing this before launching the lookup coroutine ensures synchronous updates for the current frame.
        val current = _callerMetadata.value
        if (current == null || current.number != number || (current.name == current.number && !cnam.isNullOrBlank())) {
            _callerMetadata.value = CallerMetadata(
                number = number,
                name = cnam ?: number.ifBlank { "Unknown" },
                isLocalContact = false
            )
        }

        lookupJob?.cancel()
        lookupJob = scope.launch {
            val metadata = getCallerNameUseCase(number, cnam)
            _callerMetadata.value = metadata
        }
    }
    
    fun onCallEnded() {
        lookupJob?.cancel()
        _callerMetadata.value = null
    }
}
