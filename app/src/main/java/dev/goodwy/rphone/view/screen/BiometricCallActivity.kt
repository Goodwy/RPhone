package dev.goodwy.rphone.view.screen

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.VideoProfile
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallActivity
import dev.goodwy.rphone.controller.CallService
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.PasswordDialogContent
import dev.goodwy.rphone.view.screen.settings.PinDialogContent
import dev.goodwy.rphone.view.theme.Rill4Theme
import org.koin.android.ext.android.inject

class BiometricCallActivity : FragmentActivity() {

    private val prefs: PreferenceManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val action = intent?.getStringExtra("NOTIFICATION_PENDING_ACTION") ?: run { finish(); return }
        val biometricType = prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""

        // Verify we should actually gate this specific call
        val callPhoneNumber = CallService.currentCallSession.value?.call?.details?.handle?.schemeSpecificPart
        if (!prefs.shouldGateCallWithBiometric(callPhoneNumber) || biometricType.isEmpty()) {
            // Lock scope excludes this number — perform action directly
            val call = CallService.currentCallSession.value?.call
            when (action) {
                "ANSWER" -> {
                    try { call?.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {}
                    startActivity(Intent(this, CallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra("ANSWERED_FROM_NOTIFICATION", true)
                    })
                }
                "DECLINE" -> try { call?.disconnect() } catch (_: Exception) {}
            }
            finish()
            return
        }

        setContent {
            Rill4Theme {
                val activity = this
                val call = CallService.currentCallSession.collectAsState().value?.call
                if (call != null) BiometricFloatingUi(
                    biometricType  = biometricType,
                    activity       = activity,
                    expectedPin      = prefs.getString(PreferenceManager.KEY_BIOMETRICS_PIN, "") ?: "",
                    expectedPassword = prefs.getString(PreferenceManager.KEY_BIOMETRICS_PASSWORD, "") ?: "",
                    onSuccess = {
                        when (action) {
                            "ANSWER" -> {
                                try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {}
                                startActivity(Intent(activity, CallActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                    putExtra("ANSWERED_FROM_NOTIFICATION", true)
                                })
                            }
                            "DECLINE" -> try { call.disconnect() } catch (_: Exception) {}
                        }
                        finish()
                    },
                    onDismiss = { finish() }
                ) else finish()
            }
        }
    }
}

@Composable
private fun BiometricFloatingUi(
    biometricType: String,
    activity: FragmentActivity,
    expectedPin: String,
    expectedPassword: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { if (biometricType != "system") onDismiss() }
    ) {
        when (biometricType) {
            "system" -> {
                LaunchedEffect(Unit) {
                    val executor = ContextCompat.getMainExecutor(activity)
                    val prompt = BiometricPrompt(
                        activity, executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onDismiss() }
                            override fun onAuthenticationFailed() {}
                        }
                    )
                    prompt.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Ever Dialer")
                            .setSubtitle("Verify your identity to access this call")
                            .setNegativeButtonText("Cancel")
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            .build()
                    )
                }
            }
            "pin" -> {
                Surface(
                    modifier = Modifier
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,//MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Box(
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        PinDialogContent(
                            title = stringResource(R.string.enter_pin),
                            isVerify = true,
                            expectedPin = expectedPin,
                            onConfirm = { onSuccess() },
                            onDismiss = onDismiss
                        )
                    }
                }
            }
            "password" -> {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp)
                        .widthIn(max = 360.dp)
                        .wrapContentHeight()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    PasswordDialogContent(
                        title            = stringResource(R.string.enter_password),
                        isVerify         = true,
                        expectedPassword = expectedPassword,
                        showCloseButton  = true,
                        onConfirm        = { onSuccess() },
                        onDismiss        = onDismiss
                    )
                }
            }
        }
    }
}
