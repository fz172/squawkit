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
  fun `a sheet is a face with a sliver of edge, far thinner than a plate`() {
    assertThat(BrandStackGeometry.sheetEdge(1))
      .isEqualTo("M512.0,391.0L762.0,521.0L512.0,651.0L262.0,521.0Z")
    assertThat(BrandStackGeometry.SHEET_THICKNESS).isLessThan(THICKNESS / 3)
  }

  @Test
  fun `a print lands centred on its plate's face`() {
    for (plate in 0 until PLATE_COUNT) {
      val print = BrandStackGeometry.printOnFace(plate, 100f, 40f, 800f)
      assertThat(print.mapX(500f, 440f)).isWithin(TOLERANCE).of(CENTRE_X)
      assertThat(print.mapY(500f, 440f)).isWithin(TOLERANCE).of(BrandStackGeometry.faceY(plate))
    }
  }

  @Test
  fun `a print lies in the face's plane and stays inside it`() {
    val print = BrandStackGeometry.printOnFace(0, 100f, 40f, 800f)
    val y = BrandStackGeometry.faceY(0)
    // The drawing's corners: top-left heads for the face's top corner, bottom-right for its
    // bottom one, and the other two for its sides.
    assertThat(print.mapX(100f, 40f)).isWithin(TOLERANCE).of(CENTRE_X)
    assertThat(print.mapY(100f, 40f)).isLessThan(y)
    assertThat(print.mapX(900f, 840f)).isWithin(TOLERANCE).of(CENTRE_X)
    assertThat(print.mapY(900f, 840f)).isGreaterThan(y)
    assertThat(print.mapY(900f, 40f)).isWithin(TOLERANCE).of(y)
    assertThat(print.mapY(100f, 840f)).isWithin(TOLERANCE).of(y)
    // A face corner is a whole half-width or half-height out; the print stops short of it.
    assertThat(print.mapX(900f, 40f) - CENTRE_X).isLessThan(HALF_WIDTH * 0.75f)
    assertThat(y - print.mapY(100f, 40f)).isLessThan(HALF_HEIGHT * 0.75f)
  }

  private companion object {
    const val TOLERANCE = 1e-3f
  }
}
