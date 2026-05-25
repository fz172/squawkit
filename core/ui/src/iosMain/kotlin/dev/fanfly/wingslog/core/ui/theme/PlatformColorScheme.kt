package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme? {
  // The aviation palette is product identity on every platform.
  return null
}
