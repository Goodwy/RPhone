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
        val current = _callerMetadata.value
        // Avoid redundant lookups if we already have the correct metadata or are already looking it up.
        // We only proceed if it's a new number, or if we have a number-only metadata and a CNAM just arrived.
        if (current != null && current.number == number) {
            val isJustNumber = current.name == current.number
            val hasNewCnam = !cnam.isNullOrBlank() && isJustNumber
            if (!hasNewCnam) return 
        }

        // Immediately emit a fallback so UI/Notification isn't empty/Unknown if we have CNAM or just the number.
        // Doing this before launching the lookup coroutine ensures synchronous updates for the current frame.
        _callerMetadata.value = CallerMetadata(
            number = number,
            name = cnam ?: number.ifBlank { "Unknown" },
            isLocalContact = false
        )

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
