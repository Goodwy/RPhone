package dev.goodwy.rphone.view.theme

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.koin.compose.koinInject
import java.io.File

private fun buildCustomColorScheme(primary: Color, dark: Boolean): ColorScheme {
    val argb = primary.toArgb()
    val r = android.graphics.Color.red(argb)
    val g = android.graphics.Color.green(argb)
    val b = android.graphics.Color.blue(argb)

    fun blend(a: Int, b2: Int, ratio: Float) = (a + (b2 - a) * ratio).toInt().coerceIn(0, 255)

    val secR = blend(r, 128, 0.4f)
    val secG = blend(g, 128, 0.4f)
    val secB = blend(b, 128, 0.4f)
    val secondary = Color(secR, secG, secB)

    val terR = blend(b, r, 0.3f)
    val terG = blend(r, g, 0.3f)
    val terB = blend(g, b, 0.3f)
    val tertiary = Color(terR, terG, terB)

    val secondaryContainer = if (dark) {
        Color(
            blend(secR, 40, 0.6f),
            blend(secG, 40, 0.6f),
            blend(secB, 40, 0.6f)
        )
    } else {
        Color(
            blend(secR, 230, 0.7f),
            blend(secG, 230, 0.7f),
            blend(secB, 230, 0.7f)
        )
    }

    val inverseSurface = if (dark) {
        Color(0xFFE8E8ED)
    } else {
        Color(0xFF1C1B1F)
    }

    val inversePrimary = if (dark) {
        Color(
            blend(r, 255, 0.8f),
            blend(g, 255, 0.8f),
            blend(b, 255, 0.8f)
        )
    } else {
        Color(
            blend(r, 0, 0.6f),
            blend(g, 0, 0.6f),
            blend(b, 0, 0.6f)
        )
    }

    return if (dark) {
        val primaryContainer = Color(
            blend(r, 255, 0.55f),
            blend(g, 255, 0.55f),
            blend(b, 255, 0.55f)
        )
        val onPrimaryContainer = Color(
            blend(r, 0, 0.7f),
            blend(g, 0, 0.7f),
            blend(b, 0, 0.7f)
        )
        darkColorScheme(
            primary = primaryContainer,
            onPrimary = onPrimaryContainer,
            primaryContainer = Color(blend(r, 40, 0.7f), blend(g, 40, 0.7f), blend(b, 40, 0.7f)),
            onPrimaryContainer = primaryContainer,
            secondary = Color(blend(secR, 200, 0.5f), blend(secG, 200, 0.5f), blend(secB, 200, 0.5f)),
            tertiary = Color(blend(terR, 200, 0.5f), blend(terG, 200, 0.5f), blend(terB, 200, 0.5f)),
            secondaryContainer = secondaryContainer,
            inverseSurface = inverseSurface,
            inversePrimary = inversePrimary,
            onSecondaryContainer = Color.White,
            inverseOnSurface = Color(0xFF1C1B1F)
        ).copy(
            background = Color(0xFF17191F),
            surfaceContainerLowest = Color(0xFF0D0E12), // Dialpad Key
            surface = Color(0xFF17191F),
            surfaceContainer = Color(0xFF1D1F27), // Dialpad, Bottom Bar
            surfaceContainerLow = Color(0xFF121319), // ModalBottomSheet
            surfaceContainerHigh = Color(0xFF20222A), // Little Button
            surfaceContainerHighest = Color(0xFF292C34),
            surfaceVariant = Color(0xFF23262E), // Header Card Contacts
            surfaceBright = Color(0xFF292C34), // Card Contacts, Search
        )
    } else {
        val primaryContainer = Color(
            blend(r, 255, 0.75f),
            blend(g, 255, 0.75f),
            blend(b, 255, 0.75f)
        )
        val onPrimaryContainer = Color(
            blend(r, 0, 0.7f),
            blend(g, 0, 0.7f),
            blend(b, 0, 0.7f)
        )
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            tertiary = tertiary,
            secondaryContainer = secondaryContainer,
            inverseSurface = inverseSurface,
            inversePrimary = inversePrimary,
            onSecondaryContainer = Color.Black,
            inverseOnSurface = Color.White
        ).copy(
            background = Color(0xFFEDEDF6),
            surfaceContainerLowest = Color(0xFFFAF8FE), // Dialpad Key
            surface = Color(0xFFEDEDF6),
            surfaceContainerLow = Color(0xFFF3F3FA), // ModalBottomSheet
            surfaceContainer = Color(0xFFE7E7F1), // Dialpad, Bottom Bar
            surfaceContainerHigh = Color(0xFFE5E6F0), // Little Button
            surfaceContainerHighest = Color(0xFFE7E7F1), //
            surfaceVariant = Color(0xFFE1E2ED), // Header Card Contacts
            surfaceBright = Color(0xFFFAF8FE), // Card, Search
        )
    }
}

@Composable
fun Rill4Theme(
    systemDark: Boolean = isSystemInDarkTheme(),
    prefs: PreferenceManager = koinInject(),
    content: @Composable () -> Unit
) {
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()

    val themeMode      = prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto"
    val dynamicColor   = prefs.getBoolean(PreferenceManager.KEY_DYNAMIC_COLORS, true)
    val customPrimaryInt = prefs.getInt("custom_primary_color", 0)
    val customFontPath = prefs.getString(PreferenceManager.KEY_CUSTOM_FONT_PATH, null)
    val fontSizeScale  = prefs.getFloat(PreferenceManager.KEY_CUSTOM_FONT_SIZE, 1.0f)

    val darkTheme = when (themeMode) {
        "light", "white"  -> false
        "dark",  "black"  -> true
        "auto_bw"         -> systemDark
        else              -> systemDark
    }

    val context = LocalContext.current

    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context).copy(
                //Default color, Darker -> Lighter
                background = Color(0xFF17191F),
                surfaceContainerLowest = Color(0xFF0D0E12), // Dialpad Key
                surface = Color(0xFF17191F),
                surfaceContainerLow = Color(0xFF121319), // ModalBottomSheet
                surfaceContainer = Color(0xFF1D1F27), // Bottom Bar
                surfaceContainerHigh = Color(0xFF20222A), // Little Button
                surfaceContainerHighest = Color(0xFF1D1F27), // Dialpad
                surfaceVariant = Color(0xFF23262E), // Header Card Contacts
                surfaceBright = Color(0xFF292C34), // Card Contacts, Search
            )
            else dynamicLightColorScheme(context).copy(
                //Default color, Lighter -> Darker
                background = Color(0xFFEDEDF6),
                surfaceContainerLowest = Color(0xFFFAF8FE), // Dialpad Key
                surface = Color(0xFFEDEDF6),
                surfaceContainerLow = Color(0xFFF3F3FA), // ModalBottomSheet
                surfaceContainer = Color(0xFFE7E7F1), // Bottom Bar
                surfaceContainerHigh = Color(0xFFE5E6F0), // Little Button
                surfaceContainerHighest = Color(0xFFE7E7F1), // Dialpad
                surfaceVariant = Color(0xFFE1E2ED), // Header Card Contacts
                surfaceBright = Color(0xFFFAF8FE), // Card, Search //0xFFFEFEFE
            )
        else -> {
            val primary = if (customPrimaryInt != 0) Color(customPrimaryInt.toLong() and 0xFFFFFFFFL)
                          else color_default_primary
            buildCustomColorScheme(primary, darkTheme)
        }
    }

    colorScheme = when (themeMode) {
        "black" -> colorScheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, // Dialpad Key
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222), // Dialpad
            surfaceVariant = Color(0xFF1E1E1E),
            surfaceBright = Color(0xFF222222), // Card, Search
        )
        "white" -> colorScheme.copy(
            background = Color(0xFFF6F6F6), surface = Color(0xFFF6F6F6),
            surfaceContainerLowest = Color.White, // Dialpad Key
            surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainer = Color(0xFFF0F0F0),
            surfaceContainerHigh = Color(0xFFE8E8E8),
            surfaceContainerHighest = Color(0xFFE8E8E8), // Dialpad
            surfaceVariant = Color(0xFFE0E0E0),
            surfaceBright = Color.White, // Card, Search
        )
        "auto_bw" -> if (darkTheme) colorScheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, // Dialpad Key
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222), // Dialpad
            surfaceVariant = Color(0xFF1E1E1E),
            surfaceBright = Color(0xFF222222), // Card, Search
        ) else colorScheme.copy(
            background = Color(0xFFF6F6F6), surface = Color(0xFFF6F6F6),
            surfaceContainerLowest = Color.White, // Dialpad Key
            surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainer = Color(0xFFF0F0F0),
            surfaceContainerHigh = Color(0xFFE8E8E8),
            surfaceContainerHighest = Color(0xFFE8E8E8), // Dialpad
            surfaceVariant = Color(0xFFE0E0E0),
            surfaceBright = Color.White, // Card, Search
        )
        else -> colorScheme
    }

    // ── Sync status bar / nav bar with theme ──────────────────────────────────
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Edge-to-edge: let content flow under transparent system bars instead of a
            // solid tinted status bar block.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            // Light (white) icons belong on a dark background, and vice-versa — this was
            // previously inverted, making the status bar icons blend into the background.
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme        }
    }

    val customFontFamily: FontFamily = remember(customFontPath, settingsState) {
        if (customFontPath != null) {
            val file = File(customFontPath)
            if (file.exists()) {
                try { FontFamily(Typeface.createFromFile(file)) }
                catch (e: Exception) { FontFamily.Default }
            } else FontFamily.Default
        } else FontFamily.Default
    }

    val typography = remember(customFontFamily, fontSizeScale) {
        buildTypography(customFontFamily, fontSizeScale)
    }

    val customColors = rememberCustomColors(
        darkTheme = darkTheme
    )

    CompositionLocalProvider(
        LocalCustomColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

// My Colors
data class CustomColors(
    val colorDarkIndigo: Color,
    val colorDarkBlue: Color,
    val colorDarkPink: Color,
    val colorDarkOrange: Color,
    val colorDarkAmber: Color,
    val colorDarkGreen: Color,
    val colorDarkOliva: Color,
    val colorDarkCyan: Color,
    val colorDarkRed: Color,
    val colorDarkPurple: Color,
    val colorIndigo: Color,
    val colorBlue: Color,
    val colorPink: Color,
    val colorOrange: Color,
    val colorAmber: Color,
    val colorGreen: Color,
    val colorOliva: Color,
    val colorCyan: Color,
    val colorRed: Color,
    val colorPurple: Color
)

val LocalCustomColors = staticCompositionLocalOf {
    CustomColors(
        colorDarkIndigo  = Color(0xFF0041A5),
        colorDarkBlue    = Color(0xFF004F6B),
        colorDarkPink    = Color(0xFF9B0054),
        colorDarkOrange  = Color(0xFF7E2F00),
        colorDarkAmber   = Color(0xFF753700),
        colorDarkGreen   = Color(0xFF005428),
        colorDarkOliva   = Color(0xFF474747),
        colorDarkCyan    = Color(0xFF00505F),
        colorDarkRed     = Color(0xFF97000A),
        colorDarkPurple  = Color(0xFF5D26AA),
        colorIndigo      = Color(0xFF96CAFF),
        colorBlue        = Color(0xFF25D7FF),
        colorPink        = Color(0xFFFFA9E7),
        colorOrange      = Color(0xFFFFB279),
        colorAmber       = Color(0xFFFFBA00),
        colorGreen       = Color(0xFF5CDD7F),
        colorOliva       = Color(0xFFC7C7C7),
        colorCyan        = Color(0xFF00D8F7),
        colorRed         = Color(0xFFFFAFAB),
        colorPurple      = Color(0xFFDFB9FF),
    )
}

val ColorScheme.customColors: CustomColors
    @Composable
    get() = LocalCustomColors.current

@Composable
fun rememberCustomColors(
    darkTheme: Boolean
): CustomColors {
    return remember(darkTheme) {
        CustomColors(
            colorDarkIndigo  = Color(0xFF0041A5),
            colorDarkBlue    = Color(0xFF004F6B),
            colorDarkPink    = Color(0xFF9B0054),
            colorDarkOrange  = Color(0xFF7E2F00),
            colorDarkAmber   = Color(0xFF753700),
            colorDarkGreen   = Color(0xFF005428),
            colorDarkOliva   = Color(0xFF474747),
            colorDarkCyan    = Color(0xFF00505F),
            colorDarkRed     = Color(0xFF97000A),
            colorDarkPurple  = Color(0xFF5D26AA),
            colorIndigo      = if (darkTheme) Color(0xFF96CAFF) else Color(0xFFCBE5FF),
            colorBlue        = if (darkTheme) Color(0xFF25D7FF) else Color(0xFFB1EBFF),
            colorPink        = if (darkTheme) Color(0xFFFFA9E7) else Color(0xFFFFD6F0),
            colorOrange      = if (darkTheme) Color(0xFFFFB279) else Color(0xFFFFDABF),
            colorAmber       = if (darkTheme) Color(0xFFFFBA00) else Color(0xFFFFDF6A),
            colorGreen       = if (darkTheme) Color(0xFF5CDD7F) else Color(0xFFB0F1B6),
            colorOliva       = if (darkTheme) Color(0xFFC7C7C7) else Color(0xFFC7C7C7),
            colorCyan        = if (darkTheme) Color(0xFF00D8F7) else Color(0xFF98EFFF),
            colorRed         = if (darkTheme) Color(0xFFFFAFAB) else Color(0xFFFFD8DB),
            colorPurple      = if (darkTheme) Color(0xFFDFB9FF) else Color(0xFFF2DBFF),
        )
    }
}

