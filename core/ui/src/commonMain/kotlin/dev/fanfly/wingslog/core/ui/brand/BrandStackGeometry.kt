package dev.fanfly.wingslog.core.ui.brand

/**
 * How one plate of the brand stack is placed for a frame. [lift] raises it, as a fraction of the
 * mark's own square, [alpha] fades it, and [thickness] runs from 0, a face with no edge under it,
 * to 1, the full plate. The resting stack is every plate at [Rest].
 */
data class PlatePose(val lift: Float = 0f, val alpha: Float = 1f, val thickness: Float = 1f) {
  companion object {
    val Rest = PlatePose()
  }
}

/**
 * How one sheet of paper is placed for a frame: [x] and [y] are how far it is from its seat on the
 * plate it settles on, as fractions of the mark's own square, and [alpha] fades it.
 */
data class SheetPose(val x: Float, val y: Float, val alpha: Float) {
  companion object {
    val Hidden = SheetPose(0f, 0f, 0f)
  }
}

/**
 * Lays a flat square drawing down on a plate's face: `x' = a·x + c·y + tx`, `y' = b·x + d·y + ty`.
 * The drawing's x axis runs down the face's right-hand edge and its y axis down the left-hand one,
 * which is how something printed on a sheet lying in this perspective looks.
 */
data class FaceTransform(
  val a: Float,
  val b: Float,
  val c: Float,
  val d: Float,
  val tx: Float,
  val ty: Float,
) {
  fun mapX(x: Float, y: Float): Float = a * x + c * y + tx
  fun mapY(x: Float, y: Float): Float = b * x + d * y + ty
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

  /** A sheet of paper is a plate's face with this much edge under it. */
  const val SHEET_THICKNESS = 9f

  /** How much of a face a drawing printed on it spans, corner to corner. */
  const val PRINT_SPAN = 0.6f

  private const val BOTTOM_FACE_Y = 626f
  private const val PLATE_PITCH = 114f

  /** The height of the centre of [plate]'s top face. */
  fun faceY(plate: Int): Float = BOTTOM_FACE_Y - plate * PLATE_PITCH

  /** The top face of [plate], as path data. */
  fun face(plate: Int): String = diamond(faceY(plate))

  /** The underside of [plate]; filled in the edge colour beneath the face, it reads as thickness. */
  fun edge(plate: Int): String = diamond(faceY(plate) + THICKNESS)

  /** The underside of a sheet of paper lying on [plate]'s face. */
  fun sheetEdge(plate: Int): String = diamond(faceY(plate) + SHEET_THICKNESS)

  /**
   * The transform that prints a drawing on [plate]'s face, centred. The drawing is the square at
   * ([viewportX], [viewportY]) with side [viewportSize], as a [GlyphSpec] describes its artwork.
   */
  fun printOnFace(
    plate: Int,
    viewportX: Float,
    viewportY: Float,
    viewportSize: Float,
  ): FaceTransform {
    // Half the face's right-hand and left-hand edges, per unit of the drawing.
    val k = PRINT_SPAN / viewportSize
    val a = HALF_WIDTH * k
    val b = HALF_HEIGHT * k
    val c = -HALF_WIDTH * k
    val d = HALF_HEIGHT * k
    val centreX = viewportX + viewportSize / 2
    val centreY = viewportY + viewportSize / 2
    return FaceTransform(
      a = a,
      b = b,
      c = c,
      d = d,
      tx = CENTRE_X - a * centreX - c * centreY,
      ty = faceY(plate) - b * centreX - d * centreY,
    )
  }

  private fun diamond(y: Float): String =
    "M$CENTRE_X,${y - HALF_HEIGHT}L${CENTRE_X + HALF_WIDTH},${y}" +
      "L$CENTRE_X,${y + HALF_HEIGHT}L${CENTRE_X - HALF_WIDTH},${y}Z"
}
