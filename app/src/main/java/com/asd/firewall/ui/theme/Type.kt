package com.asd.firewall.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Font Families ───────────────────────────────────────────────
// We use system fonts as fallbacks since we can't load Google Fonts
// at compile time without a network call. For production, embed the
// Outfit and JetBrains Mono font files in app/src/main/res/font/.
val OutfitFamily = FontFamily.Default      // Replace with Outfit font files
val MonoFamily   = FontFamily.Monospace    // Replace with JetBrains Mono files

// ── AEGIS XII Typography ────────────────────────────────────────
val AegisTypography = Typography(
    // Display — used for large stat numbers
    displayLarge = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.Bold,
        fontSize    = 57.sp,
        lineHeight  = 64.sp,
        color       = TextBright
    ),
    displayMedium = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 45.sp,
        lineHeight  = 52.sp,
        color       = TextBright
    ),
    // Headline — section headers
    headlineLarge = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 22.sp,
        lineHeight  = 28.sp,
        color       = TextMain
    ),
    headlineMedium = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 18.sp,
        lineHeight  = 24.sp,
        color       = TextMain
    ),
    headlineSmall = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 15.sp,
        lineHeight  = 20.sp,
        color       = TextMuted
    ),
    // Title — panel headers, tab labels
    titleLarge = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 16.sp,
        lineHeight  = 22.sp,
        color       = TextMain
    ),
    titleMedium = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        color       = TextMain
    ),
    titleSmall = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        color       = TextMuted
    ),
    // Body — general content
    bodyLarge = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        color       = TextMain
    ),
    bodyMedium = TextStyle(
        fontFamily  = OutfitFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        color       = TextMuted
    ),
    // Label — small tags, badges
    labelLarge = TextStyle(
        fontFamily  = MonoFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 11.sp,
        lineHeight  = 14.sp,
        color       = AccentCyan
    ),
    labelMedium = TextStyle(
        fontFamily  = MonoFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 10.sp,
        lineHeight  = 14.sp,
        color       = TextMuted
    ),
    labelSmall = TextStyle(
        fontFamily  = MonoFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 9.sp,
        lineHeight  = 12.sp,
        color       = TextMuted
    )
)
