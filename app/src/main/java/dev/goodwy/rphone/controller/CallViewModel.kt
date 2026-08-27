package dev.goodwy.rphone.controller

import androidx.lifecycle.ViewModel
import android.telecom.Call
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.domain.model.CallerMetadata
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import dev.goodwy.rphone.modal.`interface`.CallSession
import kotlinx.coroutines.flow.StateFlow

class CallViewModel(
    private val callRepository: ICallRepository,
    private val callStateManager: CallStateManager
) : ViewModel() {

    val currentCallSession: StateFlow<CallSession?> = callRepository.currentCallSession
    val allCalls: StateFlow<List<Call>> = callRepository.allCalls
    val audioState = callRepository.audioState
    val callerMetadata: StateFlow<CallerMetadata?> = callStateManager.callerMetadata

    fun answerCall() = callRepository.answerCall()
    fun declineCall() = callRepository.declineCall()
    fun toggleMute() = callRepository.toggleMute()
    fun cycleAudioRoute() = callRepository.cycleAudioRoute()
    fun setAudioRoute(route: Int) = callRepository.setAudioRoute(route)
    fun setPreferredCall(call: Call?) = callRepository.setPreferredCall(call)
    fun setIsActivityVisible(visible: Boolean) = callRepository.setIsActivityVisible(visible)
}
