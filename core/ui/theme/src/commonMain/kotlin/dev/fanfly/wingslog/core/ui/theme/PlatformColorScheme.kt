package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
expect fun platformColorScheme(
  darkTheme: Boolean,
  dynamicColor: Boolean
): ColorScheme?
