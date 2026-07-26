package dev.fanfly.wingslog.feature.tasks.update.compose

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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.NoteAdd
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
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.create_work_log
import wingslog.feature.tasks.update.generated.resources.create_work_log_subtitle
import wingslog.feature.tasks.update.generated.resources.skip_this_cycle_option
import wingslog.feature.tasks.update.generated.resources.skip_this_cycle_option_subtitle

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
 * A contextual menu displayed when the user taps "Resolve" on the task editing screen — mirrors
 * squawk's ResolveOptionsMenu (feature/squawk/update). Rendered as a speech bubble anchored above
 * and centered on the Resolve button, with a tail pointing back down at it.
 */
@Composable
fun ResolveTaskOptionsMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  onCreateWorkLog: () -> Unit,
  onSkipThisCycle: () -> Unit,
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
        ResolveTaskMenuItem(
          icon = Icons.Default.NoteAdd,
          iconBackground = MaterialTheme.colorScheme.primaryContainer,
          iconTint = MaterialTheme.colorScheme.primary,
          label = stringResource(Res.string.create_work_log),
          subtitle = stringResource(Res.string.create_work_log_subtitle),
          onClick = onCreateWorkLog,
        )
        HorizontalDivider(
          modifier = Modifier.padding(
            horizontal = Spacing.small,
            vertical = Spacing.extraSmall
          ),
          color = MaterialTheme.colorScheme.outlineVariant,
        )
        ResolveTaskMenuItem(
          icon = Icons.Default.FastForward,
          iconBackground = MaterialTheme.statusColors.caution.container,
          iconTint = MaterialTheme.statusColors.caution.accent,
          label = stringResource(Res.string.skip_this_cycle_option),
          subtitle = stringResource(Res.string.skip_this_cycle_option_subtitle),
          onClick = onSkipThisCycle,
        )
      }
    }
  }
}

@Composable
private fun ResolveTaskMenuItem(
  icon: ImageVector,
  iconBackground: Color,
  iconTint: Color,
  label: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(ItemCornerRadius))
      .clickable(onClick = onClick)
      .padding(horizontal = Spacing.medium, vertical = Spacing.small),
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
    Column {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
