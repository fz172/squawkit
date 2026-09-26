package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Motion tokens (DESIGN.md §6). Continuity only: 150–250 ms, ease-out, no bounce. */
object Motion {
  // Durations, in milliseconds
  const val short = 150           // Fades, and anything leaving
  const val medium = 200          // Placement, anything arriving
  const val long =
    250            // The budget's ceiling: gesture settles, large surfaces

  val easeOut: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)      // Arriving
  val easeIn: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)       // Leaving
  val gestureEaseOut: Easing =
    CubicBezierEasing(0.32f, 0.72f, 0f, 1f) // Held gestures only

  fun <T> enter(): FiniteAnimationSpec<T> = tween(medium, easing = easeOut)
  fun <T> exit(): FiniteAnimationSpec<T> = tween(short, easing = easeIn)
}

@Composable
fun rememberReduceMotion(): Boolean = remember { isReduceMotionEnabled() }

/** The OS "reduce motion" preference, where Compose does not already honour it. */
internal expect fun isReduceMotionEnabled(): Boolean
