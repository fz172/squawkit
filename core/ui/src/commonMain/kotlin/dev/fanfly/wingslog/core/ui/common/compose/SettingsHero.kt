package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlin.math.roundToInt

private val HaloSize = 120.dp
private val DiscSize = 88.dp
private val HeroIconSize = 44.dp
private val BobAmplitude = 3.dp
private const val InactiveAlpha = 0.55f
private const val HaloBreath = 0.06f
private const val HeroPeriodMillis = 2_400

/**
 * The slow breath the settings heroes share: 0 → 1 → 0 over [HeroPeriodMillis]. Held at the
 * midpoint when [active] is false, so an off setting reads as still without snapping.
 */
@Composable
fun rememberHeroPulse(active: Boolean = true): State<Float> {
  val transition = rememberInfiniteTransition(label = "settings-hero")
  return transition.animateFloat(
    initialValue = if (active) 0f else 0.5f,
    targetValue = if (active) 1f else 0.5f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = HeroPeriodMillis, easing = EaseInOutSine),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "settings-hero-pulse",
  )
}

/** Bobs the subject by ±[BobAmplitude] with [pulse]; read in the layout phase, so no recomposition. */
fun Modifier.heroBob(pulse: () -> Float): Modifier = offset {
  IntOffset(x = 0, y = ((pulse() - 0.5f) * 2f * BobAmplitude.toPx()).roundToInt())
}

/**
 * The head of a settings detail page: one subject in a breathing ringed disc, a title and a
 * sentence — the halo swells and the icon bobs, the way the sync and notification heroes always
 * have.
 *
 * @param active false when the setting the page governs is off; the subject dims and holds still.
 */
@Composable
fun SettingsHero(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  active: Boolean = true,
) {
  val cs = MaterialTheme.colorScheme
  val pulse = rememberHeroPulse(active)
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = Spacing.large),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.xLarge),
  ) {
    Box(
      modifier = Modifier
        .size(HaloSize)
        .graphicsLayer {
          val scale = 1f + HaloBreath * pulse.value
          scaleX = scale
          scaleY = scale
        }
        .clip(CircleShape)
        .background(cs.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(DiscSize)
          .clip(CircleShape)
          .background(if (active) cs.primaryContainer else cs.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier
            .size(HeroIconSize)
            .heroBob { pulse.value },
          tint = if (active) cs.primary else cs.onSurfaceVariant.copy(alpha = InactiveAlpha),
        )
      }
    }
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = cs.onSurface,
      )
      Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = cs.onSurfaceVariant,
      )
    }
  }
}
