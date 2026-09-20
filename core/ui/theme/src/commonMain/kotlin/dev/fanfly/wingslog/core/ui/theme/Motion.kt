package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Motion tokens (DESIGN.md §6). Continuity only: 150–250 ms, ease-out, no bounce. */
object Motion {
  // Durations, in milliseconds
  const val short = 150           // Fades, and anything leaving
  const val medium = 200          // Placement, anything arriving
  const val long = 250            // The budget's ceiling: gesture settles, large surfaces

  val easeOut: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)      // Arriving
  val easeIn: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)       // Leaving
  val gestureEaseOut: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f) // Held gestures only

  val sharedAxisOffset = 30.dp    // How far a shared-axis page travels

  fun <T> enter(): FiniteAnimationSpec<T> = tween(medium, easing = easeOut)
  fun <T> exit(): FiniteAnimationSpec<T> = tween(short, easing = easeIn)
}

enum class MotionAxis { X, Y }

/**
 * Shared-axis transition between sibling pages. [forward] slides toward the axis start, as when
 * moving to a later sibling. Under Reduce Motion the travel is dropped and only the fade remains.
 */
@Composable
fun rememberSharedAxis(axis: MotionAxis): (forward: Boolean) -> ContentTransform {
  val reduceMotion = rememberReduceMotion()
  val offsetPx = with(LocalDensity.current) { Motion.sharedAxisOffset.roundToPx() }
  return remember(axis, reduceMotion, offsetPx) {
    { forward ->
      // The incoming fade waits for the outgoing one, so two pages are never legible at once.
      val fadeIn = fadeIn(tween(Motion.medium, delayMillis = Motion.short, easing = Motion.easeOut))
      val fadeOut = fadeOut(Motion.exit())
      val travel = if (forward) offsetPx else -offsetPx
      val slide = tween<IntOffset>(Motion.short + Motion.medium, easing = Motion.easeOut)
      when {
        reduceMotion -> fadeIn togetherWith fadeOut
        axis == MotionAxis.X ->
          (slideInHorizontally(slide) { travel } + fadeIn) togetherWith
            (slideOutHorizontally(slide) { -travel } + fadeOut)

        else ->
          (slideInVertically(slide) { travel } + fadeIn) togetherWith
            (slideOutVertically(slide) { -travel } + fadeOut)
      }
    }
  }
}

/** `animateItem` on the motion tokens. Under Reduce Motion rows fade but do not travel. */
@Composable
fun LazyItemScope.motionItem(): Modifier {
  val reduceMotion = rememberReduceMotion()
  return Modifier.animateItem(
    fadeInSpec = Motion.enter(),
    placementSpec = if (reduceMotion) null else Motion.enter(),
    fadeOutSpec = Motion.exit(),
  )
}

@Composable
fun rememberReduceMotion(): Boolean = remember { isReduceMotionEnabled() }

/** The OS "reduce motion" preference, where Compose does not already honour it. */
internal expect fun isReduceMotionEnabled(): Boolean
