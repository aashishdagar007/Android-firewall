package com.asd.firewall.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── AEGIS XII Dark Color Scheme ─────────────────────────────────
// Material3 color scheme mapped to the AEGIS XII palette.
private val AegisDarkColorScheme = darkColorScheme(
    primary          = AccentCyan,
    onPrimary        = BgDark,
    primaryContainer = BgPanel,
    onPrimaryContainer = TextMain,

    secondary        = AccentBlue,
    onSecondary      = TextBright,
    secondaryContainer = BgPanel,
    onSecondaryContainer = TextMain,

    tertiary         = AccentPurple,
    onTertiary       = TextBright,

    background       = BgDark,
    onBackground     = TextMain,

    surface          = BgPanel,
    onSurface        = TextMain,
    surfaceVariant   = Color(0xFF101520),
    onSurfaceVariant = TextMuted,

    error            = AccentRed,
    onError          = TextBright,

    outline          = BorderColor,
    outlineVariant   = BorderLight,

    inverseSurface   = TextMain,
    inverseOnSurface = BgDark,
)

@Composable
fun AegisTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Full edge-to-edge dark UI
            window.statusBarColor     = BgDark.toArgb()
            window.navigationBarColor = BgDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars  = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = AegisDarkColorScheme,
        typography  = AegisTypography,
        content     = content
    )
}
