package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.fanfly.wingslog.core.ui.theme.Spacing

private val BubbleWidth = 300.dp
private val BubbleCornerRadius = Spacing.buttonCornerRadius
private val BubbleTailWidth = 16.dp
private val BubbleTailHeight = 8.dp
private val BubbleGap = 14.dp
private val BubbleEdgeMargin = Spacing.large
private val BubbleContentPadding = 6.dp
private val ItemCornerRadius = 11.dp
private val ItemIconSize = 30.dp
private val ItemIconCornerRadius = 9.dp
private val ItemIconGlyphSize = 15.dp

// A single-line row needs more breathing room than a two-line one to read as the same height.
private val ItemPaddingSingleLine = 13.dp
private val ItemPaddingTwoLine = Spacing.small

/** One row of a [ResolveBubbleMenu]. */
data class ResolveMenuAction(
  val icon: ImageVector,
  val iconBackground: Color,
  val iconTint: Color,
  val label: String,
  /** Optional second line explaining what the option does. */
  val subtitle: String? = null,
  val onClick: () -> Unit,
)

/**
 * The contextual menu raised by a "Resolve" button, listing the ways a record can be resolved.
 * Rendered as a speech bubble above the anchor with a tail pointing back at it. When the anchor is
 * clamped away from the window edge the tail still points at the anchor's centre; when there is no
 * room above (a card at the top of a list) the bubble opens below with the tail on its top edge.
 *
 * Compose it inside the anchor — [BottomButtons]' `dangerMenuContent`, or a [SwipeAction]'s
 * `menuContent` — so the popup anchors there. Keep it feature-agnostic.
 */
@Composable
fun ResolveBubbleMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  actions: List<ResolveMenuAction>,
) {
  if (!expanded) return

  val density = LocalDensity.current
  var placement by remember { mutableStateOf<BubblePlacement?>(null) }
  val positionProvider = remember(density) {
    ResolveMenuPositionProvider(
      gapPx = with(density) { BubbleGap.toPx() }.toInt(),
      marginPx = with(density) { BubbleEdgeMargin.toPx() }.toInt(),
      onPlaced = { placement = it },
    )
  }
  val tailSide = placement?.tailSide ?: BubbleTailSide.Bottom

  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = true),
  ) {
    DisableSelection {
      val bubbleShape = remember(placement) {
        SpeechBubbleShape(
          tailWidth = BubbleTailWidth,
          tailHeight = BubbleTailHeight,
          cornerRadius = BubbleCornerRadius,
          tailCenterX = placement?.tailCenterX?.toFloat(),
          tailSide = tailSide,
        )
      }
      Box(
        modifier = Modifier
          .width(BubbleWidth)
          .shadow(elevation = 8.dp, shape = bubbleShape, clip = false)
          .background(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            bubbleShape
          )
          .border(
            Spacing.hairline,
            MaterialTheme.colorScheme.outlineVariant,
            bubbleShape
          ),
      ) {
        Column(
          modifier = Modifier
            .padding(
              top = if (tailSide == BubbleTailSide.Top) BubbleTailHeight else 0.dp,
              bottom = if (tailSide == BubbleTailSide.Bottom) BubbleTailHeight else 0.dp,
            )
            .padding(BubbleContentPadding),
        ) {
          actions.forEachIndexed { index, action ->
            if (index > 0) {
              HorizontalDivider(
                modifier = Modifier.padding(
                  horizontal = Spacing.small,
                  vertical = Spacing.extraSmall
                ),
                color = MaterialTheme.colorScheme.outlineVariant,
              )
            }
            ResolveMenuItem(action)
          }
        }
      }
    }
  }
}

@Composable
private fun ResolveMenuItem(action: ResolveMenuAction) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(ItemCornerRadius))
      .clickable(onClick = action.onClick)
      .padding(
        horizontal = Spacing.medium,
        vertical = if (action.subtitle == null) ItemPaddingSingleLine
        else ItemPaddingTwoLine,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(ItemIconSize)
        .background(
          action.iconBackground,
          RoundedCornerShape(ItemIconCornerRadius)
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = action.icon,
        contentDescription = null,
        tint = action.iconTint,
        modifier = Modifier.size(ItemIconGlyphSize),
      )
    }
    Spacer(modifier = Modifier.width(Spacing.medium))
    Column {
      Text(
        text = action.label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      action.subtitle?.let { subtitle ->
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

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

private class ResolveMenuPositionProvider(
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

/**
 * A rounded rectangle with a small triangular tail on its top or bottom edge, centred on
 * [tailCenterX] (px from the left; the middle when null).
 */
private class SpeechBubbleShape(
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
