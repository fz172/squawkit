package dev.fanfly.wingslog.core.ui.brand

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Thing glyphs as tintable [ImageVector]s, built from the generated [ThingGlyphPaths]. Each
 * vector's viewport is the glyph's own tight square, so `Icon(glyph, Modifier.size(n))` fills `n`
 * the way [BrandPlane] does.
 */
object ThingGlyphs {
  val Car: ImageVector = ThingGlyphPaths.CAR.toImageVector()
  val Bike: ImageVector = ThingGlyphPaths.BIKE.toImageVector()
  val Boat: ImageVector = ThingGlyphPaths.BOAT.toImageVector()
  val Home: ImageVector = ThingGlyphPaths.HOME.toImageVector()
  val Toolbox: ImageVector = ThingGlyphPaths.TOOLBOX.toImageVector()

  /** The open box the login hero pours the others into before it becomes the plane. */
  val Crate: ImageVector = ThingGlyphPaths.CRATE.toImageVector()

  /** The order the hero flies them in. */
  val heroSequence: List<ImageVector> = listOf(Bike, Car, Boat, Home, Toolbox)
}

private fun GlyphSpec.toImageVector(): ImageVector =
  ImageVector.Builder(
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = viewportSize,
    viewportHeight = viewportSize,
  ).apply {
    addGroup(translationX = -viewportX, translationY = -viewportY)
    for (piece in paths) {
      val nodes = PathParser().parsePathString(piece.data).toNodes()
      if (piece.strokeWidth == null) {
        addPath(pathData = nodes, fill = SolidColor(Color.Black))
      } else {
        addPath(
          pathData = nodes,
          stroke = SolidColor(Color.Black),
          strokeLineWidth = piece.strokeWidth,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        )
      }
    }
    clearGroup()
  }.build()
