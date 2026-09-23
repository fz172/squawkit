package dev.fanfly.wingslog.core.ui.menu

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

internal class ResolveMenuPositionProvider(
  private val gapPx: Int,
  private val marginPx: Int,
  private val onPlaced: (BubblePlacement) -> Unit,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val placement =
      placeBubble(anchorBounds, windowSize, popupContentSize, gapPx, marginPx)
    onPlaced(placement)
    return placement.offset
  }
}
