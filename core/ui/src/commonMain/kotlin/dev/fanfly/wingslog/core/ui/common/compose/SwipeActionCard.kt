package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One revealable action of a [SwipeActionCard]. */
@Immutable
data class SwipeAction(
  val icon: ImageVector,
  /** One or two short words, lexicon-resolved by the caller. A label that wraps is a caller bug. */
  val label: String,
  val tone: SwipeActionTone,
  val onClick: () -> Unit,
  /**
   * Optional popup composed inside this action's button, so a `Popup` placed here anchors to the
   * button (the same trick `BottomButtons.dangerMenuContent` uses). Resolve puts its bubble here.
   */
  val menuContent: (@Composable () -> Unit)? = null,
)

enum class SwipeActionTone { POSITIVE, DESTRUCTIVE }

/** Resting positions of a [SwipeActionCard]. `OpenStart` is dragged toward the end, uncovering the start edge. */
enum class SwipeRevealValue { OpenStart, Closed, OpenEnd }

/**
 * One open card per list. Remembered by the list and passed to every card; a card whose [key]
 * stops being [openKey] animates itself closed. Not saved across process death on purpose — a
 * revealed card is not state worth surviving it.
 */
@Stable
class SwipeRevealController {
  var openKey: Any? by mutableStateOf(null)
    private set

  fun open(key: Any) {
    openKey = key
  }

  fun close() {
    openKey = null
  }

  internal fun closeIf(key: Any) {
    if (openKey == key) openKey = null
  }

  /** Closes the open card on the first vertical scroll delta. Install on the list with `Modifier.nestedScroll`. */
  val closeOnScroll: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(
      available: Offset,
      source: NestedScrollSource
    ): Offset {
      if (available.y != 0f) close()
      return Offset.Zero
    }
  }
}

@Composable
fun rememberSwipeRevealController(): SwipeRevealController =
  remember { SwipeRevealController() }

private val ActionMinWidth = 72.dp
private const val PositionalThreshold = 0.4f
private const val SnapDurationMillis = 200

/**
 * Slides [content] sideways in either direction to reveal [actions], laid out side by side in the
 * given order on whichever side the user dragged toward. The open distance is the row's measured
 * width, so a card with one action opens about one button's width and a card with two about two.
 * An empty list disables the drag.
 *
 * Reveal-and-tap only: the drag never commits an action, so a long or accidental swipe changes
 * nothing. Tapping an open card closes it instead of firing the card's own click; a closed card's
 * tap passes through untouched. [key] is the record id, so a list update mid-drag keeps the state.
 */
@Composable
fun SwipeActionCard(
  actions: List<SwipeAction>,
  controller: SwipeRevealController,
  key: Any,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val enabled = actions.isNotEmpty()
  var panelWidthPx by remember { mutableIntStateOf(0) }
  val state =
    remember(key) { AnchoredDraggableState(initialValue = SwipeRevealValue.Closed) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(state, panelWidthPx, enabled) {
    val width = panelWidthPx.toFloat()
    state.updateAnchors(
      DraggableAnchors {
        SwipeRevealValue.Closed at 0f
        if (enabled && width > 0f) {
          SwipeRevealValue.OpenStart at width
          SwipeRevealValue.OpenEnd at -width
        }
      }
    )
  }
  // Settling open claims the controller; settling closed by hand releases it.
  LaunchedEffect(state, controller) {
    snapshotFlow { state.settledValue }.collect { settled ->
      if (settled == SwipeRevealValue.Closed) controller.closeIf(key) else controller.open(
        key
      )
    }
  }
  // Another card opening, a scroll, or an outside tap moves the key away: close.
  LaunchedEffect(state, controller) {
    snapshotFlow { controller.openKey }.collect { openKey ->
      if (openKey != key && state.settledValue != SwipeRevealValue.Closed) {
        state.animateTo(SwipeRevealValue.Closed)
      }
    }
  }

  // Which edge the drag is uncovering. Derived, so the row only recomposes when the sign flips.
  val revealedSide by remember(state) {
    derivedStateOf {
      val offset = state.offset
      when {
        offset.isNaN() || offset == 0f -> null
        offset > 0f -> Alignment.CenterStart
        else -> Alignment.CenterEnd
      }
    }
  }
  val isOpen by remember(state) { derivedStateOf { state.targetValue != SwipeRevealValue.Closed } }
  val shape = RoundedCornerShape(Spacing.cardCornerRadius)
  val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
    state = state,
    positionalThreshold = { distance -> distance * PositionalThreshold },
    animationSpec = tween(SnapDurationMillis, easing = EaseOut),
  )

  Box(modifier = modifier.clip(shape)) {
    if (enabled) {
      // Always composed so the open distance is measured before the first drag; invisible until
      // the card slides off it. Nothing peeks when closed.
      Box(modifier = Modifier.matchParentSize()) {
        Row(
          modifier = Modifier
            .align(revealedSide ?: Alignment.CenterStart)
            .alpha(if (revealedSide == null) 0f else 1f)
            .fillMaxHeight()
            .width(IntrinsicSize.Max)
            .clip(shape)
            .onSizeChanged { panelWidthPx = it.width },
        ) {
          actions.forEach { action -> SwipeActionButton(action) }
        }
      }
    }
    Box(
      modifier = Modifier
        .offset {
          IntOffset(state.offset.takeUnless { it.isNaN() }
                      ?.roundToInt() ?: 0, 0)
        }
        .anchoredDraggable(
          state = state,
          orientation = Orientation.Horizontal,
          enabled = enabled,
          flingBehavior = flingBehavior,
        ),
    ) {
      content()
      if (isOpen) {
        Box(
          modifier = Modifier
            .matchParentSize()
            .clickable(interactionSource = null, indication = null) {
              scope.launch { state.animateTo(SwipeRevealValue.Closed) }
            },
        )
      }
    }
  }
}

@Composable
private fun SwipeActionButton(action: SwipeAction) {
  val (container, onContainer) = when (action.tone) {
    SwipeActionTone.DESTRUCTIVE ->
      MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

    SwipeActionTone.POSITIVE ->
      MaterialTheme.statusColors.positive.container to MaterialTheme.statusColors.positive.accent
  }
  Box(
    modifier = Modifier
      .fillMaxHeight()
      .widthIn(min = ActionMinWidth)
      .background(container)
      .clickable(onClick = action.onClick)
      .padding(horizontal = Spacing.medium),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Icon(action.icon, contentDescription = null, tint = onContainer)
      Text(
        text = action.label,
        style = MaterialTheme.typography.labelMedium,
        color = onContainer,
        maxLines = 1,
        overflow = TextOverflow.Clip,
      )
    }
    action.menuContent?.invoke()
  }
}
