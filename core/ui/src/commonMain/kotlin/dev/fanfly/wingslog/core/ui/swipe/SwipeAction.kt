package dev.fanfly.wingslog.core.ui.swipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.ui.form.BottomButtons

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
