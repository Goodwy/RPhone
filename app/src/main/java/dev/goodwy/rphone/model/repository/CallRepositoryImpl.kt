package dev.goodwy.rphone.model.repository

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import dev.goodwy.rphone.model.`interface`.CallSession
import dev.goodwy.rphone.model.`interface`.ICallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CallRepositoryImpl : ICallRepository {

    private val _currentCallSession = MutableStateFlow<CallSession?>(null)
    override val currentCallSession: StateFlow<CallSession?> = _currentCallSession.asStateFlow()

    private val _allCalls = MutableStateFlow<List<Call>>(emptyList())
    override val allCalls: StateFlow<List<Call>> = _allCalls.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    override val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

    private val _isActivityVisible = MutableStateFlow(false)
    override val isActivityVisible: StateFlow<Boolean> = _isActivityVisible.asStateFlow()

    private val _preferredCall = MutableStateFlow<Call?>(null)

    private var inCallService: InCallService? = null

    fun bindService(service: InCallService) {
        this.inCallService = service
    }

    fun unbindService() {
        this.inCallService = null
    }

    override fun updateCurrentCallSession(session: CallSession?) {
        _currentCallSession.value = session
    }

    override fun updateAllCalls(calls: List<Call>) {
        _allCalls.value = ArrayList(calls)
    }

    override fun updateAudioState(state: CallAudioState?) {
        _audioState.value = state
    }

    override fun setIsActivityVisible(visible: Boolean) {
        _isActivityVisible.value = visible
    }

    override fun setPreferredCall(call: Call?) {
        _preferredCall.value = call
    }

    override fun getPreferredCall(): Call? = _preferredCall.value

    override fun answerCall() {
        _currentCallSession.value?.call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    override fun declineCall() {
        val call = _currentCallSession.value?.call ?: return
        val isRinging = call.state == Call.STATE_RINGING
        try {
            if (isRinging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.reject(Call.REJECT_REASON_DECLINED)
            } else {
                call.disconnect()
            }
        } catch (_: Exception) {
            try { call.disconnect() } catch (_: Exception) {}
        }
    }

    override fun mute(muted: Boolean) {
        inCallService?.setMuted(muted)
    }

    override fun toggleMute() {
        val currentMute = _audioState.value?.isMuted ?: false
        mute(!currentMute)
    }

    override fun setAudioRoute(route: Int) {
        inCallService?.setAudioRoute(route)
    }

    override fun cycleAudioRoute() {
        val state = _audioState.value ?: return
        val supported = state.supportedRouteMask
        val current = state.route

        val nextRoute = when (current) {
            CallAudioState.ROUTE_EARPIECE -> {
                if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) CallAudioState.ROUTE_SPEAKER
                else if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0) CallAudioState.ROUTE_BLUETOOTH
                else current
            }
            CallAudioState.ROUTE_SPEAKER -> {
                if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0) CallAudioState.ROUTE_BLUETOOTH
                else if ((supported and CallAudioState.ROUTE_WIRED_HEADSET) != 0) CallAudioState.ROUTE_WIRED_HEADSET
                else if ((supported and CallAudioState.ROUTE_EARPIECE) != 0) CallAudioState.ROUTE_EARPIECE
                else current
            }
            CallAudioState.ROUTE_BLUETOOTH -> {
                if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) CallAudioState.ROUTE_SPEAKER
                else if ((supported and CallAudioState.ROUTE_EARPIECE) != 0) CallAudioState.ROUTE_EARPIECE
                else current
            }
            CallAudioState.ROUTE_WIRED_HEADSET -> {
                if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) CallAudioState.ROUTE_SPEAKER
                else if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0) CallAudioState.ROUTE_BLUETOOTH
                else current
            }
            else -> if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) CallAudioState.ROUTE_SPEAKER else current
        }

        if (nextRoute != current) setAudioRoute(nextRoute)
    }

    override fun mergeCalls() {
        val inst = inCallService ?: return
        val calls = inst.calls ?: return
        if (calls.isEmpty()) return
        val activeCall = calls.find { it.state == Call.STATE_ACTIVE }
        val heldCall = calls.find { it.state == Call.STATE_HOLDING }

        try {
            if (activeCall != null && activeCall.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                activeCall.mergeConference()
                return
            }
        } catch (e: Exception) {
            Log.w("CallService", "mergeConference failed: ${e.message}")
        }

        try {
            if (activeCall != null && heldCall != null) {
                activeCall.conference(heldCall)
            } else if (calls.size >= 2) {
                calls[0].conference(calls[1])
            }
        } catch (e: Exception) {
            Log.e("CallService", "Error conferencing calls: ${e.message}")
        }
    }
}