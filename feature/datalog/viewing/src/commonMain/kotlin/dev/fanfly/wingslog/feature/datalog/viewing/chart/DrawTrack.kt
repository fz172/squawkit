package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.fanfly.wingslog.feature.datalog.model.PositionColumn
import dev.fanfly.wingslog.feature.datalog.model.chart.MapViewport
import dev.fanfly.wingslog.feature.datalog.model.chart.WebMercator

/**
 * The track as one path, broken wherever a row had no fix or falls outside [inWindow], so a gap in
 * the log is a gap on the map rather than a straight line across it.
 */
internal fun DrawScope.drawTrack(
  position: PositionColumn,
  viewport: MapViewport,
  inWindow: (Int) -> Boolean,
  color: androidx.compose.ui.graphics.Color,
  strokeWidth: Float,
) {
  val path = Path()
  var started = false
  for (i in position.latitude.indices) {
    val lat = position.latitude[i]
    val lon = position.longitude[i]
    if (lat.isNaN() || lon.isNaN() || !inWindow(i)) {
      started = false
      continue
    }
    val x = viewport.screenX(WebMercator.normalizedX(lon))
    val y = viewport.screenY(WebMercator.normalizedY(lat))
    if (started) path.lineTo(x, y) else path.moveTo(x, y)
      .also { started = true }
  }
  drawPath(path, color, style = Stroke(width = strokeWidth))
}
