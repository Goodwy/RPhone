package dev.goodwy.rphone.controller

import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.screen.ExpressiveCallScreen
import dev.goodwy.rphone.view.theme.Rill4Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import kotlin.getValue

private data class CallIdentity(
    val number: String,
    val name: String,
    val photoUri: String?,
    val backgroundUri: String?
)

private data class CachedCallIdentity(
    val identity: CallIdentity,
    val version: Int
)

class CallActivity : FragmentActivity() { //ComponentActivity()

    private val contactsRepo: IContactsRepository by inject()
    private val preferenceManager: PreferenceManager by inject()
    private val callViewModel: CallViewModel by inject()
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private val isFinishingCall = java.util.concurrent.atomic.AtomicBoolean(false)
    private var keyguardDismissRequested = false
    private val identityCache = mutableMapOf<String, CachedCallIdentity>()

    companion object {
        /** FloatingCallService observes this to hide the bubble when CallActivity is visible. */
        val isInForeground = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        turnScreenOnAndShowWhileLocked()
        super.onCreate(savedInstanceState)

        CallBackgroundStore.attach(preferenceManager)

        if (callViewModel.allCalls.value.none { it.state != Call.STATE_DISCONNECTED } &&
            callViewModel.currentCallSession.value == null
        ) {
            finish()
            return
        }

        if (preferenceManager.getBoolean(PreferenceManager.KEY_KEEP_SCREEN_ON, true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setupProximitySensor()
        val themeMode = preferenceManager.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto"
        applySystemBarStyle(
            themeMode == "dark" || themeMode == "black" ||
                    (themeMode == "auto" && isNightMode()) || (themeMode == "auto_bw" && isNightMode())
        )

        setContent {
            Rill4Theme {
                val session by callViewModel.currentCallSession.collectAsStateWithLifecycle()
                val audioState by callViewModel.audioState.collectAsStateWithLifecycle()
                val settingsState by preferenceManager.settingsChanged.collectAsStateWithLifecycle()
                val callerMetadata by callViewModel.callerMetadata.collectAsStateWithLifecycle()

                var retainedSession by remember { mutableStateOf<CallSession?>(null) }
                LaunchedEffect(session) {
                    session?.let { retainedSession = it }
                }

                val displaySession = session ?: retainedSession
                val displayCall = displaySession?.call
                val callState = session?.state

                val identity = if (displayCall != null) {
                    rememberCallIdentity(displayCall, settingsState)
                } else {
                    null
                }

                val darkSystemTheme = isSystemInDarkTheme()
                val darkTheme = themeMode == "dark" || themeMode == "black" ||
                        (themeMode == "auto" && darkSystemTheme) || (themeMode == "auto_bw" && darkSystemTheme)
                val lightSystemBarIcons = darkTheme ||
                        identity?.backgroundUri != null ||
                        identity?.photoUri != null

                DisposableEffect(lightSystemBarIcons) {
                    applySystemBarStyle(lightSystemBarIcons)
                    onDispose { }
                }

                LaunchedEffect(callState, settingsState, audioState) {
                    val keepScreenOn =
                        preferenceManager.getBoolean(PreferenceManager.KEY_KEEP_SCREEN_ON, true)
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    when (callState) {
                        Call.STATE_ACTIVE -> {
                            if (preferenceManager.getBoolean(
                                    PreferenceManager.KEY_VIBRATE_ON_ANSWER,
                                    true
                                )
                            ) {
                                this@CallActivity.window?.decorView?.performHapticFeedback(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        HapticFeedbackConstants.CONFIRM
                                    } else {
                                        HapticFeedbackConstants.VIRTUAL_KEY
                                    }
                                )
                            }
                            if (preferenceManager.getBoolean(
                                    PreferenceManager.KEY_PROXIMITY_SENSOR,
                                    true
                                )
                            ) {
                                acquireProximityLock()
                            } else {
                                releaseProximityLock()
                            }
                        }

                        Call.STATE_DIALING -> {
                            if (preferenceManager.getBoolean(
                                    PreferenceManager.KEY_PROXIMITY_SENSOR,
                                    true
                                )
                            ) {
                                acquireProximityLock()
                            } else {
                                releaseProximityLock()
                            }
                        }

                        Call.STATE_DISCONNECTED -> {
                            if (preferenceManager.getBoolean(
                                    PreferenceManager.KEY_VIBRATE_ON_HANGUP,
                                    false
                                )
                            ) {
                                this@CallActivity.window?.decorView?.performHapticFeedback(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        HapticFeedbackConstants.REJECT
                                    } else {
                                        HapticFeedbackConstants.LONG_PRESS
                                    }
                                )
                            }
                            releaseProximityLock()
                            delay(400)
                            dismissCallScreen()
                        }

                        else -> releaseProximityLock()
                    }

                    if (session == null) {
                        delay(400)
                        if (callViewModel.allCalls.value.none { it.state != Call.STATE_DISCONNECTED }) {
                            dismissCallScreen()
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AnimatedContent(
                        targetState = displayCall,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)))
                                .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)))
                        },
                        label = "CallSwitch"
                    ) { targetCall ->
                        val answeredFromNotification = intent?.getBooleanExtra("ANSWERED_FROM_NOTIFICATION", false) ?: false

                        if (targetCall == null) {
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                            val isDisplayed = targetCall === displayCall
                            val targetIdentity =
                                if (isDisplayed && identity != null) {
                                    identity
                                } else {
                                    rememberCallIdentity(targetCall, settingsState)
                                }
                            val targetState = if (isDisplayed) {
                                session?.state ?: targetCall.state
                            } else {
                                targetCall.state
                            }
                            val connectTime = if (isDisplayed) {
                                displaySession?.connectTimeMillis ?: 0L
                            } else {
                                targetCall.details?.connectTimeMillis ?: 0L
                            }

                            ExpressiveCallScreen(
                                call = targetCall,
                                callState = targetState,
                                contactName = targetIdentity.name,
                                phoneNumber = targetIdentity.number,
                                photoUri = targetIdentity.photoUri,
                                audioState = audioState,
                                initialConnectTime = connectTime,
                                backgroundUri = targetIdentity.backgroundUri,
                                skipIncomingScreen = answeredFromNotification
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun rememberCallIdentity(call: Call, settingsState: Int): CallIdentity {
        val context = LocalContext.current
        val unknownLabel = stringResource(R.string.label_unknown)
        val number = remember(call) { call.details?.handle?.schemeSpecificPart.orEmpty() }

        var identity by remember(number, unknownLabel) {
            val base = cachedIdentity(number, settingsState)
                ?: CallIdentity(number, number.ifEmpty { unknownLabel }, null, null)
            mutableStateOf(
                if (base.backgroundUri == null) {
                    base.copy(backgroundUri = CallBackgroundStore.defaultModel(context))
                } else {
                    base
                }
            )
        }

        LaunchedEffect(number, settingsState) {
            val handle = number.ifEmpty { null }

            val handleResult = CallBackgroundStore.resolveResult(context, handle, null)
            val handleBackground = handleResult.uri
            if (handleBackground != null && handleBackground != identity.backgroundUri) {
                identity = identity.copy(backgroundUri = handleBackground)
            }

            val lookup = if (handle == null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { contactsRepo.getContactByNumber(number) }
                }
            }
            val contact = lookup?.getOrNull()
            val contactFailed = lookup?.isFailure == true

            val contactId = contact?.id?.takeIf { it.isNotBlank() }
            val idResult = if (handleBackground == null && contactId != null) {
                CallBackgroundStore.resolveResult(context, handle, contactId)
            } else {
                null
            }

            val resolvedBackground = handleBackground ?: idResult?.uri
            val resolveFailed =
                handleResult.failed || idResult?.failed == true || contactFailed
            val defaultBackground = CallBackgroundStore.defaultModelAsync(context)
            val background = resolvedBackground
                ?: identity.backgroundUri.takeIf { resolveFailed }
                ?: defaultBackground

            val resolved = CallIdentity(
                number = number,
                name = contact?.name?.takeIf { it.isNotBlank() }
                    ?: identity.name.takeIf { contactFailed && it.isNotBlank() }
                    ?: number.ifEmpty { unknownLabel },
                photoUri = contact?.photoUri ?: identity.photoUri.takeIf { contactFailed },
                backgroundUri = background
            )
            cacheIdentity(number, resolved, settingsState)
            identity = resolved
        }

        return identity
    }

    private fun cachedIdentity(number: String, version: Int): CallIdentity? {
        if (number.isEmpty()) return null
        val cached = identityCache[number] ?: return null
        if (cached.version != version) return null
        return cached.identity
    }

    private fun cacheIdentity(number: String, identity: CallIdentity, version: Int) {
        identityCache.entries.removeAll { it.value.version != version }
        if (number.isEmpty()) return
        identityCache[number] = CachedCallIdentity(identity, version)
    }

    private fun isNightMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun applySystemBarStyle(lightIcons: Boolean) {
        val style = if (lightIcons) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onResume() {
        super.onResume()
        isInForeground.value = true
    }

    override fun onPause() {
        super.onPause()
        isInForeground.value = false
    }

    private fun setupProximitySensor() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "RillPhoneApp::ProximityWakeLock"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProximityLock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!isFinishingCall.get() && callViewModel.allCalls.value.any { it.state != Call.STATE_DISCONNECTED }) {
            turnScreenOnAndShowWhileLocked()
        }
    }

    private fun dismissCallScreen() {
        if (isFinishingCall.getAndSet(true)) return

        if (callViewModel.allCalls.value.any { it.state != Call.STATE_DISCONNECTED }) {
            isFinishingCall.set(false)
            return
        }

        setShowWhenLocked(false)
        setTurnScreenOn(false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        finishAndRemoveTask()
    }

    private fun turnScreenOnAndShowWhileLocked() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (!keyguardDismissRequested) {
            keyguardDismissRequested = true
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
    }

    override fun onStart() {
        super.onStart()
        callViewModel.setIsActivityVisible(true)
    }

    override fun onStop() {
        super.onStop()
        callViewModel.setIsActivityVisible(false)
    }

    private fun acquireProximityLock() {
        val route = callViewModel.audioState.value?.route
        val isHandsFree = route == android.telecom.CallAudioState.ROUTE_SPEAKER ||
                route == android.telecom.CallAudioState.ROUTE_BLUETOOTH ||
                route == android.telecom.CallAudioState.ROUTE_WIRED_HEADSET

        if (!isHandsFree && preferenceManager.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) {
            proximityWakeLock?.let { if (!it.isHeld) it.acquire(20*60*1000L /*20 minutes*/) }
        } else {
            releaseProximityLock()
        }
    }

    private fun releaseProximityLock() {
        proximityWakeLock?.let { if (it.isHeld) it.release() }
    }
}