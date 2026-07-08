package dev.fanfly.wingslog.feature.squawk.update.compose

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import dev.fanfly.wingslog.core.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.update.generated.resources.Res
import wingslog.feature.squawk.update.generated.resources.dismiss_no_work_planned
import wingslog.feature.squawk.update.generated.resources.fixed_option_label

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

/**
 * A contextual menu displayed when the user clicks "Resolve" button in squawk
 * editing page. The menu asks the user to choose resolution
 * type (dismissed/fixed). Rendered as a speech bubble anchored above and
 * centered on the Resolve button, with a tail pointing back down at it.
 */
@Composable
fun ResolveOptionsMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  onDismissNoWorkPlanned: () -> Unit,
  onFixedClick: () -> Unit,
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
        ResolveMenuItem(
          icon = Icons.Default.Close,
          iconBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
          iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
          label = stringResource(Res.string.dismiss_no_work_planned),
          onClick = onDismissNoWorkPlanned,
        )
        HorizontalDivider(
          modifier = Modifier.padding(
            horizontal = Spacing.small,
            vertical = Spacing.extraSmall
          ),
          color = MaterialTheme.colorScheme.outlineVariant,
        )
        ResolveMenuItem(
          icon = Icons.Default.Check,
          iconBackground = MaterialTheme.statusColors.positive.container,
          iconTint = MaterialTheme.statusColors.positive.accent,
          label = stringResource(Res.string.fixed_option_label),
          onClick = onFixedClick,
        )
      }
    }
  }
}

@Composable
private fun ResolveMenuItem(
  icon: ImageVector,
  iconBackground: Color,
  iconTint: Color,
  label: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(ItemCornerRadius))
      .clickable(onClick = onClick)
      .padding(horizontal = Spacing.medium, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(ItemIconSize)
        .background(iconBackground, RoundedCornerShape(ItemIconCornerRadius)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(15.dp),
      )
    }
    Spacer(modifier = Modifier.width(Spacing.medium))
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
    )
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
