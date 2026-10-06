package dev.fanfly.wingslog.core.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.CENTRE_X
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.CORNER_STROKE
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.HALF_HEIGHT
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.HALF_WIDTH
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.SHEET_THICKNESS
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.VIEWPORT
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.VIEWPORT_X
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.VIEWPORT_Y

/** One sheet of paper: the [plate] of the brand stack it settles on and the [glyph] printed on it. */
data class BrandSheet(val plate: Int, val glyph: GlyphSpec)

/** Paper is the sky tone the app sets behind primary content, so it reads on both themes. */
private val PAPER = SolidColor(Color(0xFFD5E3FF))
private val PAPER_EDGE = SolidColor(Color(0xFF9DBDF2))
private val INK = SolidColor(Color(0xFF1A5FAE))

/**
 * Sheets of paper in the brand stack's perspective, each with a Thing printed on it. They share
 * [BrandStack]'s square, so a sheet at rest lies exactly on its plate's face; lay this over a
 * [BrandStack] of the same size. [pose] is read in the draw phase, once per sheet, and a sheet may
 * be posed outside the square: nothing here clips it.
 */
@Composable
fun BrandSheets(
  sheets: List<BrandSheet>,
  modifier: Modifier = Modifier,
  pose: (sheet: Int) -> SheetPose,
) {
  val art = remember(sheets) { sheets.map(::SheetArt) }
  Canvas(modifier) {
    val scale = size.minDimension / VIEWPORT
    withTransform({
      scale(scale, scale, pivot = Offset.Zero)
      translate(-VIEWPORT_X, -VIEWPORT_Y)
    }) {
      art.forEachIndexed { index, sheet ->
        val own = pose(index)
        if (own.alpha > 0f) {
          translate(left = own.x * VIEWPORT, top = own.y * VIEWPORT) {
            with(sheet) { draw(own.alpha) }
          }
        }
      }
    }
  }
}

/** A piece of a glyph, ready to draw: stroked [stroke] wide when it is a line, filled otherwise. */
private class PrintPiece(val path: Path, val stroke: Stroke?)

/** One sheet's paths, built once in the icon's 1024 space. */
private class SheetArt(sheet: BrandSheet) {
  private val face = BrandStackGeometry.face(sheet.plate).toPath()
  private val edge = BrandStackGeometry.sheetEdge(sheet.plate).toPath()
  private val corners = Stroke(width = CORNER_STROKE, join = StrokeJoin.Round)
  private val bounds = BrandStackGeometry.faceY(sheet.plate).let { y ->
    val round = CORNER_STROKE / 2
    Rect(
      left = CENTRE_X - HALF_WIDTH - round,
      top = y - HALF_HEIGHT - round,
      right = CENTRE_X + HALF_WIDTH + round,
      bottom = y + HALF_HEIGHT + SHEET_THICKNESS + round,
    )
  }
  private val fade = Paint()
  private val print = sheet.glyph.paths.map { piece ->
    PrintPiece(
      path = piece.data.toPath(),
      stroke = piece.strokeWidth?.let {
        Stroke(width = it, cap = StrokeCap.Round, join = StrokeJoin.Round)
      },
    )
  }
  private val onFace = with(sheet.glyph) {
    BrandStackGeometry.printOnFace(sheet.plate, viewportX, viewportY, viewportSize).toMatrix()
  }

  /**
   * A fading sheet is drawn whole into a layer and the layer faded, because its pieces overlap:
   * fading each one separately would show the edge through the face and the fill through the stroke.
   */
  fun DrawScope.draw(alpha: Float) {
    if (alpha >= 1f) return drawOpaque()
    fade.alpha = alpha
    drawContext.canvas.saveLayer(bounds, fade)
    drawOpaque()
    drawContext.canvas.restore()
  }

  private fun DrawScope.drawOpaque() {
    rounded(edge, PAPER_EDGE)
    rounded(face, PAPER)
    withTransform({ transform(onFace) }) {
      for (piece in print) {
        if (piece.stroke == null) {
          drawPath(piece.path, INK)
        } else {
          drawPath(piece.path, INK, style = piece.stroke)
        }
      }
    }
  }

  private fun DrawScope.rounded(path: Path, brush: Brush) {
    drawPath(path, brush)
    drawPath(path, brush, style = corners)
  }
}

private fun FaceTransform.toMatrix(): Matrix =
  Matrix().also {
    it[0, 0] = a
    it[0, 1] = b
    it[1, 0] = c
    it[1, 1] = d
    it[3, 0] = tx
    it[3, 1] = ty
  }

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()
