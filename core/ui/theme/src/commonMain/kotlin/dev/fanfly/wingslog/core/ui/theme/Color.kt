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

// --- Instrument Neutrals ---
// Every M3 neutral role is authored here, on the primary's hue (~258 in oklch, matching
// AviationBlue40's 255) at chroma 0.005-0.037, so
// surfaces read as cool blue-grey instrument panel rather than the violet-leaning Material 3
// baseline. A scheme that names only primary/secondary/tertiary inherits that baseline for every
// surface in the app, which is what happened before (DESIGN.md §4).
//
// Chosen so a card is visible on its background without a 1dp border. Measured as ΔL* — the right
// metric for a large-area edge, where a WCAG ratio is not — `surface` to `surfaceContainer` is 10.0
// in dark and 4.3 in light, against the baseline's 6.4 and 3.5.
//
// The light ladder stops where it does on purpose: StatusWarningLight (#8B5E00) has to clear 4.5:1
// as text on the deepest container, and it reaches 4.52 on Neutral88. Darkening the ramp further
// buys separation by failing contrast.
internal val Neutral04 = Color(0xFF06090F)   // dark: surfaceContainerLowest, surfaceDim
internal val Neutral06 = Color(0xFF0A0E14)   // dark: background, surface
internal val Neutral11 = Color(0xFF151C27)   // dark: surfaceContainerLow
internal val Neutral15 = Color(0xFF1B2431)   // dark: surfaceContainer
internal val Neutral20 = Color(0xFF222C3B)   // dark: surfaceContainerHigh, inverseSurface (light)
internal val Neutral25 = Color(0xFF293446)   // dark: surfaceContainerHighest
internal val Neutral30 = Color(0xFF313C4C)   // dark: outlineVariant
internal val Neutral35 = Color(0xFF3A4557)   // dark: surfaceVariant, surfaceBright
internal val Neutral40 = Color(0xFF545F72)   // light: onSurfaceVariant
internal val Neutral55 = Color(0xFF6B7A8F)   // both: outline
internal val Neutral70 = Color(0xFF9BA8BC)   // dark: onSurfaceVariant
internal val Neutral80 = Color(0xFFC3CBD8)   // light: outlineVariant
internal val Neutral86 = Color(0xFFD3DBE7)   // light: surfaceVariant, surfaceDim
internal val Neutral88 = Color(0xFFE0E6EF)   // light: surfaceContainerHighest
internal val Neutral90 = Color(0xFFE1E7F0)   // dark: onSurface, inverseSurface
internal val Neutral91 = Color(0xFFE4EAF2)   // light: surfaceContainerHigh
internal val Neutral94 = Color(0xFFE8EDF4)   // light: surfaceContainer — the card
internal val Neutral96 = Color(0xFFF1F4FA)   // light: surfaceContainerLow, inverseOnSurface
internal val Neutral98 = Color(0xFFF7F9FC)   // light: background, surface
internal val Neutral12 = Color(0xFF141A24)   // light: onSurface, onBackground

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
