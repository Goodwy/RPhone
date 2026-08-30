package dev.goodwy.rphone.data.manager

import dev.goodwy.rphone.domain.model.CallerMetadata
import dev.goodwy.rphone.domain.usecase.GetCallerNameUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton manager that holds the state of the current call.
 * This acts as the "Source of Truth" for the presentation layer.
 */
class CallStateManager(private val getCallerNameUseCase: GetCallerNameUseCase) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _callerMetadataMap = MutableStateFlow<Map<String, CallerMetadata>>(emptyMap())
    val callerMetadataMap: StateFlow<Map<String, CallerMetadata>> = _callerMetadataMap.asStateFlow()

    fun onNewCallReceived(number: String, cnam: String?) {
        scope.launch {
            val metadata = getCallerNameUseCase(number, cnam)
            synchronized(this) {
                val currentMap = _callerMetadataMap.value
                _callerMetadataMap.value = currentMap + (number to metadata)
            }
        }
    }
    
    fun onCallEnded(number: String? = null) {
        if (number == null) {
            _callerMetadataMap.value = emptyMap()
        } else {
            synchronized(this) {
                val currentMap = _callerMetadataMap.value
                _callerMetadataMap.value = currentMap - number
            }
        }
    }
}
