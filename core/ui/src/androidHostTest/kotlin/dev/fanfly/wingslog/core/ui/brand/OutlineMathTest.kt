package dev.fanfly.wingslog.core.ui.brand

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutlineMathTest {

  // A unit square, clockwise from the top-left, one point per corner.
  private val square = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

  @Test
  fun `winding is detected from the signed area`() {
    val reversed = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)
    assertThat(OutlineMath.signedArea(square) * OutlineMath.signedArea(reversed)).isLessThan(0f)
    assertThat(OutlineMath.matchWinding(reversed, square)).usingTolerance(0.0).containsExactly(*square.toTypedArray()).inOrder()
    assertThat(OutlineMath.matchWinding(square, square)).isSameInstanceAs(square)
  }

  @Test
  fun `rotation shifts the start point cyclically`() {
    val shifted = OutlineMath.rotate(square, 1)
    assertThat(shifted).usingTolerance(0.0).containsExactly(1f, 0f, 1f, 1f, 0f, 1f, 0f, 0f).inOrder()
  }

  @Test
  fun `align picks the rotation whose points sit closest to the target`() {
    // The same square, starting two corners later: alignment must undo that shift exactly.
    val startedElsewhere = OutlineMath.rotate(square, 2)
    assertThat(OutlineMath.align(square, startedElsewhere))
      .usingTolerance(0.0).containsExactly(*square.toTypedArray()).inOrder()
  }

  @Test
  fun `align rewinds and rotates together`() {
    val reversedAndShifted = OutlineMath.rotate(floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f), 3)
    assertThat(OutlineMath.align(square, reversedAndShifted))
      .usingTolerance(0.0).containsExactly(*square.toTypedArray()).inOrder()
  }

  @Test
  fun `lerp hits both ends exactly`() {
    val other = floatArrayOf(0.5f, 0.5f, 1.5f, 0.5f, 1.5f, 1.5f, 0.5f, 1.5f)
    val out = FloatArray(8)
    OutlineMath.lerp(square, other, 0f, out)
    assertThat(out).usingTolerance(0.0).containsExactly(*square.toTypedArray()).inOrder()
    OutlineMath.lerp(square, other, 1f, out)
    assertThat(out).usingTolerance(0.0).containsExactly(*other.toTypedArray()).inOrder()
    OutlineMath.lerp(square, other, 0.5f, out)
    assertThat(out[0]).isWithin(1e-6f).of(0.25f)
  }

  @Test
  fun `first contour stops before a hole`() {
    assertThat(OutlineMorph.firstContour("M1,1L2,2ZM3,3L4,4Z")).isEqualTo("M1,1L2,2Z")
    assertThat(OutlineMorph.firstContour("M1,1L2,2Z")).isEqualTo("M1,1L2,2Z")
  }
}
