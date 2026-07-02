package com.asd.firewall.ui.theme

import androidx.compose.ui.graphics.Color

// ── AEGIS XII Color System ──────────────────────────────────────
// Mirrors the desktop dashboard CSS variables exactly.

// Backgrounds
val BgDark       = Color(0xFF030508)
val BgPanel      = Color(0xFF0C101A)
val BgPanelLight = Color(0x660C101A)  // 40% opacity panel
val BgCard       = Color(0xFF0F1520)  // Slightly lighter card bg

// Borders
val BorderColor  = Color(0x261A3040)  // rgba(0, 229, 255, 0.15)
val BorderLight  = Color(0x0DFFFFFF)  // rgba(255,255,255,0.05)
val BorderCyan   = Color(0x4000E5FF)  // 25% opacity cyan border

// Text
val TextMain     = Color(0xFFE2E8F0)
val TextMuted    = Color(0xFF8B9BB4)
val TextBright   = Color(0xFFFFFFFF)
val TextDim      = Color(0xFF4A5568)

// Accents (same as dashboard)
val AccentCyan   = Color(0xFF00E5FF)
val AccentBlue   = Color(0xFF3B82F6)
val AccentGreen  = Color(0xFF00E676)
val AccentRed    = Color(0xFFFF3355)
val AccentGold   = Color(0xFFFFD700)
val AccentPurple = Color(0xFFA855F7)
val AccentOrange = Color(0xFFFF8C00)
val AccentTeal   = Color(0xFF14B8A6)   // NEW: NeonTeal for variety
val AccentPink   = Color(0xFFEC4899)   // NEW: for threat map pings

// Glow colors (used in shadow/elevation effects)
val GlowCyan     = Color(0x5900E5FF)  // 35% opacity
val GlowRed      = Color(0x59FF3355)
val GlowGreen    = Color(0x5900E676)
val GlowPurple   = Color(0x59A855F7)  // NEW
val GlowTeal     = Color(0x5914B8A6)  // NEW

// Shimmer / skeleton loading colors
val ShimmerBase  = Color(0xFF1A2035)
val ShimmerHighlight = Color(0xFF253050)

// Security score gradient stops
val ScoreHigh    = Color(0xFF00E676)   // 80-100
val ScoreMid     = Color(0xFFFFD700)   // 50-79
val ScoreLow     = Color(0xFFFF3355)   // 0-49
