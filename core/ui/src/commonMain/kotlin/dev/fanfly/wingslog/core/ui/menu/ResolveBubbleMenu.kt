package dev.fanfly.wingslog.core.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.fanfly.wingslog.core.ui.form.BottomButtons
import dev.fanfly.wingslog.core.ui.swipe.SwipeAction
import dev.fanfly.wingslog.core.ui.theme.Spacing

private val BubbleWidth = 300.dp

private val BubbleCornerRadius = Spacing.buttonCornerRadius

private val BubbleTailWidth = 16.dp

private val BubbleTailHeight = 8.dp

private val BubbleGap = 14.dp

private val BubbleEdgeMargin = Spacing.large

private val BubbleContentPadding = 6.dp

internal val ItemCornerRadius = 11.dp

internal val ItemIconSize = 30.dp

internal val ItemIconCornerRadius = 9.dp

internal val ItemIconGlyphSize = 15.dp

// A single-line row needs more breathing room than a two-line one to read as the same height.
internal val ItemPaddingSingleLine = 13.dp

internal val ItemPaddingTwoLine = Spacing.small

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
