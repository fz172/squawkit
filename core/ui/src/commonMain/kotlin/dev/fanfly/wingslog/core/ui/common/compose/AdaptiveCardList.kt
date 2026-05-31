package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lays out cards in a single column or an N-column grid, depending on [columns].
 *
 * Wide screens (see [LayoutTier.cardColumns]) get a multi-column grid; phone/rail tiers fall back to
 * a single column. Built from plain rows so it composes safely inside an existing `verticalScroll`
 * column (unlike a lazy grid). Each cell fills its width, so the card content should use
 * `Modifier.fillMaxWidth()`.
 */
@Composable
fun <T> AdaptiveCardList(
  items: List<T>,
  columns: Int,
  modifier: Modifier = Modifier,
  spacing: Dp = 12.dp,
  itemContent: @Composable (T) -> Unit,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
    if (columns <= 1) {
      items.forEach { item ->
        Box(modifier = Modifier.fillMaxWidth()) { itemContent(item) }
      }
    } else {
      items.chunked(columns).forEach { rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
          rowItems.forEach { item ->
            Box(modifier = Modifier.weight(1f)) { itemContent(item) }
          }
          // Keep the last (short) row's cells aligned with the grid above.
          repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
      }
    }
  }
}
