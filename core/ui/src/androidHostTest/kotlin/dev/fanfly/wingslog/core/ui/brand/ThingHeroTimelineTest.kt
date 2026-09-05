package dev.fanfly.wingslog.core.ui.brand

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ui.brand.ThingHeroTimeline.GLYPH_COUNT
import dev.fanfly.wingslog.core.ui.brand.ThingHeroTimeline.TOTAL_MS
import org.junit.Test

class ThingHeroTimelineTest {

  @Test
  fun `the resting state is the plane with its fan and nothing else`() {
    val ms = TOTAL_MS
    assertThat(ThingHeroTimeline.planeAlpha(ms)).isEqualTo(1f)
    assertThat(ThingHeroTimeline.crate(ms).alpha).isEqualTo(0f)
    assertThat(ThingHeroTimeline.morphOutlineAlpha(ms)).isEqualTo(0f)
    assertThat(ThingHeroTimeline.detailsAlpha(ms)).isEqualTo(0f)
    for (i in 0 until GLYPH_COUNT) {
      assertThat(ThingHeroTimeline.flying(i, ms).alpha).isEqualTo(0f)
      val fan = ThingHeroTimeline.fanned(i, ms)
      assertThat(fan.alpha).isGreaterThan(0f)
      assertThat(fan.scale).isEqualTo(1f)
    }
    assertThat(ThingHeroTimeline.idleWeight(ms)).isEqualTo(1f)
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
      val start = (0..TOTAL_MS).first { ThingHeroTimeline.flying(i, it).alpha > 0f }
      assertThat(start).isGreaterThan(lastStart)
      lastStart = start
      // Starts far from the centre, ends at it.
      val first = ThingHeroTimeline.flying(i, start)
      assertThat(maxOf(kotlin.math.abs(first.x), kotlin.math.abs(first.y))).isGreaterThan(0.5f)
    }
    for (i in 0 until GLYPH_COUNT) {
      assertThat(ThingHeroTimeline.flying(i, ThingHeroTimeline.MORPH_START).alpha).isEqualTo(0f)
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
      val sum = ThingHeroTimeline.morphOutlineAlpha(ms) + ThingHeroTimeline.planeAlpha(ms)
      assertThat(sum).isWithin(1e-5f).of(1f)
    }
    assertThat(ThingHeroTimeline.morph(TOTAL_MS)).isEqualTo(1f)
  }

  @Test
  fun `the fan emerges only after the morph is done`() {
    for (i in 0 until GLYPH_COUNT) {
      assertThat(ThingHeroTimeline.fanned(i, ThingHeroTimeline.MORPH_END - 1).alpha).isEqualTo(0f)
    }
    assertThat(ThingHeroTimeline.morph(ThingHeroTimeline.MORPH_END)).isEqualTo(1f)
  }

  @Test
  fun `wobble is bounded and phase-offset per glyph`() {
    val a = ThingHeroTimeline.fanWobbleRotation(0, 0f)
    val b = ThingHeroTimeline.fanWobbleRotation(1, 0f)
    assertThat(a).isNotEqualTo(b)
    for (phase in listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f)) {
      assertThat(kotlin.math.abs(ThingHeroTimeline.planeBobRotation(phase))).isAtMost(1.5f)
      assertThat(kotlin.math.abs(ThingHeroTimeline.fanWobbleRotation(2, phase))).isAtMost(4f)
    }
  }
}
