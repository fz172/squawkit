package dev.fanfly.wingslog.core.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** The Things printed on the hero's sheets, in the order the sheets arrive. */
private val HERO_THINGS = listOf(
  ThingGlyphPaths.BIKE,
  ThingGlyphPaths.CAR,
  ThingGlyphPaths.BOAT,
  ThingGlyphPaths.HOME,
  ThingGlyphPaths.TOOLBOX,
)

/**
 * The brand hero for the sign-in surfaces: sheets of paper, a Thing printed on each, come in layer
 * by layer and become the plates of the brand stack, which then breathes alone. See
 * [ThingHeroTimeline] for the choreography.
 *
 * [size] is the square the hero is laid out in; the stack sits in the middle of it and sheets start
 * outside it, so a parent that clips (a card) will cut them at its edge, which reads as flying
 * into the card. With [animate] false the hero renders its resting state: the stack, breathing.
 *
 * The stack and the paper keep their own colours ([BrandStack], [BrandSheets]) on every theme.
 */
@Composable
fun ThingHero(
  size: Dp,
  modifier: Modifier = Modifier,
  animate: Boolean = true,
) {
  val clock =
    remember { Animatable(if (animate) 0f else ThingHeroTimeline.TOTAL_MS.toFloat()) }
  LaunchedEffect(animate) {
    if (animate && clock.value < ThingHeroTimeline.TOTAL_MS) {
      clock.animateTo(
        ThingHeroTimeline.TOTAL_MS.toFloat(),
        tween(
          ThingHeroTimeline.TOTAL_MS - clock.value.toInt(),
          easing = LinearEasing
        ),
      )
    }
  }
  val idle = rememberInfiniteTransition(label = "heroIdle")
  val phase by idle.animateFloat(
    initialValue = 0f,
    targetValue = ThingHeroTimeline.TWO_PI,
    animationSpec = infiniteRepeatable(
      animation = tween(
        ThingHeroTimeline.IDLE_PERIOD_MS,
        easing = LinearEasing
      ),
      repeatMode = RepeatMode.Restart,
    ),
    label = "heroPhase",
  )
  val sheets = remember {
    HERO_THINGS.mapIndexed { index, thing ->
      BrandSheet(plate = ThingHeroTimeline.SHEET_PLATES[index], glyph = thing)
    }
  }
  val markSize = size * ThingHeroTimeline.MARK_SIZE

  Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
    // The plates, each appearing under its sheets once they have landed.
    BrandStack(Modifier.size(markSize)) { plate ->
      ThingHeroTimeline.plate(plate, clock.value.toInt(), phase)
    }

    // The sheets, always above the plates: a layer's paper is gone before the next one arrives.
    BrandSheets(sheets, Modifier.size(markSize)) { sheet ->
      ThingHeroTimeline.sheet(sheet, clock.value.toInt())
    }
  }
}
