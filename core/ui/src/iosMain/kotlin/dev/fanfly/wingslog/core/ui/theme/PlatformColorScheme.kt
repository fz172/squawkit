package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme? {
  return null // Dynamic colors not available on iOS in this way
}
