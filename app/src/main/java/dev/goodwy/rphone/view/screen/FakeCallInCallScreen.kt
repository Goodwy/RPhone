package dev.goodwy.rphone.view.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end
import java.util.Locale

@Composable
fun FakeCallInCallScreen(
    callerName: String,
    callerNumber: String,
    callState: Int, // 0 = RINGING, 1 = ACTIVE, 2 = DISCONNECTED
    durationSeconds: Long,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isHold: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit
) {
    var showKeypad by remember { mutableStateOf(false) }
    var typedDigits by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                // Avatar
                RillAvatar(
                    name = callerName,
                    photoUri = null,
                    modifier = Modifier.size(120.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = callerName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = callerNumber,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White.copy(alpha = 0.7f)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                val statusText = when (callState) {
                    0 -> stringResource(R.string.fake_call_incoming)
                    1 -> if (isHold) "On Hold" else formatCallDuration(durationSeconds)
                    else -> "Call Ended"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (callState == 0) color_call_button else Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )

                if (showKeypad && typedDigits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = typedDigits,
                        style = MaterialTheme.typography.headlineSmall.copy(color = Color.White)
                    )
                }
            }

            // Middle: Controls or Keypad when active
            if (callState == 1 && showKeypad) {
                FakeInCallKeypad(
                    onDigitClick = { digit -> typedDigits += digit },
                    onClose = { showKeypad = false }
                )
            } else if (callState == 1) {
                // Active Call Action Grid
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallActionButton(
                            icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            label = if (isMuted) "Unmute" else "Mute",
                            isActive = isMuted,
                            onClick = onToggleMute
                        )
                        CallActionButton(
                            icon = Icons.Rounded.Dialpad,
                            label = "Keypad",
                            isActive = false,
                            onClick = { showKeypad = true }
                        )
                        CallActionButton(
                            icon = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeDown,
                            label = "Speaker",
                            isActive = isSpeakerOn,
                            onClick = onToggleSpeaker
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallActionButton(
                            icon = if (isHold) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            label = if (isHold) "Resume" else "Hold",
                            isActive = isHold,
                            onClick = onToggleHold
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Bottom Actions (Incoming Answer/Decline or Active End Call)
            if (callState == 0) {
                // Ringing Call: Answer & Decline Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decline Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onDecline,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(color_call_end)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = stringResource(R.string.fake_call_decline),
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.fake_call_decline),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // Answer Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        IconButton(
                            onClick = onAnswer,
                            modifier = Modifier
                                .size(72.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(color_call_button)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = stringResource(R.string.fake_call_answer),
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.fake_call_answer),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else if (callState == 1) {
                // Active Call: End Call Button
                Box(
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(color_call_end)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun FakeInCallKeypad(
    onDigitClick: (String) -> Unit,
    onClose: () -> Unit
) {
    val keypadGrid = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        keypadGrid.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                row.forEach { digit ->
                    Surface(
                        onClick = { onDigitClick(digit) },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = digit,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onClose) {
            Text(
                text = "Hide Keypad",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color.Black else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatCallDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
