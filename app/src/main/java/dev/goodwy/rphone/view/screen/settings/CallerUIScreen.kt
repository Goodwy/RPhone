package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.color_call_end
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallerUIScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()

    var hangupWidth by remember { mutableFloatStateOf(prefs.getFloat(PreferenceManager.KEY_HANGUP_WIDTH, 0.5f).coerceIn(0.1f, 1.0f)) }

    val context = LocalContext.current
    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isRotation90 = rotation == Surface.ROTATION_90
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Text("Caller UI", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
//                .padding(padding)
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Hang Up Button ────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        SettingsSectionLabel("Hang Up Button")
                        RillExpressiveCard {
                            Column(modifier = Modifier
                                .background(color = cardColor)
                                .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = color_call_end.copy(alpha = 0.15f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.CallEnd,
                                                contentDescription = null,
                                                tint = color_call_end,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            "Customise Width",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Adjust the width of the hang up button",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.height(20.dp))

                                // Live preview
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val isCircle = hangupWidth <= 0.1f
                                    Surface(
                                        shape = if (isCircle) CircleShape else RoundedCornerShape(42.dp),
                                        color = color_call_end,
                                        modifier = if (isCircle) Modifier.size(64.dp)
                                            else Modifier.fillMaxWidth(hangupWidth.coerceIn(0.1f, 1.0f)).height(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val showText = hangupWidth > 0.5f
                                                Icon(
                                                    Icons.Rounded.CallEnd,
                                                    null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(if (showText) 26.dp else 32.dp)
                                                )
                                                if (showText) {
                                                    Text(
                                                        stringResource(R.string.end_call),
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
//                                    Icon(
//                                        Icons.Default.Remove,
//                                        null,
//                                        modifier = Modifier.size(18.dp),
//                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
                                    Slider(
                                        value = hangupWidth,
                                        onValueChange = { hangupWidth = it },
                                        onValueChangeFinished = {
                                            prefs.setFloat(PreferenceManager.KEY_HANGUP_WIDTH, hangupWidth)
                                        },
                                        valueRange = 0.1f..1.0f,
                                        steps = 8,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = color_call_end,
                                            activeTrackColor = color_call_end,
                                            inactiveTrackColor = color_call_end.copy(alpha = 0.3f)
                                        )
                                    )
//                                    Icon(
//                                        Icons.Default.Add,
//                                        null,
//                                        modifier = Modifier.size(18.dp),
//                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Narrow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${(hangupWidth * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Full Width",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
