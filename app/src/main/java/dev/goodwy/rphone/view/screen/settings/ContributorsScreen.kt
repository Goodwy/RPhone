package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.GITHUB_DEV
import dev.goodwy.rphone.controller.util.openLink
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

data class Contributor(
    val name: String,
    val role: String,
    val githubUrl: String? = null
)

val appContributors = listOf(
    Contributor("Goodwy", "Developer", GITHUB_DEV),
)

val otherContributors = listOf(
    Contributor("user-grinch", "RivoPhoneApp", "https://github.com/user-grinch/RivoPhoneApp"),
    Contributor("hari161008", "Ever-Dialer", "https://github.com/hari161008/Ever-Dialer"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContributorsScreen(
    navigator: DestinationsNavigator
) {
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
                title = { Text("Contributors", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
//                .padding(padding)
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    SettingsSectionLabel("Development Team")
                    RillExpressiveCard {
                        appContributors.forEachIndexed { index, contributor ->
                            RillListItem(
                                headline = contributor.name,
                                supporting = contributor.role,
                                trailingIcon = if (contributor.githubUrl != null) Icons.Outlined.Launch else null,
                                modifierTrailingIcon = Modifier.padding(end = 8.dp).size(20.dp),
                                onClick = {
                                    contributor.githubUrl?.let { openLink(context, it) }
                                }
                            )
                        }
                    }
                }
            }
            item {
                Column {
                    SettingsSectionLabel("Apps That Inspired Us")
                    RillExpressiveCard {
                        otherContributors.forEachIndexed { index, contributor ->
                            RillListItem(
                                headline = contributor.name,
                                supporting = contributor.role,
                                trailingIcon = if (contributor.githubUrl != null) Icons.Outlined.Launch else null,
                                modifierTrailingIcon = Modifier.padding(end = 8.dp).size(20.dp),
                                onClick = {
                                    contributor.githubUrl?.let { openLink(context, it) }
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
