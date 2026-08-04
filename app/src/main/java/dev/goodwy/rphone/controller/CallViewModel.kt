package dev.goodwy.rphone.controller

import androidx.lifecycle.ViewModel
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.model.CallerMetadata
import kotlinx.coroutines.flow.StateFlow

class CallViewModel(private val callStateManager: CallStateManager) : ViewModel() {
    // ViewModel strictly exposes state from the Manager
    val callerMetadata: StateFlow<CallerMetadata?> = callStateManager.callerMetadata
}
