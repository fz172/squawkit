package dev.fanfly.wingslog.core.ui.brand

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
 * 3. The crate's outline morphs into the plane body; the plane's details fade in over it.
 * 4. The five glyphs re-emerge smaller behind the plane and settle into a fan.
 * 5. Everything wobbles gently, forever.
 */
object ThingHeroTimeline {
  const val TOTAL_MS = 4600

  private const val CRATE_IN_START = 0
  private const val CRATE_IN_MS = 420
  private const val FLY_START = 380
  private const val FLY_STAGGER = 330
  private const val FLY_MS = 560
  const val MORPH_START = 2560
  private const val MORPH_MS = 900
  const val MORPH_END = MORPH_START + MORPH_MS
  const val FAN_START = MORPH_END
  private const val FAN_STAGGER = 90
  private const val FAN_MS = 620
  const val IDLE_START = FAN_START + 540

  const val GLYPH_COUNT = 5

  /** Where each glyph flies in from, as a direction; multiplied by [FLY_DISTANCE]. */
  private val FLY_FROM = listOf(
    -1.0f to -0.35f,
    1.0f to -0.15f,
    -0.9f to 0.55f,
    0.25f to -1.0f,
    0.9f to 0.6f,
  )
  private const val FLY_DISTANCE = 0.95f

  /** Where each glyph settles in the fan behind the plane. */
  private val FAN_AT = listOf(
    -0.36f to -0.20f,
    0.36f to -0.18f,
    -0.30f to 0.27f,
    0.06f to -0.39f,
    0.34f to 0.27f,
  )

  /** Sizes as fractions of the hero box. */
  const val PLANE_SIZE = 0.62f
  const val FLY_SIZE = 0.34f
  const val FAN_SIZE = 0.28f

  data class Frame(val x: Float, val y: Float, val scale: Float, val alpha: Float, val rotation: Float = 0f)

  /** The crate as a full vector: visible until the morph starts, then handed to the outline. */
  fun crate(ms: Int): Frame {
    val pop = ease(progress(ms, CRATE_IN_START, CRATE_IN_MS))
    val alpha = if (ms >= MORPH_START) 0f else pop
    return Frame(0f, 0f, 0.6f + 0.4f * pop, alpha)
  }

  /** 0 → crate outline, 1 → plane body outline. */
  fun morph(ms: Int): Float = ease(progress(ms, MORPH_START, MORPH_MS))

  /** The interpolated outline is drawn from the morph's start until the real plane takes over. */
  fun morphOutlineAlpha(ms: Int): Float {
    if (ms < MORPH_START) return 0f
    return 1f - progress(ms, MORPH_START + MORPH_MS - 140, 140)
  }

  fun planeAlpha(ms: Int): Float = progress(ms, MORPH_START + MORPH_MS - 140, 140)

  /** Tail pieces and speed dashes fade in over the second half of the morph. */
  fun detailsAlpha(ms: Int): Float =
    progress(ms, MORPH_START + MORPH_MS / 2, MORPH_MS / 2) * (1f - planeAlpha(ms))

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

  /** Glyph [index] re-emerging behind the plane into its fan slot. */
  fun fanned(index: Int, ms: Int): Frame {
    val p = ease(progress(ms, FAN_START + index * FAN_STAGGER, FAN_MS))
    if (p <= 0f) return HIDDEN
    val (fx, fy) = FAN_AT[index]
    return Frame(x = fx * p, y = fy * p, scale = 0.4f + 0.6f * p, alpha = 0.6f * p)
  }

  /** 0 before the idle wobble begins, 1 once it is fully in. */
  fun idleWeight(ms: Int): Float = progress(ms, IDLE_START, 600)

  /** Slow rotation in degrees for a fanned glyph; [phase] is the shared idle clock in radians. */
  fun fanWobbleRotation(index: Int, phase: Float): Float =
    4f * sin(phase + index * 1.3f).toFloat()

  fun fanWobbleY(index: Int, phase: Float): Float =
    0.012f * sin(phase * 0.8f + index * 0.9f).toFloat()

  /** The plane's bob, matching the pre-existing hero: about six points up and a degree of roll. */
  fun planeBobY(phase: Float): Float = -0.017f * (0.5f + 0.5f * sin(phase).toFloat())

  fun planeBobRotation(phase: Float): Float = -0.25f + 1.25f * sin(phase).toFloat()

  const val IDLE_PERIOD_MS = 3400
  const val TWO_PI = (2 * PI).toFloat()

  private val HIDDEN = Frame(0f, 0f, 0f, 0f)

  private fun progress(ms: Int, start: Int, duration: Int): Float =
    ((ms - start).toFloat() / duration).coerceIn(0f, 1f)

  private fun progress(p: Float, start: Float, duration: Float): Float =
    ((p - start) / duration).coerceIn(0f, 1f)

  /** Fast-out slow-in, as a smoothstep-like cubic. */
  private fun ease(t: Float): Float = t * t * (3f - 2f * t)
}
