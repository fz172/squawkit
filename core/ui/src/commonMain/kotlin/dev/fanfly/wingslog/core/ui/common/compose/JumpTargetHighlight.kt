package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** Total time the wash is visible: a beat at full strength, then a slow fade. */
private const val HOLD_MILLIS = 450
private const val FADE_MILLIS = 900

/** Peak opacity of the wash. Low enough that the card's own content stays fully legible. */
private const val PEAK_ALPHA = 0.28f

/**
 * Briefly washes this element in the primary colour, then fades out — the "you landed here" cue for
 * a card the app scrolled to on the pilot's behalf rather than one they scrolled to themselves.
 *
 * Used by every jump-to-record path: a tapped urgency notification (notifications design §5.3), and
 * the in-app jumps that already existed between a log and its affected tasks / resolved squawks.
 * Both drive the same `scrollTo…Id` parameter, so they get the same cue from this one place.
 *
 * Deliberately **not** a real Material ripple: a ripple is the acknowledgement of a touch, and
 * nobody touched this card — reusing it here would say "you pressed this". This is an ambient
 * attention wash instead, and it is drawn *over* the content (not behind it) so it reads on cards
 * that paint their own opaque background.
 *
 * A no-op when [active] is false, and it re-runs whenever [active] flips back to true, so jumping to
 * the same record twice in a row highlights twice.
 */
@Composable
fun Modifier.jumpTargetHighlight(
  active: Boolean,
  shape: Shape = RoundedCornerShape(Spacing.cardCornerRadius),
): Modifier {
  // Fully transparent unless a jump is in flight; nothing is drawn in the common case.
  val alpha = remember { Animatable(0f) }
  val color = MaterialTheme.colorScheme.primary

  LaunchedEffect(active) {
    if (!active) {
      alpha.snapTo(0f)
      return@LaunchedEffect
    }
    alpha.snapTo(PEAK_ALPHA)
    alpha.animateTo(
      targetValue = 0f,
      animationSpec = tween(
        durationMillis = FADE_MILLIS,
        delayMillis = HOLD_MILLIS,
        easing = LinearEasing,
      ),
    )
  }

  return this.drawWithContent {
    drawContent()
    if (alpha.value > 0f) {
      drawOutline(
        outline = shape.createOutline(size, layoutDirection, this),
        color = color,
        alpha = alpha.value,
      )
    }
  }
}
