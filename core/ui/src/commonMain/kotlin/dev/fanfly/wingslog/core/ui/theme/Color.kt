package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- Apple Platform Colors (iOS Native look) ---
val AppleBlue = Color(0xFF007AFF)
val AppleSystemBackgroundLight = Color(0xFFFFFFFF)
val AppleSystemBackgroundDark = Color(0xFF000000)
val AppleSecondarySystemBackgroundLight = Color(0xFFF2F2F7)
val AppleSecondarySystemBackgroundDark = Color(0xFF1C1C1E)
val AppleSeparatorLight = Color(0xFFC6C6C8)
val AppleSeparatorDark = Color(0xFF38383A)

// --- Aviation Blue (primary) ---
// References glass cockpit displays: Garmin G1000, ForeFlight, Jeppesen charts.
// A deep, confident instrument blue — nothing else in the productivity space uses this exact tone.
val AviationBlue80 = Color(0xFFA7C8FF)   // Primary — dark mode
val AviationBlue40 = Color(0xFF1A5FAE)   // Primary — light mode
val AviationBlue30 = Color(0xFF004785)   // Primary container — dark mode
val AviationBlue90 = Color(0xFFD5E3FF)   // Primary container — light mode (soft sky blue)
val AviationBlue10 = Color(0xFF001849)   // On primary container — light mode (deep navy)

// --- Blue-Gray (secondary) ---
// Cool, grounded. Pairs naturally with the blue primary and reads as "instrument panel."
val BlueGray80 = Color(0xFFBAC8E0)       // Secondary — dark mode
val BlueGray40 = Color(0xFF525E72)       // Secondary — light mode
val BlueGray30 = Color(0xFF3A4557)       // Secondary container — dark mode
val BlueGray90 = Color(0xFFD6E4F5)       // Secondary container — light mode
val BlueGray10 = Color(0xFF0E1C2B)       // On secondary container — light mode

// --- Instrument Amber (tertiary) ---
// The brand personality accent. References the amber glow of classic 6-pack gauges,
// advisory annunciators, and amber wingtip/taxi lights. Used sparingly — 10% of color moments.
val Amber80 = Color(0xFFFFBA4E)          // Tertiary — dark mode
val Amber40 = Color(0xFF7A5200)          // Tertiary — light mode
val Amber30 = Color(0xFF5B3D00)          // Tertiary container — dark mode
val Amber90 = Color(0xFFFFDFA6)          // Tertiary container — light mode (warm amber tint)
val Amber10 = Color(0xFF271900)          // On tertiary container — light mode

// --- Semantic Status ---
// These are used as TEXT colors on neutral surfaces, so they must meet WCAG 4.5:1.
// Previous pastel values (yellow #FFD54F, green #A5D6A7) failed contrast as text colors.
//
// Aviation semantics:
//   StatusOk     → "in the green" / airworthy / go
//   StatusWarning → amber caution annunciator — action required but not immediate
val StatusOkLight =
  Color(0xFF276B39)          // Dark forest green — airworthy / compliant (Light mode)
val StatusOkDark = Color(0xFF81C784)          // Light green for contrast (Dark mode)

val StatusOk: Color
  @Composable
  get() = if (isSystemInDarkTheme()) StatusOkDark else StatusOkLight

// Light Mode Colors
val StatusWarningLight = Color(0xFF8B5E00)          // Darker text/icon
val StatusWarningContainerLight = Color(0xFFFFECB3) // Pale background

// Dark Mode Colors
val StatusWarningDark = Color(0xFFFFCA28)           // Bright amber text/icon
val StatusWarningContainerDark = Color(0xFF514500)  // Deep, dark gold background

val StatusWarning: Color
  @Composable
  get() = if (isSystemInDarkTheme())
    StatusWarningDark
  else
    StatusWarningLight

val StatusWarningContainer: Color
  @Composable
  get() = if (isSystemInDarkTheme())
    StatusWarningContainerDark
  else
    StatusWarningContainerLight