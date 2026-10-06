package dev.fanfly.wingslog.core.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.CENTRE_X
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.CORNER_STROKE
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.HALF_HEIGHT
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.HALF_WIDTH
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.PLATE_COUNT
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.VIEWPORT
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.VIEWPORT_X
import dev.fanfly.wingslog.core.ui.brand.BrandStackGeometry.VIEWPORT_Y

/** One plate's colours: the face runs [faceStart] to [faceEnd] across it, the edge is flat. */
private data class PlateColors(val faceStart: Color, val faceEnd: Color, val edge: Color)

/**
 * The mark's own colours, bottom plate first: two instrument blues under a honey top. They are
 * artwork, like the launcher icon's, so they do not follow the theme.
 */
private val PLATE_COLORS = listOf(
  PlateColors(Color(0xFF1F68BC), Color(0xFF124F97), Color(0xFF0A3870)),
  PlateColors(Color(0xFF66A8F2), Color(0xFF3F8ADF), Color(0xFF1F68BC)),
  PlateColors(Color(0xFFF4D48F), Color(0xFFE4A94B), Color(0xFFB27A24)),
)

/** The shadow a plate drops on the one below: how dark, how far down, and the gap it fades over. */
private const val SHADOW_ALPHA = 0.2f
private const val SHADOW_DROP = 18f
private const val SHADOW_REACH = 0.25f

/**
 * The SquawkIt brand mark: three record plates stacked in perspective, filling the smaller side of
 * [modifier]'s box. [pose] is read in the draw phase, once per plate (bottom first), so an
 * animation can move the plates without recomposing; the default is the resting stack.
 */
@Composable
fun BrandStack(
  modifier: Modifier = Modifier,
  pose: (plate: Int) -> PlatePose = { PlatePose.Rest },
) {
  val art = remember { BrandStackArt() }
  Canvas(modifier) { with(art) { draw(pose) } }
}

/** The stack's paths and brushes, built once in the icon's 1024 space and scaled when drawn. */
private class BrandStackArt {
  private val faces = List(PLATE_COUNT) { BrandStackGeometry.face(it).toPath() }
  private val edges = List(PLATE_COUNT) { BrandStackGeometry.edge(it).toPath() }
  private val faceBrushes = List(PLATE_COUNT) { plate ->
    val y = BrandStackGeometry.faceY(plate)
    Brush.linearGradient(
      colors = listOf(PLATE_COLORS[plate].faceStart, PLATE_COLORS[plate].faceEnd),
      start = Offset(CENTRE_X - HALF_WIDTH, y - HALF_HEIGHT),
      end = Offset(CENTRE_X + HALF_WIDTH, y + HALF_HEIGHT),
    )
  }
  private val edgeBrushes = List(PLATE_COUNT) { SolidColor(PLATE_COLORS[it].edge) }
  private val shadow = SolidColor(Color.Black)
  private val corners = Stroke(width = CORNER_STROKE, join = StrokeJoin.Round)

  fun DrawScope.draw(pose: (plate: Int) -> PlatePose) {
    val scale = size.minDimension / VIEWPORT
    withTransform({
      scale(scale, scale, pivot = Offset.Zero)
      translate(-VIEWPORT_X, -VIEWPORT_Y)
    }) {
      for (plate in 0 until PLATE_COUNT) {
        val own = pose(plate)
        if (own.alpha <= 0f) continue
        translate(top = -own.lift * VIEWPORT) {
          rounded(edges[plate], edgeBrushes[plate], own.alpha)
          rounded(faces[plate], faceBrushes[plate], own.alpha)
          if (plate + 1 < PLATE_COUNT) castShadow(plate, own, pose(plate + 1))
        }
      }
    }
  }

  /** The plate above [plate] darkens its face, fading out as that plate lifts away. */
  private fun DrawScope.castShadow(plate: Int, own: PlatePose, above: PlatePose) {
    val gap = above.lift - own.lift
    val alpha = SHADOW_ALPHA * above.alpha * (1f - gap / SHADOW_REACH).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    clipPath(faces[plate]) {
      translate(top = SHADOW_DROP) { rounded(edges[plate + 1], shadow, alpha) }
    }
  }

  /** Fill plus a round-joined stroke of the same paint, which is what rounds a plate's corners. */
  private fun DrawScope.rounded(path: Path, brush: Brush, alpha: Float) {
    drawPath(path, brush, alpha)
    drawPath(path, brush, alpha, corners)
  }
}

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()
