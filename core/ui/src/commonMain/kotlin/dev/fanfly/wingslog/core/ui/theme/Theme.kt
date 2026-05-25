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
