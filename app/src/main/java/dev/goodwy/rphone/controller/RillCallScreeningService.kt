package dev.goodwy.rphone.controller

import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.telecom.TelecomManager
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.CallerPresentation
import dev.goodwy.rphone.modal.data.SpamCategory
import dev.goodwy.rphone.modal.data.SpamVerdict
import dev.goodwy.rphone.modal.data.StirShakenStatus
import dev.goodwy.rphone.modal.repository.SpamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.milliseconds

class RillCallScreeningService : CallScreeningService() {

    private val spamRepository: SpamRepository by inject()
    private val callStateManager: CallStateManager by inject()
    private val notificationManager: CallNotificationManager by inject()
    private val preferenceManager: PreferenceManager by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart?.let { Uri.decode(it) }.orEmpty()
        val presentation = mapPresentation(callDetails.handlePresentation)
        val stirShaken = mapStirShaken(callDetails)

        serviceScope.launch {
            val evaluation = withTimeoutOrNull(3_500L.milliseconds) {
                spamRepository.evaluateIncomingCall(
                    rawNumber = rawNumber,
                    presentation = presentation,
                    stirShakenStatus = stirShaken,
                    recordBurstAttempt = true
                )
            }

            if (evaluation == null) {
                respondToCall(callDetails, CallResponse.Builder().build())
                return@launch
            }

            callStateManager.recordSpamEvaluation(
                number = rawNumber,
                result = evaluation,
                screenedByService = true
            )

            val hideFromLogs = preferenceManager.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0) == 0
            val blockMethod = preferenceManager.getInt(PreferenceManager.KEY_BLOCK_METHOD, 0)

            val response = when {
                evaluation.isExplicitlyBlocked -> {
                    if (blockMethod == 0) {
                        if (preferenceManager.getBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, true)) {
                            notificationManager.showBlockedNotification(rawNumber)
                        }
                        CallResponse.Builder()
                            .setDisallowCall(true)
                            .setRejectCall(true)
                            .setSkipCallLog(hideFromLogs)
                            .setSkipNotification(true)
                            .build()
                    } else {
                        if (preferenceManager.getBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, true)) {
                            notificationManager.showBlockedNotification(rawNumber)
                        }
                        CallResponse.Builder()
                            .setDisallowCall(false)
                            .setSilenceCall(true)
                            .setSkipCallLog(hideFromLogs)
                            .setSkipNotification(true)
                            .build()
                    }
                }
                evaluation.verdict == SpamVerdict.DECLINE -> {
                    if (preferenceManager.getBoolean(PreferenceManager.KEY_SPAM_NOTIFICATION, true)) {
                        notificationManager.showSpamBlockedNotification(
                            number = rawNumber,
                            categoryLabel = categoryLabel(evaluation.category)
                        )
                    }
                    CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipCallLog(hideFromLogs)
                        .setSkipNotification(true)
                        .build()
                }
                evaluation.verdict == SpamVerdict.SILENCE -> {
                    if (preferenceManager.getBoolean(PreferenceManager.KEY_SPAM_NOTIFICATION, true)) {
                        notificationManager.showSpamBlockedNotification(
                            number = rawNumber,
                            categoryLabel = categoryLabel(evaluation.category)
                        )
                    }
                    CallResponse.Builder()
                        .setDisallowCall(false)
                        .setSilenceCall(true)
                        .build()
                }
                else -> {
                    CallResponse.Builder()
                        .setDisallowCall(false)
                        .build()
                }
            }

            respondToCall(callDetails, response)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun categoryLabel(category: SpamCategory): String {
        val resId = when (category) {
            SpamCategory.TELEMARKETER -> R.string.spam_category_telemarketer
            SpamCategory.SCAM -> R.string.spam_category_scam
            SpamCategory.ROBOCALL -> R.string.spam_category_robocall
            SpamCategory.SPOOFED -> R.string.spam_category_spoofed
            SpamCategory.PRIVATE_NUMBER -> R.string.spam_category_private
            SpamCategory.UNKNOWN_CALLER -> R.string.spam_category_unknown
            SpamCategory.USER_REPORTED -> R.string.spam_category_reported
            SpamCategory.SPAM, SpamCategory.NONE -> R.string.spam_category_spam
        }
        return getString(resId)
    }

    companion object {
        fun mapPresentation(presentation: Int): CallerPresentation {
            return when (presentation) {
                TelecomManager.PRESENTATION_RESTRICTED -> CallerPresentation.RESTRICTED
                TelecomManager.PRESENTATION_UNKNOWN -> CallerPresentation.UNKNOWN
                TelecomManager.PRESENTATION_PAYPHONE -> CallerPresentation.PAYPHONE
                else -> CallerPresentation.ALLOWED
            }
        }

        fun mapStirShaken(details: Call.Details?): StirShakenStatus {
            if (details == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return StirShakenStatus.UNKNOWN
            }
            return when (details.callerNumberVerificationStatus) {
                Connection.VERIFICATION_STATUS_PASSED -> StirShakenStatus.PASSED
                Connection.VERIFICATION_STATUS_FAILED -> StirShakenStatus.FAILED
                Connection.VERIFICATION_STATUS_NOT_VERIFIED -> StirShakenStatus.NOT_VERIFIED
                else -> StirShakenStatus.UNKNOWN
            }
        }
    }
}
