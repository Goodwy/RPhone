package dev.goodwy.rphone.view.screen

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.getDefaultDialerIntent
import dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DefaultDialerScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject

@Destination<RootGraph>()
@Composable
fun DefaultDialerScreen(navController: NavController, navigator: DestinationsNavigator) {
    val context = LocalContext.current
    var isDenied by remember { mutableStateOf(false) }

    val prefs = koinInject<PreferenceManager>()
    val defBar = prefs.getInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, 0)

    val initialRoute = if (defBar == 0) {
        RecentScreenDestination
    } else {
        ContactScreenDestination
    }

    LaunchedEffect(Unit) {
        if (isAlreadyDefaultDialer(context)) {
            navigator.navigate(initialRoute) {
                popUpTo(DefaultDialerScreenDestination) { inclusive = true }
            }
        }
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isDenied = false
            navigator.navigate(initialRoute) {
                popUpTo(DefaultDialerScreenDestination) { inclusive = true }
            }
        } else {
            isDenied = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (!isLandscape) Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = stringResource(R.string.default_dialer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.default_dialer_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                AnimatedVisibility(visible = isDenied) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .padding(top = 32.dp, start = 24.dp, end = 24.dp)
                            .wrapContentWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.default_dialer_warning),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = {
                        val intent = getDefaultDialerIntent(context)
                        roleRequestLauncher.launch(intent)
                    },
                    modifier = Modifier.wrapContentSize(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(
                        modifier = Modifier.padding(6.dp),
                        text = stringResource(R.string.set_as_default),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}