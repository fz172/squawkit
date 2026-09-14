package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.model.chart.Axis
import dev.fanfly.wingslog.feature.datalog.model.chart.DecimatedSeries
import dev.fanfly.wingslog.feature.datalog.model.chart.Decimation
import dev.fanfly.wingslog.feature.datalog.model.chart.Navigation
import dev.fanfly.wingslog.feature.datalog.model.chart.TimeTicks
import dev.fanfly.wingslog.feature.datalog.model.chart.UnitGroups
import dev.fanfly.wingslog.feature.datalog.model.chart.YRange
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_pane_empty

/** One series as a pane draws it: the forward-filled column plus what the axis label needs. */
class PaneSeries(
  val key: SeriesKey,
  val unit: String,
  val canonicalId: String,
  val values: FloatArray,
)

/** Pane heights from design §11.7; no Spacing token covers them. */
private val PaneHeightCompact: Dp = 170.dp
private val PaneHeightWide: Dp = 150.dp
private val AxisLabelSize = 10.sp
private val CursorStroke = 1.dp
private val SeriesStroke = 1.5.dp
private const val GRID_ALPHA = 0.6f
private const val BRUSH_ALPHA = 0.18f

/** The axis label style: JetBrains Mono at 10 sp (design §11.2). */
@Composable
internal fun axisLabelStyle(): TextStyle =
  WingslogTypography.dataSmall.copy(fontSize = AxisLabelSize)

/**
 * One Canvas per pane (design §11.2). Per frame each series is decimated to the pixel column over
 * the visible window and drawn as a path with two points per column. The first unit group reads
 * on the left axis, the second on the right; grid at quartiles; the cursor is a 1 dp `tertiary`
 * line; the target pane carries a `tertiary` border. Gestures come from [chartGestures]; a live
 * brush draws as a translucent `tertiary` band.
 */
@Composable
fun ChartPane(
  series: List<PaneSeries>,
  timeSeconds: IntArray,
  durationSeconds: Int,
  view: ViewWindow?,
  cursorT: Double?,
  isTarget: Boolean,
  onGesture: (GestureIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  val dark = isSystemInDarkTheme()
  val height =
    if (LocalLayoutTier.current.isCompact) PaneHeightCompact else PaneHeightWide
  val borderColor =
    if (isTarget) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
  val gridColor =
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = GRID_ALPHA)
  val cursorColor = MaterialTheme.colorScheme.tertiary
  val surface = MaterialTheme.colorScheme.surface
  val measurer = rememberTextMeasurer()
  val labelStyle = axisLabelStyle()
  var widthPx by remember { mutableIntStateOf(0) }
  var brush by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
  val window = Navigation.effective(view, durationSeconds)

  // Decimation runs synchronously here: O(rows) per series, well under a millisecond (R28).
  val columns: Map<SeriesKey, DecimatedSeries> =
    remember(view, widthPx, series) {
      if (widthPx <= 0) emptyMap()
      else series.associate {
        it.key to Decimation.decimate(
          it.values,
          timeSeconds,
          view,
          widthPx
        )
      }
    }
  val groups =
    remember(series) { UnitGroups.group(series.map { it.key to it.unit }) }
  val ranges: Map<String, YRange> = remember(columns, groups) {
    groups.associate { g -> g.unit to UnitGroups.fit(g.series.mapNotNull { columns[it] }) }
  }
  val colors: Map<SeriesKey, Color> = remember(series, dark) {
    series.associate {
      it.key to SeriesPalette.colorFor(
        it.key,
        it.canonicalId,
        dark
      )
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .border(
        Spacing.hairline,
        borderColor,
        RoundedCornerShape(Spacing.cardCornerRadius)
      ),
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .onSizeChanged { widthPx = it.width }
        .chartGestures(onIntent = onGesture, onBrush = { brush = it }),
    ) {
      drawRect(surface)
      val cursorStrokePx = CursorStroke.toPx()
      // Grid at quartiles of the height; the left group's values label them.
      for (i in 0..4) {
        val y = size.height * i / 4f
        drawLine(
          gridColor,
          Offset(0f, y),
          Offset(size.width, y),
          strokeWidth = cursorStrokePx
        )
      }
      groups.forEach { group ->
        val range = ranges[group.unit] ?: return@forEach
        group.series.forEach { key ->
          val decimated = columns[key] ?: return@forEach
          drawSeries(
            decimated,
            range,
            colors[key] ?: cursorColor,
            SeriesStroke.toPx()
          )
        }
        if (group.axis != Axis.NONE) {
          val color = colors[group.series.first()] ?: cursorColor
          UnitGroups.gridValues(range)
            .forEachIndexed { i, value ->
              val y = size.height * (1f - i / 4f)
              val label = measurer.measure(
                formatAxisValue(value),
                labelStyle.copy(color = color)
              )
              val x =
                if (group.axis == Axis.LEFT) Spacing.extraSmall.toPx() else size.width - label.size.width - Spacing.extraSmall.toPx()
              val top = (y - label.size.height / 2f).coerceIn(
                0f,
                size.height - label.size.height
              )
              drawText(label, topLeft = Offset(x, top))
            }
        }
      }
      brush?.let { range ->
        drawRect(
          cursorColor.copy(alpha = BRUSH_ALPHA),
          topLeft = Offset(range.start, 0f),
          size = Size(range.endInclusive - range.start, size.height),
        )
      }
      if (cursorT != null && widthPx > 0) {
        val x = TimeTicks.xOf(cursorT, window, widthPx)
        if (x in 0f..size.width) drawLine(
          cursorColor,
          Offset(x, 0f),
          Offset(x, size.height),
          strokeWidth = cursorStrokePx
        )
      }
    }
    if (series.isEmpty()) {
      Text(
        text = stringResource(Res.string.data_log_pane_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.Center),
      )
    }
  }
}

/**
 * Two points per pixel column, the column's min then its max, so spikes survive decimation. Empty
 * columns are skipped, not broken on: when the samples are sparser than the pixels (a short log,
 * or a deep zoom) most columns are empty and a path that restarted at each would be nothing but
 * isolated points. Consecutive samples join, which is what the forward-filled column is for.
 */
private fun DrawScope.drawSeries(
  decimated: DecimatedSeries,
  range: YRange,
  color: Color,
  strokePx: Float
) {
  val path = Path()
  var open = false
  val columnWidth = size.width / decimated.width
  for (i in 0 until decimated.width) {
    val lo = decimated.minY[i]
    val hi = decimated.maxY[i]
    if (lo.isNaN()) continue
    val x = (i + 0.5f) * columnWidth
    val yLo = size.height * (1f - range.fraction(lo))
    val yHi = size.height * (1f - range.fraction(hi))
    if (!open) {
      path.moveTo(x, yLo); open = true
    } else path.lineTo(x, yLo)
    if (yHi != yLo) path.lineTo(x, yHi)
  }
  drawPath(path, color, style = Stroke(width = strokePx))
}

/** Axis labels: integers as written, fractions to one decimal, thousands without separators. */
internal fun formatAxisValue(value: Float): String {
  val rounded = kotlin.math.round(value)
  return if (kotlin.math.abs(value - rounded) < 0.05f) rounded.toInt()
    .toString()
  else ((kotlin.math.round(value * 10f)) / 10f).toString()
}
