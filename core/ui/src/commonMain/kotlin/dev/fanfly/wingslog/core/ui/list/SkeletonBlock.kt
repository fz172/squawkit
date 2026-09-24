package dev.fanfly.wingslog.core.ui.list

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.fanfly.wingslog.core.ui.theme.Motion
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberReduceMotion
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.loading

/**
 * What makes a group of [SkeletonBlock]s one placeholder: it breathes together, holds still under
 * Reduce Motion, and is a single "Loading" node to a screen reader.
 */
@Composable
fun Modifier.skeletonPulse(): Modifier {
  val loading = stringResource(Res.string.loading)
  val pulse = if (rememberReduceMotion()) {
    1f
  } else {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
      initialValue = 1f,
      targetValue = PulseFloor,
      animationSpec = infiniteRepeatable(
        animation = tween(PulseMillis, easing = Motion.easeOut),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "skeleton_pulse",
    )
    alpha
  }
  return clearAndSetSemantics { contentDescription = loading }.alpha(pulse)
}

/** One placeholder bar. Public so a screen that is not a list can compose its own shape. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.background(
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = RoundedCornerShape(Spacing.smallCornerRadius),
    ),
  )
}
