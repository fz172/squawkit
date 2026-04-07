package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
actual fun rememberBrandHeadlineFamily(): FontFamily {
  // On iOS, we use the system font (SF Pro) even for headlines to match native Apple aesthetics.
  return FontFamily.SansSerif
}
