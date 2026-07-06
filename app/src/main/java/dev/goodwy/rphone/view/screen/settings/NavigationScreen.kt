package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun NavigationScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()

    var pillNav             by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false)) }
    var favouritesEnabled   by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)) }
    var contactsEnabled     by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)) }
    var dialpadEnabled      by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)) }
    var notesEnabled        by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, true)) }
    var iconOnlyNav         by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_ICON_ONLY_NAV, false)) }
    var openDialpadDefault  by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_OPEN_DIALPAD_DEFAULT, false)) }

    // Default Tab dialog
    var showDefaultTabDialog by remember { mutableStateOf(false) }
    var defaultTab           by remember { mutableStateOf(prefs.getString(PreferenceManager.KEY_DEFAULT_TAB, "calls") ?: "calls") }
    data class TabOption(val key: String, val label: String, val icon: ImageVector, val enabled: Boolean)
    val tabOptions = listOf(
        TabOption("favorites", "Favourites", Icons.Outlined.Star, favouritesEnabled),
        TabOption("calls",     "Calls",      Icons.Rounded.AccessTime, true),
        TabOption("contacts",  "Contacts",   Icons.Filled.AccountCircle, contactsEnabled),
        TabOption("notes",     "Note",       Icons.AutoMirrored.Outlined.StickyNote2, notesEnabled)
    )

    // ── Default Tab Dialog ─────────────────────────────────────────────────
    if (showDefaultTabDialog) {
        Dialog(onDismissRequest = { showDefaultTabDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 20.dp, end = 20.dp), /*verticalArrangement = Arrangement.spacedBy(12.dp)*/) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.customColors.colorPurple,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text("Default Tab Section", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Choose which tab opens when the app starts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        tabOptions.forEach { option ->
                            val isSelected = defaultTab == option.key && option.enabled
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val cornerRadius by animateDpAsState(
                                if (isSelected || isPressed) 40.dp else 10.dp,
                                spring(stiffness = Spring.StiffnessMediumLow),
                                label = "ButtonShapeAnimation"
                            )
                            Surface(
                                enabled = option.enabled,
                                onClick = {
                                    defaultTab = option.key
                                    prefs.setString(PreferenceManager.KEY_DEFAULT_TAB, option.key)
                                },
                                shape = RoundedCornerShape(cornerRadius),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else cardColor,
                                modifier = Modifier.fillMaxWidth().alpha(if (option.enabled) 1f else 0.5f),
                                interactionSource = interactionSource
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        option.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    RadioButton(
                                        selected = isSelected,
                                        enabled = option.enabled,
                                        onClick = {
                                            defaultTab = option.key
                                            prefs.setString(PreferenceManager.KEY_DEFAULT_TAB, option.key)
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDefaultTabDialog = false }) { Text("Done") }
                    }
                }
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "navigationAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

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
                title = { Text("Navigation", fontWeight = FontWeight.Bold) },
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
                )
                .alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Tabs ────────────────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 0L) {
                    Column {
                        NavigationSectionLabel("Bottom Navigation Sections")
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = "Enable Favourites Section",
                                supporting = "Show Favourites tab in bottom navigation",
                                leadingIcon = Icons.Outlined.Star,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                checked = favouritesEnabled,
                                onCheckedChange = {
                                    favouritesEnabled = it
                                    prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = "Enable Contacts Section",
                                supporting = "Show Contacts tab in bottom navigation",
                                leadingIcon = Icons.Filled.AccountCircle,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                checked = contactsEnabled,
                                onCheckedChange = {
                                    contactsEnabled = it
                                    prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = "Enable Keypad Section",
                                supporting = "Show Keypad tab in bottom navigation",
                                leadingIcon = Icons.Default.Dialpad,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = dialpadEnabled,
                                onCheckedChange = {
                                    dialpadEnabled = it
                                    prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = "Enable Notes Section",
                                supporting = "Show Notes tab in bottom navigation",
                                leadingIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = notesEnabled,
                                onCheckedChange = {
                                    notesEnabled = it
                                    prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, it)
                                }
                            )
                        }
                    }
                }
            }

            // ── Navigation Style ────────────────────────────
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        NavigationSectionLabel("Navigation Style")
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = "Pill Style Navigation",
                                supporting = "Show a floating pill-style nav bar instead of the standard bottom bar",
                                leadingIcon = Icons.Rounded.CallToAction,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
                                checked = pillNav,
                                onCheckedChange = {
                                    pillNav = it
                                    prefs.setBoolean(PreferenceManager.KEY_PILL_NAV, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = "Icon-Only Bottom Bar",
                                supporting = "Removes text labels from navigation",
                                leadingIcon = Icons.Rounded.Interests,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
                                checked = iconOnlyNav,
                                onCheckedChange = { iconOnlyNav = it; prefs.setBoolean(PreferenceManager.KEY_ICON_ONLY_NAV, it) }
                            )
                        }
                    }
                }
            }

            // ── Navigation ───────────────────────────────────────
//            item {
//                RillAnimatedSection(delayMs = 120L) {
//                    Column {
//                        NavigationSectionLabel("Home Screen")
//                        RillExpressiveCard {
//                            RillListItem(
//                                headline = "Default Tab Section",
//                                supporting = "Choose which tab opens when the app starts (currently: ${tabOptions.firstOrNull { it.key == defaultTab }?.label ?: "Calls"})",
//                                leadingIcon = Icons.Rounded.Home,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
//                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
//                                trailingIcon = Icons.Default.ChevronRight,
//                                onClick = { showDefaultTabDialog = true }
//                            )
//                            RillSwitchListItem(
//                                headline = "Open Dialpad by Default",
//                                supporting = "Show dialpad automatically when app starts",
//                                leadingIcon = Icons.Outlined.Dialpad,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
//                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
//                                checked = openDialpadDefault,
//                                onCheckedChange = {
//                                    openDialpadDefault = it
//                                    prefs.setBoolean(PreferenceManager.KEY_OPEN_DIALPAD_DEFAULT, it)
//                                }
//                            )
//                        }
//                    }
//                }
//            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun NavigationSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary
    )
}
