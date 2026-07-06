package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.ui.graphics.Color

// --- Aviation Blue (primary) ---
// References glass cockpit displays: Garmin G1000, ForeFlight, Jeppesen charts.
// A deep, confident instrument blue — nothing else in the productivity space uses this exact tone.
val AviationBlue80 = Color(0xFFA7C8FF)   // Primary — dark mode
val AviationBlue40 = Color(0xFF1A5FAE)   // Primary — light mode
val AviationBlue30 = Color(0xFF004785)   // Primary container — dark mode
val AviationBlue90 =
  Color(0xFFD5E3FF)   // Primary container — light mode (soft sky blue)
val AviationBlue10 =
  Color(0xFF001849)   // On primary container — light mode (deep navy)

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
val Amber90 =
  Color(0xFFFFDFA6)          // Tertiary container — light mode (warm amber tint)
val Amber10 = Color(0xFF271900)          // On tertiary container — light mode

// --- Semantic Status ---
// These are used as TEXT colors on neutral surfaces, so they must meet WCAG 4.5:1.
// Previous pastel values (yellow #FFD54F, green #A5D6A7) failed contrast as text colors.
//
// Aviation semantics:
//   positive → "in the green" / airworthy / go
//   caution  → amber caution annunciator — action required but not immediate
internal val StatusOkLight =
  Color(0xFF276B39)          // Dark forest green — airworthy / compliant (Light mode)
internal val StatusOkDark =
  Color(0xFF81C784)          // Light green for contrast (Dark mode)

internal val StatusOkContainerLight = Color(0xFFE3F2E8)
internal val StatusOkContainerDark = Color(0xFF1B4D2B)

// Light Mode Colors
internal val StatusWarningLight = Color(0xFF8B5E00)          // Darker text/icon
internal val StatusWarningContainerLight = Color(0xFFFFECB3) // Pale background

// Dark Mode Colors
internal val StatusWarningDark =
  Color(0xFFFFCA28)           // Bright amber text/icon
internal val StatusWarningContainerDark =
  Color(0xFF514500)  // Deep, dark gold background
