package dev.goodwy.rphone.controller

import android.content.Context
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.R
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.model.CallerMetadata
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CallViewModel(
    private val context: Context,
    private val callRepository: ICallRepository,
    private val callStateManager: CallStateManager
) : ViewModel() {

    val currentCallSession: StateFlow<CallSession?> = callRepository.currentCallSession
    val allCalls: StateFlow<List<Call>> = callRepository.allCalls
    val audioState = callRepository.audioState
    
    val callerMetadata: StateFlow<CallerMetadata?> = combine(
        callRepository.currentCallSession,
        callStateManager.callerMetadataMap
    ) { session, metadataMap ->
        val number = session?.call?.details?.handle?.schemeSpecificPart ?: ""
        metadataMap[number]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val callDuration: StateFlow<Long> = callRepository.currentCallSession.flatMapLatest { session ->
        if (session == null || session.state != Call.STATE_ACTIVE) {
            flowOf(0L)
        } else {
            flow {
                val connectTime = session.connectTimeMillis
                while (true) {
                    val duration = if (connectTime > 0) (System.currentTimeMillis() - connectTime) / 1000 else 0L
                    emit(duration)
                    delay(1000)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val simLabel: StateFlow<String?> = callRepository.currentCallSession.map { session ->
        val accountHandle = session?.call?.details?.accountHandle ?: return@map null
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val account = try {
            telecomManager.getPhoneAccount(accountHandle)
        } catch (_: Exception) {
            null
        }
        account?.label?.toString() ?: context.getString(R.string.call_screen_sim_label, accountHandle.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun answerCall() = callRepository.answerCall()
    fun declineCall() = callRepository.declineCall()
    fun toggleMute() = callRepository.toggleMute()
    fun cycleAudioRoute() = callRepository.cycleAudioRoute()
    fun setAudioRoute(route: Int) = callRepository.setAudioRoute(route)
    fun setPreferredCall(call: Call?) = callRepository.setPreferredCall(call)
    fun setIsActivityVisible(visible: Boolean) = callRepository.setIsActivityVisible(visible)
}
