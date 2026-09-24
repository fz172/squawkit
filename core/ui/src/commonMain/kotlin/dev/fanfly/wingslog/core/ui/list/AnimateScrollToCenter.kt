package dev.fanfly.wingslog.core.ui.list

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState

/**
 * Scrolls until item [index] sits in the middle of the viewport, or as near as the ends of the list
 * allow. A row parked at the top edge sits under a pinned header and reads as "the list moved";
 * one in the middle reads as "this one".
 */
suspend fun LazyListState.animateScrollToCenter(index: Int) {
  // An item's height is unknown until it is laid out: bring it on screen, then settle the rest.
  if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
    val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    animateScrollToItem(index, scrollOffset = -viewport / 2)
  }
  val item =
    layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
  val viewportCenter =
    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
  animateScrollBy((item.offset + item.size / 2 - viewportCenter).toFloat())
}
