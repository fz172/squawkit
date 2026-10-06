package dev.fanfly.wingslog.core.ui.brand

/**
 * How one plate of the brand stack is placed for a frame. [lift] raises it, as a fraction of the
 * mark's own square, and [alpha] fades it; the resting stack is every plate at [Rest].
 */
data class PlatePose(val lift: Float = 0f, val alpha: Float = 1f) {
  companion object {
    val Rest = PlatePose()
  }
}

/**
 * The SquawkIt brand mark as numbers: three record plates stacked in perspective, authored in the
 * app icon's 1024 box (`docs/branding/app-icon-record-stack.svg` is the master artwork).
 *
 * Each plate is a diamond [face] with a second diamond [THICKNESS] below it for its [edge]; both
 * are filled and then stroked [CORNER_STROKE] wide with round joins, which is what rounds the
 * corners. Plates are indexed bottom first. Free of Compose types so it is unit-testable;
 * [BrandStack] does the drawing.
 */
object BrandStackGeometry {
  const val PLATE_COUNT = 3

  /** The square that frames the artwork tightly, corner strokes included. */
  const val VIEWPORT = 570f
  const val VIEWPORT_X = 227f
  const val VIEWPORT_Y = 246f

  const val CENTRE_X = 512f
  const val HALF_WIDTH = 250f
  const val HALF_HEIGHT = 130f
  const val THICKNESS = 38f
  const val CORNER_STROKE = 44f

  private const val BOTTOM_FACE_Y = 626f
  private const val PLATE_PITCH = 114f

  /** The height of the centre of [plate]'s top face. */
  fun faceY(plate: Int): Float = BOTTOM_FACE_Y - plate * PLATE_PITCH

  /** The top face of [plate], as path data. */
  fun face(plate: Int): String = diamond(faceY(plate))

  /** The underside of [plate]; filled in the edge colour beneath the face, it reads as thickness. */
  fun edge(plate: Int): String = diamond(faceY(plate) + THICKNESS)

  /**
   * The outline of the whole of [plate], face and edge together, as one closed contour. This is
   * what the login hero morphs the crate into.
   */
  fun silhouette(plate: Int): String {
    val y = faceY(plate)
    val left = CENTRE_X - HALF_WIDTH
    val right = CENTRE_X + HALF_WIDTH
    return "M$CENTRE_X,${y - HALF_HEIGHT}L$right,${y}L$right,${y + THICKNESS}" +
      "L$CENTRE_X,${y + THICKNESS + HALF_HEIGHT}L$left,${y + THICKNESS}L$left,${y}Z"
  }

  private fun diamond(y: Float): String =
    "M$CENTRE_X,${y - HALF_HEIGHT}L${CENTRE_X + HALF_WIDTH},${y}" +
      "L$CENTRE_X,${y + HALF_HEIGHT}L${CENTRE_X - HALF_WIDTH},${y}Z"
}
