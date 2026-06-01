package dev.fanfly.wingslog.core.ui.adaptive.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Route frame for add/edit forms presented through Navigation Compose dialog destinations.
 *
 * COMPACT keeps the existing full-screen form behavior. MEDIUM+ constrains the same form content in
 * a centered dialog while preserving the previous destination behind it.
 */
@Composable
fun AdaptiveFormDialogFrame(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val tier = layoutTierFor(maxWidth)
    CompositionLocalProvider(LocalLayoutTier provides tier) {
      if (tier == LayoutTier.COMPACT) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        ) {
          content()
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            modifier = Modifier
              .widthIn(max = ContentWidth.Form)
              .fillMaxWidth()
              .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
          ) {
            content()
          }
        }
      }
    }
  }
}
