package dev.fanfly.wingslog.core.ui.brand

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ui.brand.ThingHeroTimeline.GLYPH_COUNT
import dev.fanfly.wingslog.core.ui.brand.ThingHeroTimeline.TOTAL_MS
import org.junit.Test

class ThingHeroTimelineTest {

  @Test
  fun `the resting state is the plane alone`() {
    val ms = TOTAL_MS
    assertThat(ThingHeroTimeline.planeAlpha(ms)).isEqualTo(1f)
    assertThat(ThingHeroTimeline.crate(ms).alpha).isEqualTo(0f)
    assertThat(ThingHeroTimeline.morphOutlineAlpha(ms)).isEqualTo(0f)
    assertThat(ThingHeroTimeline.detailsAlpha(ms)).isEqualTo(0f)
    for (i in 0 until GLYPH_COUNT) {
      assertThat(ThingHeroTimeline.flying(i, ms).alpha).isEqualTo(0f)
      assertThat(ThingHeroTimeline.fanned(i, ms).alpha).isEqualTo(0f)
    }
    assertThat(ThingHeroTimeline.idleWeight(ms)).isEqualTo(1f)
  }

  @Test
  fun `the fan shows fully, holds, then drifts out and fades`() {
    for (i in 0 until GLYPH_COUNT) {
      val held = ThingHeroTimeline.fanned(i, ThingHeroTimeline.FAN_OUT_START)
      assertThat(held.alpha).isWithin(1e-5f)
        .of(0.6f)
      assertThat(held.scale).isEqualTo(1f)
      val leaving =
        ThingHeroTimeline.fanned(i, ThingHeroTimeline.FAN_OUT_START + 400)
      assertThat(leaving.alpha).isLessThan(held.alpha)
      // Further out along the same line, not back towards the plane.
      assertThat(kotlin.math.abs(leaving.x) + kotlin.math.abs(leaving.y))
        .isGreaterThan(kotlin.math.abs(held.x) + kotlin.math.abs(held.y))
      assertThat(
        ThingHeroTimeline.fanned(
          i,
          ThingHeroTimeline.FAN_END
        ).alpha
      ).isEqualTo(0f)
    }
  }

  @Test
  fun `the opening frame shows only the crate popping in`() {
    assertThat(ThingHeroTimeline.planeAlpha(0)).isEqualTo(0f)
    assertThat(ThingHeroTimeline.crate(0).alpha).isEqualTo(0f)
    assertThat(ThingHeroTimeline.crate(420).alpha).isEqualTo(1f)
    for (i in 0 until GLYPH_COUNT) {
      assertThat(ThingHeroTimeline.flying(i, 0).alpha).isEqualTo(0f)
      assertThat(ThingHeroTimeline.fanned(i, 0).alpha).isEqualTo(0f)
    }
  }

  @Test
  fun `glyphs fly in one after another and every one is gone before the morph starts`() {
    var lastStart = -1
    for (i in 0 until GLYPH_COUNT) {
      val start =
        (0..TOTAL_MS).first { ThingHeroTimeline.flying(i, it).alpha > 0f }
      assertThat(start).isGreaterThan(lastStart)
      lastStart = start
      // Starts far from the centre, ends at it.
      val first = ThingHeroTimeline.flying(i, start)
      assertThat(
        maxOf(
          kotlin.math.abs(first.x),
          kotlin.math.abs(first.y)
        )
      ).isGreaterThan(0.5f)
    }
    for (i in 0 until GLYPH_COUNT) {
      assertThat(
        ThingHeroTimeline.flying(
          i,
          ThingHeroTimeline.MORPH_START
        ).alpha
      ).isEqualTo(0f)
    }
  }

  @Test
  fun `the crate hands over to the outline, and the outline to the plane, without a gap`() {
    val morphStart = ThingHeroTimeline.MORPH_START
    // Just before the morph the crate vector is fully visible; at the morph the outline takes over.
    assertThat(ThingHeroTimeline.crate(morphStart - 1).alpha).isEqualTo(1f)
    assertThat(ThingHeroTimeline.crate(morphStart).alpha).isEqualTo(0f)
    assertThat(ThingHeroTimeline.morphOutlineAlpha(morphStart)).isEqualTo(1f)
    // Outline and plane cross-fade: their alphas always sum to one while the outline is shown.
    for (ms in morphStart..TOTAL_MS) {
      val sum =
        ThingHeroTimeline.morphOutlineAlpha(ms) + ThingHeroTimeline.planeAlpha(
          ms
        )
      assertThat(sum).isWithin(1e-5f)
        .of(1f)
    }
    assertThat(ThingHeroTimeline.morph(TOTAL_MS)).isEqualTo(1f)
  }

  @Test
  fun `the fan emerges only after the morph is done`() {
    for (i in 0 until GLYPH_COUNT) {
      assertThat(
        ThingHeroTimeline.fanned(
          i,
          ThingHeroTimeline.MORPH_END - 1
        ).alpha
      ).isEqualTo(0f)
    }
    assertThat(ThingHeroTimeline.morph(ThingHeroTimeline.MORPH_END)).isEqualTo(
      1f
    )
  }

  @Test
  fun `the plane bob stays within a degree and a half`() {
    for (phase in listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f)) {
      assertThat(kotlin.math.abs(ThingHeroTimeline.planeBobRotation(phase))).isAtMost(
        1.5f
      )
    }
  }
}
