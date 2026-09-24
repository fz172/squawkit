package dev.fanfly.wingslog.core.ui.menu

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * A rounded rectangle with a small triangular tail on its top or bottom edge, centred on
 * [tailCenterX] (px from the left; the middle when null).
 */
internal class SpeechBubbleShape(
  private val tailWidth: Dp,
  private val tailHeight: Dp,
  private val cornerRadius: Dp,
  private val tailCenterX: Float?,
  private val tailSide: BubbleTailSide,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val tailWidthPx = with(density) { tailWidth.toPx() }
    val tailHeightPx = with(density) { tailHeight.toPx() }
    val cornerPx = with(density) { cornerRadius.toPx() }
    val centerX = clampTailCenter(
      tailCenterX = tailCenterX ?: (size.width / 2f),
      width = size.width,
      cornerPx = cornerPx,
      tailWidthPx = tailWidthPx,
    )
    val bodyTop = if (tailSide == BubbleTailSide.Top) tailHeightPx else 0f
    val bodyBottom =
      if (tailSide == BubbleTailSide.Bottom) size.height - tailHeightPx else size.height

    val body = Path().apply {
      addRoundRect(
        RoundRect(
          left = 0f,
          top = bodyTop,
          right = size.width,
          bottom = bodyBottom,
          radiusX = cornerPx,
          radiusY = cornerPx,
        )
      )
    }
    val tail = Path().apply {
      if (tailSide == BubbleTailSide.Bottom) {
        moveTo(centerX - tailWidthPx / 2f, bodyBottom)
        lineTo(centerX, size.height)
        lineTo(centerX + tailWidthPx / 2f, bodyBottom)
      } else {
        moveTo(centerX - tailWidthPx / 2f, bodyTop)
        lineTo(centerX, 0f)
        lineTo(centerX + tailWidthPx / 2f, bodyTop)
      }
      close()
    }
    val combined = Path().apply { op(body, tail, PathOperation.Union) }
    return Outline.Generic(combined)
  }
}
