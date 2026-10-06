package dev.fanfly.wingslog.core.ui.brand

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.PLATE_COUNT
import dev.fanfly.wingslog.core.ui.brand.ThingHeroTimeline.SHEET_COUNT
import dev.fanfly.wingslog.core.ui.brand.ThingHeroTimeline.SHEET_PLATES
import dev.fanfly.wingslog.core.ui.brand.ThingHeroTimeline.TOTAL_MS
import kotlin.math.abs
import org.junit.Test

class ThingHeroTimelineTest {

  @Test
  fun `the opening frame is empty`() {
    for (sheet in 0 until SHEET_COUNT) {
      assertThat(ThingHeroTimeline.sheet(sheet, 0).alpha).isEqualTo(0f)
    }
    for (plate in 0 until PLATE_COUNT) {
      assertThat(ThingHeroTimeline.plate(plate, 0, REST_PHASE).alpha).isEqualTo(0f)
    }
  }

  @Test
  fun `the resting state is the stack alone, every plate whole and in its seat`() {
    for (sheet in 0 until SHEET_COUNT) {
      assertThat(ThingHeroTimeline.sheet(sheet, TOTAL_MS).alpha).isEqualTo(0f)
    }
    for (plate in 0 until PLATE_COUNT) {
      val pose = ThingHeroTimeline.plate(plate, TOTAL_MS, REST_PHASE)
      assertThat(pose.alpha).isEqualTo(1f)
      assertThat(pose.thickness).isEqualTo(1f)
      assertThat(pose.lift).isWithin(TOLERANCE).of(0f)
    }
    assertThat(ThingHeroTimeline.idleWeight(TOTAL_MS)).isEqualTo(1f)
  }

  @Test
  fun `every plate gets a sheet, and sheets arrive bottom layer first`() {
    assertThat(SHEET_PLATES.toSet()).containsExactlyElementsIn(0 until PLATE_COUNT)
    for (sheet in 1 until SHEET_COUNT) {
      assertThat(SHEET_PLATES[sheet]).isAtLeast(SHEET_PLATES[sheet - 1])
      assertThat(ThingHeroTimeline.sheetStart(sheet))
        .isGreaterThan(ThingHeroTimeline.sheetStart(sheet - 1))
    }
  }

  @Test
  fun `a sheet starts well away from its seat and closes on it without overshooting`() {
    for (sheet in 0 until SHEET_COUNT) {
      val start = ThingHeroTimeline.sheetStart(sheet)
      val landed = ThingHeroTimeline.fuseStart(SHEET_PLATES[sheet])
      val first = ThingHeroTimeline.sheet(sheet, start + 1)
      var last = abs(first.x) + abs(first.y)
      assertThat(last).isGreaterThan(0.8f)
      for (ms in start + 1..landed) {
        val pose = ThingHeroTimeline.sheet(sheet, ms)
        val distance = abs(pose.x) + abs(pose.y)
        assertThat(distance).isAtMost(last + TOLERANCE)
        // Never past its seat: it stays on the side it came from.
        assertThat(pose.x * first.x).isAtLeast(0f)
        assertThat(pose.y).isAtMost(0f)
        last = distance
      }
      assertThat(last).isWithin(TOLERANCE).of(0f)
      assertThat(ThingHeroTimeline.sheet(sheet, landed).alpha).isEqualTo(1f)
    }
  }

  @Test
  fun `the two sheets of a layer come from opposite sides, the top one from overhead`() {
    val left = ThingHeroTimeline.sheet(0, ThingHeroTimeline.sheetStart(0) + 1)
    val right = ThingHeroTimeline.sheet(1, ThingHeroTimeline.sheetStart(1) + 1)
    assertThat(left.x).isLessThan(0f)
    assertThat(right.x).isGreaterThan(0f)
    val top = SHEET_COUNT - 1
    val overhead = ThingHeroTimeline.sheet(top, ThingHeroTimeline.sheetStart(top) + 1)
    assertThat(overhead.x).isEqualTo(0f)
    assertThat(overhead.y).isLessThan(-0.5f)
  }

  @Test
  fun `a plate appears under its sheets as they land, never before`() {
    for (plate in 0 until PLATE_COUNT) {
      val fuse = ThingHeroTimeline.fuseStart(plate)
      assertThat(ThingHeroTimeline.plate(plate, fuse - 1, REST_PHASE).alpha).isEqualTo(0f)
      val bare = ThingHeroTimeline.plate(plate, fuse, REST_PHASE)
      assertThat(bare.alpha).isEqualTo(1f)
      assertThat(bare.thickness).isEqualTo(0f)
      // The paper is still fully there at that instant, so the hand-over cannot be seen.
      for (sheet in 0 until SHEET_COUNT) {
        if (SHEET_PLATES[sheet] == plate) {
          assertThat(ThingHeroTimeline.sheet(sheet, fuse).alpha).isEqualTo(1f)
        }
      }
    }
  }

  @Test
  fun `fusing thickens the plate as the paper fades, and both finish together`() {
    for (plate in 0 until PLATE_COUNT) {
      val sheet = SHEET_PLATES.indexOf(plate)
      var thickness = 0f
      var paper = 1f
      for (ms in ThingHeroTimeline.fuseStart(plate)..ThingHeroTimeline.fuseEnd(plate)) {
        val now = ThingHeroTimeline.plate(plate, ms, REST_PHASE).thickness
        val left = ThingHeroTimeline.sheet(sheet, ms).alpha
        assertThat(now).isAtLeast(thickness)
        assertThat(left).isAtMost(paper)
        thickness = now
        paper = left
      }
      assertThat(thickness).isEqualTo(1f)
      assertThat(paper).isEqualTo(0f)
    }
  }

  @Test
  fun `a layer is finished before the next one's paper arrives`() {
    for (plate in 0 until PLATE_COUNT - 1) {
      assertThat(ThingHeroTimeline.fuseEnd(plate))
        .isAtMost(ThingHeroTimeline.fuseStart(plate + 1))
    }
  }

  @Test
  fun `the idle breath lifts the stack and parts its plates, by a few percent at most`() {
    val crest = (kotlin.math.PI / 2).toFloat()
    var below = 0f
    for (plate in 0 until PLATE_COUNT) {
      val lift = ThingHeroTimeline.plate(plate, TOTAL_MS, crest).lift
      assertThat(lift).isGreaterThan(below)
      assertThat(lift).isAtMost(0.05f)
      below = lift
    }
  }

  private companion object {
    const val TOLERANCE = 1e-5f

    // The bottom of the idle breath, which leaves a pose at its plain seat.
    val REST_PHASE = (3 * kotlin.math.PI / 2).toFloat()
  }
}
