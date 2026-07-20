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
import androidx.compose.ui.res.stringResource
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
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.Title

data class Contributor(
    val name: String,
    val role: String,
    val githubUrl: String? = null
)

val appContributors = listOf(
    Contributor("Goodwy", "Developer", GITHUB_DEV),
)

val otherContributors = listOf(
    Contributor("RivoPhoneApp", "user-grinch", "https://github.com/user-grinch/RivoPhoneApp"),
    Contributor("Ever-Dialer", "hari161008", "https://github.com/hari161008/Ever-Dialer"),
    Contributor( "Phone by Google","Google LLC", "https://play.google.com/store/apps/details?id=com.google.android.dialer"),
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
                title = { Title(stringResource(R.string.contributors)) },
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
                    SettingsSectionLabel(stringResource(R.string.development_team))
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
                    SettingsSectionLabel(stringResource(R.string.apps_that_inspired_us))
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

            item { Spacer(modifier = Modifier.height(80.dp).navigationBarsPadding()) }
        }
    }
}
