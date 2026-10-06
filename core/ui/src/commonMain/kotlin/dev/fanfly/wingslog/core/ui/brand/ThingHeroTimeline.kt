package dev.fanfly.wingslog.core.ui.brand

import dev.fanfly.wingslog.core.ui.brand.ThingHeroTimeline.FLY_DISTANCE
import kotlin.math.PI
import kotlin.math.sin

/**
 * The login hero's choreography as pure functions of elapsed milliseconds, so the composable only
 * maps numbers to transforms and the sequence itself can be unit-tested.
 *
 * Distances are fractions of the hero's box size, measured from its centre. The sequence:
 *
 * 1. The crate pops in at the centre.
 * 2. Five Thing glyphs fly in from off-screen, one after another, shrinking into the crate.
 * 3. The crate's outline morphs into the bottom plate of the brand stack.
 * 4. The two plates above it fall into place, the honey one last.
 * 5. The five glyphs re-emerge smaller behind the stack, hold a beat, then drift out and fade.
 * 6. The stack, alone, breathes gently forever.
 */
object ThingHeroTimeline {
  const val GLYPH_COUNT = 5

  private const val CRATE_IN_START = 0
  private const val CRATE_IN_MS = 420
  private const val FLY_START = 380
  private const val FLY_STAGGER = 330
  private const val FLY_MS = 560
  const val MORPH_START = 2560
  private const val MORPH_MS = 900
  const val MORPH_END = MORPH_START + MORPH_MS

  /** The stack's upper plates start falling as the bottom one finishes forming. */
  const val PLATE_START = MORPH_END - 140
  private const val PLATE_STAGGER = 240
  private const val PLATE_MS = 460
  const val STACK_END =
    PLATE_START + (BrandStackGeometry.PLATE_COUNT - 2) * PLATE_STAGGER + PLATE_MS

  /** The fan opens as the last plate settles, so the finished stack is what it frames. */
  const val FAN_START = STACK_END - 160
  private const val FAN_STAGGER = 90
  private const val FAN_MS = 620
  private const val FAN_HOLD_MS = 900
  private const val FAN_OUT_MS = 700
  const val FAN_OUT_START =
    FAN_START + (GLYPH_COUNT - 1) * FAN_STAGGER + FAN_MS + FAN_HOLD_MS
  const val FAN_END =
    FAN_OUT_START + (GLYPH_COUNT - 1) * FAN_STAGGER / 2 + FAN_OUT_MS
  const val IDLE_START = STACK_END

  /** How far above its seat a plate starts, as a fraction of the stack's own square. */
  const val PLATE_DROP = 0.5f

  /** The idle breath: every plate rises by [IDLE_RISE], and each one up by [IDLE_SPREAD] more. */
  private const val IDLE_RISE = 0.02f
  private const val IDLE_SPREAD = 0.012f
  const val TOTAL_MS = FAN_END

  /** Where each glyph flies in from, as a direction; multiplied by [FLY_DISTANCE]. */
  private val FLY_FROM = listOf(
    -1.0f to -0.35f,
    1.0f to -0.15f,
    -0.9f to 0.55f,
    0.25f to -1.0f,
    0.9f to 0.6f,
  )
  private const val FLY_DISTANCE = 0.95f

  /** Where each glyph pauses in the fan behind the stack before drifting out along the same line. */
  private val FAN_AT = listOf(
    -0.36f to -0.20f,
    0.36f to -0.18f,
    -0.30f to 0.27f,
    0.06f to -0.39f,
    0.34f to 0.27f,
  )

  /** Sizes as fractions of the hero box. */
  const val MARK_SIZE = 0.62f
  const val FLY_SIZE = 0.34f
  const val FAN_SIZE = 0.28f

  data class Frame(
    val x: Float,
    val y: Float,
    val scale: Float,
    val alpha: Float,
    val rotation: Float = 0f
  )

  /** The crate as a full vector: visible until the morph starts, then handed to the outline. */
  fun crate(ms: Int): Frame {
    val pop = ease(progress(ms, CRATE_IN_START, CRATE_IN_MS))
    val alpha = if (ms >= MORPH_START) 0f else pop
    return Frame(0f, 0f, 0.6f + 0.4f * pop, alpha)
  }

  /** 0 → crate outline, 1 → the bottom plate's outline. */
  fun morph(ms: Int): Float = ease(progress(ms, MORPH_START, MORPH_MS))

  /** The interpolated outline is drawn from the morph's start until the real plate takes over. */
  fun morphOutlineAlpha(ms: Int): Float {
    if (ms < MORPH_START) return 0f
    return 1f - bottomPlateAlpha(ms)
  }

  /** The bottom plate, in its own colours, cross-fading in over the last of the morph. */
  fun bottomPlateAlpha(ms: Int): Float = progress(ms, PLATE_START, MORPH_END - PLATE_START)

  /** Glyph [index] on its way into the crate; alpha 0 before and after. */
  fun flying(index: Int, ms: Int): Frame {
    val start = FLY_START + index * FLY_STAGGER
    val p = progress(ms, start, FLY_MS)
    if (p <= 0f || p >= 1f) return HIDDEN
    val e = ease(p)
    val (dx, dy) = FLY_FROM[index]
    val remaining = 1f - e
    return Frame(
      x = dx * FLY_DISTANCE * remaining,
      y = dy * FLY_DISTANCE * remaining,
      scale = 1f - 0.75f * e,
      alpha = 1f - progress(p, 0.7f, 0.3f),
      rotation = -14f * remaining * dx,
    )
  }

  /**
   * Glyph [index] behind the stack: emerging into its fan slot, holding, then drifting further out
   * along the same line while fading. Hidden before and after, so the idle state is the stack alone.
   */
  fun fanned(index: Int, ms: Int): Frame {
    val p = ease(progress(ms, FAN_START + index * FAN_STAGGER, FAN_MS))
    if (p <= 0f) return HIDDEN
    val out =
      ease(progress(ms, FAN_OUT_START + index * FAN_STAGGER / 2, FAN_OUT_MS))
    if (out >= 1f) return HIDDEN
    val (fx, fy) = FAN_AT[index]
    val reach = p + 0.45f * out
    return Frame(
      x = fx * reach,
      y = fy * reach,
      scale = 0.4f + 0.6f * p,
      alpha = 0.6f * p * (1f - out)
    )
  }

  /**
   * Plate [index] of the brand stack, bottom first. The bottom plate is what the crate's outline
   * turns into, so it only cross-fades in over the end of the morph. Each plate above it falls from
   * [PLATE_DROP] up, fading in on the way and slowing into its seat without overshooting. Once the
   * stack is built, [phase] opens and closes it: see [idleLift].
   */
  fun plate(index: Int, ms: Int, phase: Float): PlatePose {
    val idle = idleLift(index, ms, phase)
    if (index == 0) return PlatePose(lift = idle, alpha = bottomPlateAlpha(ms))
    val p = progress(ms, PLATE_START + (index - 1) * PLATE_STAGGER, PLATE_MS)
    if (p <= 0f) return PlatePose(lift = PLATE_DROP, alpha = 0f)
    return PlatePose(
      lift = PLATE_DROP * (1f - easeOut(p)) + idle,
      alpha = progress(p, 0f, 0.4f),
    )
  }

  /** The stack breathing at rest: it floats up a little, and the plates part a little as it does. */
  fun idleLift(index: Int, ms: Int, phase: Float): Float =
    idleWeight(ms) * (IDLE_RISE + index * IDLE_SPREAD) * (0.5f + 0.5f * sin(phase))

  /** 0 before the idle breath begins, 1 once it is fully in. */
  fun idleWeight(ms: Int): Float = progress(ms, IDLE_START, 600)

  const val IDLE_PERIOD_MS = 3400
  const val TWO_PI = (2 * PI).toFloat()

  private val HIDDEN = Frame(0f, 0f, 0f, 0f)

  private fun progress(ms: Int, start: Int, duration: Int): Float =
    ((ms - start).toFloat() / duration).coerceIn(0f, 1f)

  private fun progress(p: Float, start: Float, duration: Float): Float =
    ((p - start) / duration).coerceIn(0f, 1f)

  /** Fast-out slow-in, as a smoothstep-like cubic. */
  private fun ease(t: Float): Float = t * t * (3f - 2f * t)

  /** Decelerating all the way in, for something that lands. */
  private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)
}
