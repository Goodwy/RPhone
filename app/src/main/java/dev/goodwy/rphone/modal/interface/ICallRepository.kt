package dev.goodwy.rphone.modal.`interface`

import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.StateFlow

data class CallSession(
    val call: Call,
    val state: Int,
    val updateTime: Long = System.currentTimeMillis(),
    val connectTimeMillis: Long = 0L
)

interface ICallRepository {
    val currentCallSession: StateFlow<CallSession?>
    val allCalls: StateFlow<List<Call>>
    val audioState: StateFlow<CallAudioState?>
    val isActivityVisible: StateFlow<Boolean>

    fun updateCurrentCallSession(session: CallSession?)
    fun updateAllCalls(calls: List<Call>)
    fun updateAudioState(state: CallAudioState?)
    fun setIsActivityVisible(visible: Boolean)

    fun setPreferredCall(call: Call?)
    fun getPreferredCall(): Call?

    // Actions that proxy to InCallService
    fun answerCall()
    fun declineCall()
    fun mute(muted: Boolean)
    fun toggleMute()
    fun setAudioRoute(route: Int)
    fun cycleAudioRoute()
}