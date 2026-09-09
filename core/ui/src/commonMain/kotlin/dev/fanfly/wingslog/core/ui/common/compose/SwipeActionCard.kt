package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import kotlinx.coroutines.launch
import kotlin.math.abs
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

private val ActionWidth = 64.dp

/** Breathing room between the controls and both the card's edge and the list's. */
internal val ActionRowInset = 4.dp
private val ActionIconSize = 24.dp
private val DividerHeight = 34.dp
private val LiftElevation = 8.dp
private const val PositionalThreshold = 0.42f

/**
 * The reveal's easing, and the one place this component departs from DESIGN.md §6.
 *
 * §6 asks for ease-out and 150–250 ms. The duration stays inside that; the curve is the design
 * spec's own `cubic-bezier(.32,.72,0,1)` — an ease-out that leaves almost all its travel in the
 * first third, which is what makes a dragged card feel attached to the finger rather than played
 * back. `EaseOut` decelerates too gently for a gesture the user is still holding.
 */
private val RevealEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private const val SnapDurationMillis = 250

/** How far the card dims at full reveal. The controls behind it become the lit thing. */
private const val MaxDimAlpha = 0.16f

/** Icons hold at zero until the drag has committed, then fade across the next half of the travel. */
private const val IconFadeStart = 0.2f
private const val IconFadeSpan = 0.5f
private val IconSlideDistance = 14.dp

/**
 * Slides [content] sideways in either direction to reveal [actions], laid out side by side in the
 * given order on whichever side the user dragged toward. The open distance is the row's measured
 * width, so a card with one action opens about one button's width and a card with two about two.
 * An empty list disables the drag.
 *
 * The revealed controls are bare icons on the list background — no colored blocks. What separates
 * them from the record is the card itself, which lifts on a shadow, dims, and slides aside; the
 * icons fade and slide in behind it as the gesture commits. Tone lives in the icon color, so a
 * destructive action reads as destructive without a red slab arriving under the user's thumb.
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
  // 0 closed, 1 fully open. Everything that reacts to the drag reads this one number.
  val progress by remember(state) {
    derivedStateOf {
      val offset = state.offset
      val width = panelWidthPx.toFloat()
      if (offset.isNaN() || width <= 0f) 0f else (abs(offset) / width).coerceIn(0f, 1f)
    }
  }
  val isOpen by remember(state) { derivedStateOf { state.targetValue != SwipeRevealValue.Closed } }
  val shape = RoundedCornerShape(Spacing.cardCornerRadius)
  val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
    state = state,
    positionalThreshold = { distance -> distance * PositionalThreshold },
    animationSpec = tween(SnapDurationMillis, easing = RevealEasing),
  )

  Box(modifier = modifier.clip(shape)) {
    if (enabled) {
      // Always composed so the open distance is measured before the first drag; the buttons
      // themselves carry the fade, so nothing shows through while the card is closed.
      Box(modifier = Modifier.matchParentSize()) {
        Row(
          modifier = Modifier
            .align(revealedSide ?: Alignment.CenterStart)
            .fillMaxHeight()
            // Measured outside the inset, so the card slides the row's whole visual extent. Inside
            // it, the card stops 4dp short and parks over the first icon.
            .onSizeChanged { panelWidthPx = it.width }
            .padding(horizontal = ActionRowInset),
          horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          actions.forEachIndexed { index, action ->
            if (index > 0) ActionDivider()
            SwipeActionButton(action, progress)
          }
        }
      }
    }
    Box(
      modifier = Modifier
        .offset {
          IntOffset(state.offset.takeUnless { it.isNaN() }
                      ?.roundToInt() ?: 0, 0)
        }
        // The lift. DESIGN.md §6 rules out decorative shadows and this is not one: it exists only
        // while a drag is in flight, and it is what says the controls are *behind* the card rather
        // than beside it. Tonal elevation cannot express that — it tints, it does not separate.
        .shadow(LiftElevation * progress, shape),
    ) {
      Box(
        modifier = Modifier.anchoredDraggable(
          state = state,
          orientation = Orientation.Horizontal,
          enabled = enabled,
          flingBehavior = flingBehavior,
        ),
      ) {
        content()
        // Drawn, never interactive: it must not eat the drag it is reacting to.
        if (progress > 0f) {
          Box(
            modifier = Modifier
              .matchParentSize()
              .background(Color.Black.copy(alpha = progress * MaxDimAlpha)),
          )
        }
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
}

/** Hairline between two bare controls, doing the job the button backgrounds used to. */
@Composable
private fun ActionDivider() {
  Box(
    modifier = Modifier
      .width(1.dp)
      .height(DividerHeight)
      .background(MaterialTheme.colorScheme.outlineVariant),
  )
}

@Composable
private fun SwipeActionButton(action: SwipeAction, progress: Float) {
  val tint = when (action.tone) {
    SwipeActionTone.DESTRUCTIVE -> MaterialTheme.colorScheme.error
    SwipeActionTone.POSITIVE -> MaterialTheme.statusColors.positive.accent
  }
  val slide = with(LocalDensity.current) { IconSlideDistance.toPx() }
  Box(
    modifier = Modifier
      .fillMaxHeight()
      .width(ActionWidth)
      .clickable(onClick = action.onClick)
      .padding(vertical = Spacing.small),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.graphicsLayer {
        alpha = ((progress - IconFadeStart) / IconFadeSpan).coerceIn(0f, 1f)
        // Trails the card out rather than sitting waiting for it.
        translationX = slide * (1f - progress)
      },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Icon(
        action.icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(ActionIconSize),
      )
      Text(
        text = action.label,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Clip,
      )
    }
    action.menuContent?.invoke()
  }
}
