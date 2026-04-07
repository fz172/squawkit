package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme? {
  return if (darkTheme) {
    darkColorScheme(
      primary = AppleBlue,
      onPrimary = AppleSystemBackgroundDark,
      surface = AppleSystemBackgroundDark,
      background = AppleSystemBackgroundDark,
      surfaceContainer = AppleSecondarySystemBackgroundDark,
      outlineVariant = AppleSeparatorDark,
      // Maintain aviation amber for tertiary
      tertiary = Amber80,
      onTertiary = Amber10,
      tertiaryContainer = Amber30,
      onTertiaryContainer = Amber90,
    )
  } else {
    lightColorScheme(
      primary = AppleBlue,
      onPrimary = AppleSystemBackgroundLight,
      surface = AppleSystemBackgroundLight,
      background = AppleSystemBackgroundLight,
      surfaceContainer = AppleSecondarySystemBackgroundLight,
      outlineVariant = AppleSeparatorLight,
      // Maintain aviation amber for tertiary
      tertiary = Amber40,
      onTertiary = AppleSystemBackgroundLight,
      tertiaryContainer = Amber90,
      onTertiaryContainer = Amber10,
    )
  }
}
