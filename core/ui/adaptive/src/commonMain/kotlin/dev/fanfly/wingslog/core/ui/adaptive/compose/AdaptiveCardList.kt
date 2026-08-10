package dev.fanfly.wingslog.core.ui.adaptive.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

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
  spacing: Dp = Spacing.medium,
  /**
   * Items answering `true` are laid out as their own **full-width row spanning every column**,
   * instead of taking a single grid cell. Defaults to nothing spanning, so existing callers are
   * unaffected.
   *
   * Added for interleaved ad slots, which must never sit in one cell of a grid — but deliberately a
   * predicate rather than an ads-specific type, so `core:ui:adaptive` keeps knowing nothing about
   * `feature:ads`.
   */
  isSpanning: (T) -> Boolean = { false },
  itemContent: @Composable (T) -> Unit,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(spacing)
  ) {
    if (columns <= 1) {
      items.forEach { item ->
        Box(modifier = Modifier.fillMaxWidth()) { itemContent(item) }
      }
    } else if (items.any(isSpanning)) {
      // Chunk only the non-spanning runs, emitting each spanning item as its own full-width row so
      // the grid resumes cleanly underneath it.
      var index = 0
      while (index < items.size) {
        val item = items[index]
        if (isSpanning(item)) {
          Box(modifier = Modifier.fillMaxWidth()) { itemContent(item) }
          index++
          continue
        }
        val run = ArrayList<T>(columns)
        while (index < items.size && run.size < columns && !isSpanning(items[index])) {
          run += items[index]
          index++
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
          run.forEach { cell ->
            Box(modifier = Modifier.weight(1f)) { itemContent(cell) }
          }
          repeat(columns - run.size) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    } else {
      items.chunked(columns)
        .forEach { rowItems ->
          Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            rowItems.forEach { item ->
              Box(modifier = Modifier.weight(1f)) { itemContent(item) }
            }
            // Keep the last (short) row's cells aligned with the grid above.
            repeat(columns - rowItems.size) {
              Spacer(
                modifier = Modifier.weight(
                  1f
                )
              )
            }
          }
        }
    }
  }
}
