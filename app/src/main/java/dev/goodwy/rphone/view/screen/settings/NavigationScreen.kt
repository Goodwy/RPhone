package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.MoveUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.Title
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun NavigationScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()

    var pillNav             by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false)) }
    var showTabSectionsDialog by remember { mutableStateOf(false) }
    var tabShowFavorites    by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, false)) }
    var tabShowCalls        by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CALLS,      true)) }
    var tabShowContacts     by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)) }
    var tabShowDialpad      by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)) }
    var tabShowNotes        by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)) }
    var tabShowSearch       by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_SEARCH, false)) }
    var tabShowSettings     by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_SETTINGS, true)) }
    var iconOnlyNav         by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_ICON_ONLY_NAV, false)) }
    var openDialpadDefault  by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_OPEN_DIALPAD_DEFAULT, false)) }

    // Default Tab dialog
    var showDefaultTabDialog by remember { mutableStateOf(false) }
    var defaultTab           by remember { mutableStateOf(prefs.getString(PreferenceManager.KEY_DEFAULT_TAB, "calls") ?: "calls") }
    data class TabOption(val key: String, val label: String, val icon: ImageVector, val enabled: Boolean, val clickable: Boolean)
    val labelRecents =
        if (!tabShowFavorites && !tabShowContacts) stringResource(R.string.home_tab) else stringResource(R.string.recents)
    val iconRecents =
        if (!tabShowFavorites && !tabShowContacts) ImageVector.vectorResource(id = R.drawable.ic_house_fill) else Icons.Rounded.AccessTime
    val tabOptions = listOf(
        TabOption("favorites", stringResource(R.string.favorites),  Icons.Outlined.Assistant,       tabShowFavorites, true),
        TabOption("contacts",  stringResource(R.string.contacts),   Icons.Filled.AccountCircle,     tabShowContacts, true),
        TabOption("calls",     labelRecents,                        iconRecents,                    true, false),
        TabOption("dialpad",   stringResource(R.string.keypad),     Icons.Default.Dialpad, tabShowDialpad, true),
        TabOption("notes",     stringResource(R.string.call_notes), Icons.AutoMirrored.Outlined.StickyNote2, tabShowNotes, true),
        TabOption("search",    stringResource(R.string.search),     Icons.Default.Search,           tabShowSearch, true),
        TabOption("settings",  stringResource(R.string.settings),   ImageVector.vectorResource(id = R.drawable.ic_settings), tabShowSettings, true)
    )

    // Custom order of tab keys, persisted as a comma-separated string. Any tab keys
    // missing from a previously-saved (older) order are appended so new tabs always show.
    val tabOrder = remember {
        mutableStateListOf<String>().apply {
            val saved = prefs.getString(PreferenceManager.KEY_TAB_ORDER, null)
            val savedKeys = saved?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val validKeys = tabOptions.map { it.key }
            addAll(savedKeys.filter { it in validKeys })
            validKeys.forEach { key -> if (key !in this) add(key) }
        }
    }
    fun persistTabOrder() {
        prefs.setString(PreferenceManager.KEY_TAB_ORDER, tabOrder.joinToString(","))
    }

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
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else cardColor,
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

    // ── Tab Sections Dialog ──────────────────────────────────────────────────
    if (showTabSectionsDialog) {
        val density = LocalDensity.current
        val rowHeightDp = 52.dp
        val rowHeightPx = with(density) { rowHeightDp.toPx() }
        var draggedIndex by remember { mutableStateOf(-1) }
        var dragOffsetY by remember { mutableStateOf(0f) }

        fun tabChecked(key: String): Boolean = when (key) {
            "favorites"  -> tabShowFavorites
            "calls"      -> tabShowCalls
            "contacts"   -> tabShowContacts
            "dialpad"    -> tabShowDialpad
            "notes"      -> tabShowNotes
            "search"     -> tabShowSearch
            "settings"   -> tabShowSettings
            else         -> true
        }
        fun setTabChecked(key: String, value: Boolean) {
            when (key) {
                "favorites"  -> { tabShowFavorites = value;  prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES,  value) }
                "calls"      -> { tabShowCalls = value;      prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_CALLS,      value) }
                "contacts"   -> { tabShowContacts = value;   prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS,   value) }
                "dialpad"    -> { tabShowDialpad = value;    prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD,    value) }
                "notes"      -> { tabShowNotes = value;      prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES,      value) }
                "search"     -> { tabShowSearch = value;     prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_SEARCH,     value) }
                "settings"   -> { tabShowSettings = value;   prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_SETTINGS,   value) }
            }
        }

        RillDialog(
            onDismissRequest = { showTabSectionsDialog = false },
            title = stringResource(R.string.tab_sections),
            icon = Icons.Rounded.MoveUp,
            modifierIcon = Modifier.rotate(90f),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
            confirmButton = {
                TextButton(onClick = { showTabSectionsDialog = false }) { Text(stringResource(R.string.done)) }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.tab_sections_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Column {
                    tabOrder.forEachIndexed { index, tabKey ->
                        val option = tabOptions.firstOrNull { it.key == tabKey } ?: return@forEachIndexed
                        val isDragging = draggedIndex == index
                        key(tabKey) {
                            val cornerRadius by animateDpAsState(
                                if (option.enabled) 40.dp else 10.dp,
                                spring(stiffness = Spring.StiffnessMediumLow),
                                label = "ButtonShapeAnimation"
                            )
                            Surface(
                                shape = RoundedCornerShape(cornerRadius),
                                color = if (option.enabled) MaterialTheme.colorScheme.primaryContainer else cardColor,
                                tonalElevation = if (isDragging) 4.dp else 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragOffsetY else 0f
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeightDp)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (option.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        option.label,
                                        style = if (option.enabled) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (option.enabled) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (option.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Checkbox(
                                        enabled = option.clickable,
                                        checked = tabChecked(tabKey),
                                        onCheckedChange = { setTabChecked(tabKey, it) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = "Reorder ${option.label}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .pointerInput(tabKey) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggedIndex = index
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggedIndex = -1
                                                        dragOffsetY = 0f
                                                        persistTabOrder()
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = -1
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y
                                                        val moveBy = (dragOffsetY / rowHeightPx).roundToInt()
                                                        if (moveBy != 0 && draggedIndex >= 0) {
                                                            val newIndex = (draggedIndex + moveBy).coerceIn(0, tabOrder.lastIndex)
                                                            if (newIndex != draggedIndex) {
                                                                val moving = tabOrder.removeAt(draggedIndex)
                                                                tabOrder.add(newIndex, moving)
                                                                dragOffsetY -= moveBy * rowHeightPx
                                                                draggedIndex = newIndex
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                    )
                                }
                            }
                        }
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
                title = { Title(stringResource(R.string.navigations)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
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
//                        NavigationSectionLabel("Bottom Navigation Sections")
                        RillExpressiveCard {
                            RillListItem(
                                headline = stringResource(R.string.tab_sections),
                                supporting = stringResource(R.string.tab_sections_subtitle),
                                leadingIcon = Icons.Rounded.MoveUp,
                                modifierLeadingIcon = Modifier.rotate(90f),
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { showTabSectionsDialog = true }
                            )
                        }
                    }
                }
            }

            // ── Navigation Style ────────────────────────────
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.navigation_style))
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = stringResource(R.string.pill_style_navigation),
                                supporting = stringResource(R.string.pill_style_navigation_subtitle),
                                leadingIcon = Icons.Rounded.CallToAction,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = pillNav,
                                onCheckedChange = {
                                    pillNav = it
                                    prefs.setBoolean(PreferenceManager.KEY_PILL_NAV, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = stringResource(R.string.icon_only_bottom_bar),
                                supporting = stringResource(R.string.icon_only_bottom_bar_subtitle),
                                leadingIcon = Icons.Rounded.Interests,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
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

            item { Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding()) }
        }
    }
}
