package dev.fanfly.wingslog.core.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.CORNER_STROKE

/**
 * The brand hero for the sign-in surfaces: five Thing glyphs fly into a crate, the crate becomes
 * the bottom plate of the brand stack, the other two plates land on it, the glyphs show behind it
 * for a beat and drift away, and the stack breathes alone. See [ThingHeroTimeline] for the
 * choreography.
 *
 * [size] is the square the stack and its fan are laid out in; flying glyphs start outside it, so
 * a parent that clips (a card) will cut them at its edge, which reads as flying into the card.
 * With [animate] false the hero renders its resting state: the stack, breathing.
 *
 * [tint] colours the crate and the glyphs. The stack keeps its own colours ([BrandStack]).
 */
@Composable
fun ThingHero(
  size: Dp,
  tint: Color,
  fanTint: Color,
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
  val morph = remember {
    val crate = ThingGlyphPaths.CRATE
    OutlineMorph(
      from = OutlineMorph.sample(crate.paths.first().data) { p ->
        Offset(
          (p.x - crate.viewportX) / crate.viewportSize,
          (p.y - crate.viewportY) / crate.viewportSize,
        )
      },
      to = OutlineMorph.sample(BrandStackGeometry.silhouette(0)) { p ->
        with(BrandStackGeometry) {
          Offset((p.x - VIEWPORT_X) / VIEWPORT, (p.y - VIEWPORT_Y) / VIEWPORT)
        }
      },
    )
  }
  val morphPath = remember { Path() }
  val sizePx = with(LocalDensity.current) { size.toPx() }
  val markSize = size * ThingHeroTimeline.MARK_SIZE

  Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
    // Fan: the glyphs re-emerging behind the stack for a beat before they drift away.
    for (i in 0 until ThingHeroTimeline.GLYPH_COUNT) {
      Glyph(
        vector = ThingGlyphs.heroSequence[i],
        size = size * ThingHeroTimeline.FAN_SIZE,
        tint = fanTint,
        sizePx = sizePx,
        frame = { ThingHeroTimeline.fanned(i, clock.value.toInt()) },
      )
    }

    // The crate, as a real vector while glyphs are flying into it.
    Glyph(
      vector = ThingGlyphs.Crate,
      size = markSize,
      tint = tint,
      sizePx = sizePx,
      frame = { ThingHeroTimeline.crate(clock.value.toInt()) },
    )

    // The crate outline becoming the bottom plate. The plate's corners are rounded by a stroke
    // (see BrandStackGeometry), so the stroke grows in with the morph to land on the same shape.
    Canvas(
      modifier = Modifier
        .size(markSize)
        .graphicsLayer {
          alpha = ThingHeroTimeline.morphOutlineAlpha(clock.value.toInt())
        },
    ) {
      val t = ThingHeroTimeline.morph(clock.value.toInt())
      val outline = morph.pathAt(t, this.size.width, morphPath)
      val corner = t * this.size.width * CORNER_STROKE / BrandStackGeometry.VIEWPORT
      drawPath(outline, color = tint)
      if (corner > 0f) {
        drawPath(outline, color = tint, style = Stroke(width = corner, join = StrokeJoin.Round))
      }
    }

    // The stack: the bottom plate takes over from the outline, the others land on it, then all
    // three breathe.
    BrandStack(Modifier.size(markSize)) { plate ->
      ThingHeroTimeline.plate(plate, clock.value.toInt(), phase)
    }

    // Flying glyphs, above the crate so they visibly drop in.
    for (i in 0 until ThingHeroTimeline.GLYPH_COUNT) {
      Glyph(
        vector = ThingGlyphs.heroSequence[i],
        size = size * ThingHeroTimeline.FLY_SIZE,
        tint = tint,
        sizePx = sizePx,
        frame = { ThingHeroTimeline.flying(i, clock.value.toInt()) },
      )
    }
  }
}

/** One tinted vector whose transform is read from [frame] inside the draw phase, not recomposed. */
@Composable
private fun Glyph(
  vector: ImageVector,
  size: Dp,
  tint: Color,
  sizePx: Float,
  frame: () -> ThingHeroTimeline.Frame,
) {
  Icon(
    imageVector = vector,
    contentDescription = null,
    tint = tint,
    modifier = Modifier
      .size(size)
      .graphicsLayer {
        val f = frame()
        translationX = f.x * sizePx
        translationY = f.y * sizePx
        scaleX = f.scale
        scaleY = f.scale
        alpha = f.alpha
        rotationZ = f.rotation
      },
  )
}
