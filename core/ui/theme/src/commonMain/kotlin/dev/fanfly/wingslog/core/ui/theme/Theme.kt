package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
  // Primary: Avionics Blue — the dominant brand color
  primary = AviationBlue40,
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = AviationBlue90,       // Soft sky blue — used in flight time cards, badges
  onPrimaryContainer = AviationBlue10,

  // Secondary: Blue-Gray — instrument panel tone
  secondary = BlueGray40,
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = BlueGray90,
  onSecondaryContainer = BlueGray10,

  // Tertiary: Instrument Amber — the personality accent
  tertiary = Amber40,
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Amber90,             // Warm amber tint — advisory surfaces
  onTertiaryContainer = Amber10,

  // Neutrals: authored, never inherited. Without these every surface falls through to the
  // Material 3 baseline, which is violet-leaning (DESIGN.md §4).
  background = Neutral98,
  onBackground = Neutral12,
  surface = Neutral98,
  onSurface = Neutral12,
  surfaceDim = Neutral86,
  surfaceBright = Color(0xFFFFFFFF),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Neutral96,
  surfaceContainer = Neutral94,
  surfaceContainerHigh = Neutral91,
  surfaceContainerHighest = Neutral88,
  surfaceVariant = Neutral86,
  onSurfaceVariant = Neutral40,
  outline = Neutral55,
  outlineVariant = Neutral80,
  inverseSurface = Neutral20,
  inverseOnSurface = Neutral96,
  inversePrimary = AviationBlue80,
  scrim = Color(0xFF000000),
)

private val DarkColorScheme = darkColorScheme(
  primary = AviationBlue80,
  onPrimary = AviationBlue10,
  primaryContainer = AviationBlue30,
  onPrimaryContainer = AviationBlue90,

  secondary = BlueGray80,
  onSecondary = BlueGray10,
  secondaryContainer = BlueGray30,
  onSecondaryContainer = BlueGray90,

  tertiary = Amber80,
  onTertiary = Amber10,
  tertiaryContainer = Amber30,
  onTertiaryContainer = Amber90,

  // See the light scheme: the same ramp, read from the dark end.
  background = Neutral06,
  onBackground = Neutral90,
  surface = Neutral06,
  onSurface = Neutral90,
  surfaceDim = Neutral04,
  surfaceBright = Neutral35,
  surfaceContainerLowest = Neutral04,
  surfaceContainerLow = Neutral11,
  surfaceContainer = Neutral15,
  surfaceContainerHigh = Neutral20,
  surfaceContainerHighest = Neutral25,
  surfaceVariant = Neutral35,
  onSurfaceVariant = Neutral70,
  outline = Neutral55,
  outlineVariant = Neutral30,
  inverseSurface = Neutral90,
  inverseOnSurface = Neutral20,
  inversePrimary = AviationBlue40,
  scrim = Color(0xFF000000),
)

@Composable
fun WingslogTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled: WingsLog uses a deliberate aviation palette.
  // Deriving colors from the user's wallpaper would erase the instrument-blue identity.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = platformColorScheme(darkTheme, dynamicColor) ?: when {
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  CompositionLocalProvider(
    LocalStatusColors provides statusColorsFor(colorScheme, darkTheme)
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = rememberWingslogTypography(),
      content = content
    )
  }
}
