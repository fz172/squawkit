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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * The contextual menu raised by a "Resolve" bottom-bar button, listing the ways a record can be
 * resolved. Rendered as a speech bubble anchored above and centered on the button, with a tail
 * pointing back down at it.
 *
 * Pass it to [BottomButtons]' `dangerMenuContent` so it anchors to the danger slot. Used by both
 * the squawk and task edit screens — keep it feature-agnostic.
 */
@Composable
fun ResolveBubbleMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  actions: List<ResolveMenuAction>,
) {
  if (!expanded) return

  val density = LocalDensity.current
  val positionProvider = remember(density) {
    ResolveMenuPositionProvider(
      gapPx = with(density) { BubbleGap.toPx() }.toInt(),
      marginPx = with(density) { BubbleEdgeMargin.toPx() }.toInt(),
    )
  }

  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = true),
  ) {
    val bubbleShape = remember {
      SpeechBubbleShape(
        tailWidth = BubbleTailWidth,
        tailHeight = BubbleTailHeight,
        cornerRadius = BubbleCornerRadius,
      )
    }
    Box(
      modifier = Modifier
        .width(BubbleWidth)
        .shadow(elevation = 8.dp, shape = bubbleShape, clip = false)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, bubbleShape)
        .border(
          Spacing.hairline,
          MaterialTheme.colorScheme.outlineVariant,
          bubbleShape
        ),
    ) {
      Column(
        modifier = Modifier
          .padding(bottom = BubbleTailHeight)
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

private class ResolveMenuPositionProvider(
  private val gapPx: Int,
  private val marginPx: Int,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val idealX =
      anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
    val x = idealX.coerceIn(
      marginPx,
      (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(
        marginPx
      ),
    )
    val y = anchorBounds.top - popupContentSize.height - gapPx
    return IntOffset(x, y)
  }
}

/** A rounded rectangle with a small triangular tail centered on its bottom edge. */
private class SpeechBubbleShape(
  private val tailWidth: Dp,
  private val tailHeight: Dp,
  private val cornerRadius: Dp,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val tailWidthPx = with(density) { tailWidth.toPx() }
    val tailHeightPx = with(density) { tailHeight.toPx() }
    val cornerPx = with(density) { cornerRadius.toPx() }
    val bodyHeight = size.height - tailHeightPx
    val centerX = size.width / 2f

    val body = Path().apply {
      addRoundRect(
        RoundRect(
          left = 0f,
          top = 0f,
          right = size.width,
          bottom = bodyHeight,
          radiusX = cornerPx,
          radiusY = cornerPx,
        )
      )
    }
    val tail = Path().apply {
      moveTo(centerX - tailWidthPx / 2f, bodyHeight)
      lineTo(centerX, size.height)
      lineTo(centerX + tailWidthPx / 2f, bodyHeight)
      close()
    }
    val combined = Path().apply { op(body, tail, PathOperation.Union) }
    return Outline.Generic(combined)
  }
}
