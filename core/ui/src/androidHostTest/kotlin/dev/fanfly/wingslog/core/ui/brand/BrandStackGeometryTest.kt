package dev.fanfly.wingslog.core.ui.brand

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.CENTRE_X
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.CORNER_STROKE
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.HALF_HEIGHT
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.HALF_WIDTH
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.PLATE_COUNT
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.THICKNESS
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.VIEWPORT
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.VIEWPORT_X
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.VIEWPORT_Y
import org.junit.Test

class BrandStackGeometryTest {

  private val top = PLATE_COUNT - 1

  @Test
  fun `plates are indexed bottom first`() {
    for (plate in 1 until PLATE_COUNT) {
      assertThat(BrandStackGeometry.faceY(plate)).isLessThan(BrandStackGeometry.faceY(plate - 1))
    }
  }

  @Test
  fun `each plate overlaps the one below it, so the stack reads as one object`() {
    for (plate in 1 until PLATE_COUNT) {
      val pitch = BrandStackGeometry.faceY(plate - 1) - BrandStackGeometry.faceY(plate)
      assertThat(pitch).isGreaterThan(THICKNESS)
      assertThat(pitch).isLessThan(2 * HALF_HEIGHT)
    }
  }

  @Test
  fun `the viewport frames the artwork tightly, corner strokes included`() {
    val round = CORNER_STROKE / 2
    val highest = BrandStackGeometry.faceY(top) - HALF_HEIGHT - round
    val lowest = BrandStackGeometry.faceY(0) + THICKNESS + HALF_HEIGHT + round
    assertThat(highest).isEqualTo(VIEWPORT_Y)
    assertThat(lowest).isEqualTo(VIEWPORT_Y + VIEWPORT)
    assertThat(CENTRE_X - HALF_WIDTH - round).isAtLeast(VIEWPORT_X)
    assertThat(CENTRE_X + HALF_WIDTH + round).isAtMost(VIEWPORT_X + VIEWPORT)
    // Centred, so the mark sits in the middle of whatever box it is given.
    assertThat(VIEWPORT_X + VIEWPORT / 2).isEqualTo(CENTRE_X)
  }

  @Test
  fun `the edge is the face moved down by the plate's thickness`() {
    assertThat(BrandStackGeometry.face(0))
      .isEqualTo("M512.0,496.0L762.0,626.0L512.0,756.0L262.0,626.0Z")
    assertThat(BrandStackGeometry.edge(0))
      .isEqualTo("M512.0,534.0L762.0,664.0L512.0,794.0L262.0,664.0Z")
  }

  @Test
  fun `the silhouette is one closed contour around face and edge`() {
    val silhouette = BrandStackGeometry.silhouette(0)
    assertThat(silhouette).isEqualTo(
      "M512.0,496.0L762.0,626.0L762.0,664.0L512.0,794.0L262.0,664.0L262.0,626.0Z"
    )
    // One contour: the morph samples only up to a second M.
    assertThat(OutlineMorph.firstContour(silhouette)).isEqualTo(silhouette)
  }
}
