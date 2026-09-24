package dev.fanfly.wingslog.core.ui.menu

import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

internal enum class BubbleTailSide { Top, Bottom }

/** Where the bubble goes and where its tail points, in the popup's own coordinates. */
internal data class BubblePlacement(
  val offset: IntOffset,
  /** The anchor's centre relative to the bubble's left edge; the tail is clamped into the body. */
  val tailCenterX: Int,
  val tailSide: BubbleTailSide,
)

/**
 * Above the anchor when it fits, otherwise below; x clamped to the window margin. Pure, so the
 * position test can exercise the edge cases without a window.
 */
internal fun placeBubble(
  anchorBounds: IntRect,
  windowSize: IntSize,
  popupContentSize: IntSize,
  gapPx: Int,
  marginPx: Int,
): BubblePlacement {
  val idealX =
    anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
  val x = idealX.coerceIn(
    marginPx,
    (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(
      marginPx
    ),
  )
  val above = anchorBounds.top - popupContentSize.height - gapPx
  val (y, side) =
    if (above >= marginPx) above to BubbleTailSide.Bottom
    else (anchorBounds.bottom + gapPx) to BubbleTailSide.Top
  return BubblePlacement(IntOffset(x, y), anchorBounds.center.x - x, side)
}

/** Keeps the tail inside the bubble's straight edge, clear of both rounded corners. */
internal fun clampTailCenter(
  tailCenterX: Float,
  width: Float,
  cornerPx: Float,
  tailWidthPx: Float,
): Float {
  val min = cornerPx + tailWidthPx / 2f
  val max = (width - cornerPx - tailWidthPx / 2f).coerceAtLeast(min)
  return tailCenterX.coerceIn(min, max)
}
