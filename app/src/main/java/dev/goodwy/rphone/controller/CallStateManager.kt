package dev.goodwy.rphone.controller

import dev.goodwy.rphone.model.data.CallerMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Singleton manager that holds the state of the current call.
 * This acts as the "Source of Truth" for the presentation layer.
 */
class CallStateManager(private val getCallerNameUseCase: GetCallerNameUseCase) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _callerMetadataMap = MutableStateFlow<Map<String, CallerMetadata>>(emptyMap())
    val callerMetadataMap: StateFlow<Map<String, CallerMetadata>> = _callerMetadataMap.asStateFlow()

    companion object {
        private const val MAX_ENTRIES = 50
    }

    fun onNewCallReceived(number: String, cnam: String?) {
        scope.launch {
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
    }
    
    fun onCallEnded(number: String? = null) {
        if (number == null) {
            _callerMetadataMap.value = emptyMap()
        } else {
            _callerMetadataMap.update { current -> current - number }
        }
    }
}
