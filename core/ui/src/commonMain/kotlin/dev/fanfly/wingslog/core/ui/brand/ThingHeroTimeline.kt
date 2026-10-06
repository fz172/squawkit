package dev.fanfly.wingslog.core.ui.brand

import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.PLATE_COUNT
import kotlin.math.PI
import kotlin.math.sin

/**
 * The login hero's choreography as pure functions of elapsed milliseconds, so the composable only
 * maps numbers to poses and the sequence itself can be unit-tested.
 *
 * Distances are fractions of the brand mark's own square. The stack is built one layer at a time,
 * bottom first, out of sheets of paper with a Thing printed on each:
 *
 * 1. Two sheets slide in from either side, the second just behind the first, and land together.
 * 2. They fuse: the paper fades, and the plate it was lying on shows through and swells to its
 *    full thickness.
 * 3. The same again for the middle plate.
 * 4. One last sheet comes down from above and becomes the honey plate on top.
 * 5. The stack, alone, breathes gently forever.
 */
object ThingHeroTimeline {
  /** Which plate each sheet settles on, bottom first; the hero prints one Thing on each. */
  val SHEET_PLATES: List<Int> = listOf(0, 0, 1, 1, 2)
  val SHEET_COUNT = SHEET_PLATES.size

  private const val LAYER_START = 200
  private const val LAYER_STAGGER = 900

  /** The second sheet of a layer trails the first, so the two read as separate pages. */
  private const val PAIR_DELAY = 140
  private const val FLY_MS = 700
  private const val FUSE_MS = 380
  private const val IDLE_IN_MS = 600

  /** The brand mark's side as a fraction of the hero's box; the rest is room for sheets to fly. */
  const val MARK_SIZE = 0.62f

  /** How far out a sheet starts: from the left or the right and a little above, or from overhead. */
  private const val SIDE_REACH = 0.95f
  private const val SIDE_RISE = 0.3f
  private const val OVERHEAD_REACH = 0.9f

  /** The idle breath: every plate rises by [IDLE_RISE], and each one up by [IDLE_SPREAD] more. */
  private const val IDLE_RISE = 0.02f
  private const val IDLE_SPREAD = 0.012f

  const val IDLE_PERIOD_MS = 3400
  const val TWO_PI = (2 * PI).toFloat()

  /** When sheet [index] sets off. */
  fun sheetStart(index: Int): Int =
    LAYER_START + SHEET_PLATES[index] * LAYER_STAGGER + rankInLayer(index) * PAIR_DELAY

  /** When [plate]'s last sheet has landed and the layer starts turning into the plate. */
  fun fuseStart(plate: Int): Int = sheetStart(SHEET_PLATES.lastIndexOf(plate)) + FLY_MS

  /** When [plate] is finished. */
  fun fuseEnd(plate: Int): Int = fuseStart(plate) + FUSE_MS

  val STACK_END = fuseEnd(PLATE_COUNT - 1)
  val TOTAL_MS = STACK_END + IDLE_IN_MS

  /**
   * Sheet [index] on its way to its seat: it fades in as it sets off, slows all the way in, lies
   * still until its layer is complete, then fades as the plate takes over. Hidden before and after.
   */
  fun sheet(index: Int, ms: Int): SheetPose {
    val p = progress(ms, sheetStart(index), FLY_MS)
    if (p <= 0f) return SheetPose.Hidden
    val fade = 1f - progress(ms, fuseStart(SHEET_PLATES[index]), FUSE_MS)
    if (fade <= 0f) return SheetPose.Hidden
    val remaining = 1f - easeOut(p)
    return SheetPose(
      x = fromX(index) * remaining,
      y = fromY(index) * remaining,
      alpha = progress(p, 0f, 0.3f) * fade,
    )
  }

  /**
   * Plate [index] of the brand stack, bottom first. It appears under its sheets the moment the last
   * one lands, as a bare face, and swells to full thickness while the paper fades off it. Once the
   * stack is built, [phase] opens and closes it: see [idleLift].
   */
  fun plate(index: Int, ms: Int, phase: Float): PlatePose {
    if (ms < fuseStart(index)) return PlatePose(alpha = 0f, thickness = 0f)
    return PlatePose(
      lift = idleLift(index, ms, phase),
      alpha = 1f,
      thickness = ease(progress(ms, fuseStart(index), FUSE_MS)),
    )
  }

  /** The stack breathing at rest: it floats up a little, and the plates part a little as it does. */
  fun idleLift(index: Int, ms: Int, phase: Float): Float =
    idleWeight(ms) * (IDLE_RISE + index * IDLE_SPREAD) * (0.5f + 0.5f * sin(phase))

  /** 0 before the idle breath begins, 1 once it is fully in. */
  fun idleWeight(ms: Int): Float = progress(ms, STACK_END, IDLE_IN_MS)

  /** A sheet's place among those bound for the same plate: 0 for the first, 1 for the second. */
  private fun rankInLayer(index: Int): Int = index - SHEET_PLATES.indexOf(SHEET_PLATES[index])

  /** A layer's only sheet comes from overhead; of a pair, the first from the left, then the right. */
  private fun isAlone(index: Int): Boolean =
    SHEET_PLATES.indexOf(SHEET_PLATES[index]) == SHEET_PLATES.lastIndexOf(SHEET_PLATES[index])

  private fun fromX(index: Int): Float = when {
    isAlone(index) -> 0f
    rankInLayer(index) == 0 -> -SIDE_REACH
    else -> SIDE_REACH
  }

  private fun fromY(index: Int): Float = if (isAlone(index)) -OVERHEAD_REACH else -SIDE_RISE

  private fun progress(ms: Int, start: Int, duration: Int): Float =
    ((ms - start).toFloat() / duration).coerceIn(0f, 1f)

  private fun progress(p: Float, start: Float, duration: Float): Float =
    ((p - start) / duration).coerceIn(0f, 1f)

  /** Fast-out slow-in, as a smoothstep-like cubic. */
  private fun ease(t: Float): Float = t * t * (3f - 2f * t)

  /** Decelerating all the way in, for something that lands. */
  private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)
}
