package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Portrait
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.customColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AvatarsPreferenceScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()

    var showFirstLetter           by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, true)) }
    var colorfulAvatars           by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, true)) }
    var primaryColorAvatars       by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_PRIMARY_COLOR_AVATARS, false)) }
    var secondaryColorAvatars     by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SECONDARY_COLOR_AVATARS, false)) }
    var googleContactColorAvatars by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_GOOGLE_CONTACTS_AVATARS, false)) }
    var avatarFrame               by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_AVATAR_FRAME, false)) }
    var showPicture               by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SHOW_PICTURE, true)) }

    val context = LocalContext.current
    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    val isRotation90 = rotation == Surface.ROTATION_90
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(R.string.avatars_settings)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
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
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 32.dp),
                            horizontalArrangement = Arrangement.Center
                        ){
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                RillAvatar(
                                    name = "John  Doe",
                                    modifier = Modifier.size(64.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "John Doe",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val context = LocalContext.current
                                val drawableUri = "android.resource://${context.packageName}/${R.drawable.avatar}"
                                RillAvatar(
                                    name = "Jane Doe",
                                    photoUri = drawableUri,
                                    modifier = Modifier.size(64.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Jane Doe",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                        RillExpressiveCard {
                            RillSelectListItem(
                                headline = stringResource(R.string.avatar_colors),
                                leadingIcon = Icons.Rounded.Palette,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                                options = listOf(
                                    stringResource(R.string.colorful) to 0,
                                    stringResource(R.string.primary_color) to 1,
                                    stringResource(R.string.secondary_color) to 2,
                                    stringResource(R.string.google_contacts_color) to 3
                                ),
                                selectedValue = when {
                                    primaryColorAvatars -> 1
                                    secondaryColorAvatars -> 2
                                    googleContactColorAvatars -> 3
                                    else -> 0
                                },
                                onValueChange = { newValue: Int ->
                                    when(newValue) {
                                        1 -> {
                                            prefs.setBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, false)
                                            prefs.setBoolean(PreferenceManager.KEY_PRIMARY_COLOR_AVATARS, true)
                                            prefs.setBoolean(PreferenceManager.KEY_SECONDARY_COLOR_AVATARS, false)
                                            prefs.setBoolean(PreferenceManager.KEY_GOOGLE_CONTACTS_AVATARS, false)
                                        }
                                        2 -> {
                                            prefs.setBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, false)
                                            prefs.setBoolean(PreferenceManager.KEY_PRIMARY_COLOR_AVATARS, false)
                                            prefs.setBoolean(PreferenceManager.KEY_SECONDARY_COLOR_AVATARS, true)
                                            prefs.setBoolean(PreferenceManager.KEY_GOOGLE_CONTACTS_AVATARS, false)
                                        }
                                        3 -> {
                                            prefs.setBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, false)
                                            prefs.setBoolean(PreferenceManager.KEY_PRIMARY_COLOR_AVATARS, false)
                                            prefs.setBoolean(PreferenceManager.KEY_SECONDARY_COLOR_AVATARS, false)
                                            prefs.setBoolean(PreferenceManager.KEY_GOOGLE_CONTACTS_AVATARS, true)
                                        }
                                        else -> {
                                            prefs.setBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, true)
                                            prefs.setBoolean(PreferenceManager.KEY_PRIMARY_COLOR_AVATARS, false)
                                            prefs.setBoolean(PreferenceManager.KEY_SECONDARY_COLOR_AVATARS, false)
                                            prefs.setBoolean(PreferenceManager.KEY_GOOGLE_CONTACTS_AVATARS, false)
                                        }
                                    }
                                }
                            )
                            RillSwitchListItem(
                                headline = stringResource(R.string.show_first_letter_in_avatar),
                                supporting = stringResource(R.string.show_first_letter_in_avatar_subtitle),
                                leadingIcon = if (showFirstLetter) Icons.Rounded.Title else Icons.Rounded.AccountCircle,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                                checked = showFirstLetter,
                                onCheckedChange = { showFirstLetter = it; prefs.setBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, it) }
                            )
                            RillSwitchListItem(
                                headline = stringResource(R.string.avatar_frame),
                                supporting = stringResource(R.string.avatar_frame_subtitle),
                                leadingIcon = if (avatarFrame) ImageVector.vectorResource(id = R.drawable.ic_person_border)
                                else ImageVector.vectorResource(id = R.drawable.ic_person_no_border),
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                                checked = avatarFrame,
                                onCheckedChange = { avatarFrame = it; prefs.setBoolean(PreferenceManager.KEY_AVATAR_FRAME, it) }
                            )
                            RillSwitchListItem(
                                headline = stringResource(R.string.show_picture_in_avatar),
                                supporting = stringResource(R.string.show_picture_in_avatar_subtitle),
                                leadingIcon = if (showPicture) Icons.Rounded.Portrait else Icons.Rounded.TextFields,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                                checked = showPicture,
                                onCheckedChange = { showPicture = it; prefs.setBoolean(PreferenceManager.KEY_SHOW_PICTURE, it) }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding()) }
        }
    }
}
