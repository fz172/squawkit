package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import dev.fanfly.wingslog.feature.datalog.model.MapTileProvider
import dev.fanfly.wingslog.feature.datalog.model.Pane
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.model.chart.DiscreteSpans
import dev.fanfly.wingslog.feature.datalog.model.chart.PaneKind
import dev.fanfly.wingslog.feature.datalog.model.chart.chipLabel
import dev.fanfly.wingslog.feature.datalog.model.chart.isMultiEngine
import dev.fanfly.wingslog.feature.datalog.model.chart.isSwimlaneSeries
import dev.fanfly.wingslog.feature.datalog.model.chart.paneKind
import dev.fanfly.wingslog.feature.datalog.viewing.chart.ChartPane
import dev.fanfly.wingslog.feature.datalog.viewing.chart.ChipInfo
import dev.fanfly.wingslog.feature.datalog.viewing.chart.DiscreteSwimlanes
import dev.fanfly.wingslog.feature.datalog.viewing.chart.DropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.MapPane
import dev.fanfly.wingslog.feature.datalog.viewing.chart.PaneHeaderChips
import dev.fanfly.wingslog.feature.datalog.viewing.chart.PaneSeries
import dev.fanfly.wingslog.feature.datalog.viewing.chart.ProjectedSpan
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDrag
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDragState
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesPalette
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SwimlaneSeries
import dev.fanfly.wingslog.feature.datalog.viewing.chart.TimeAxis
import dev.fanfly.wingslog.feature.datalog.viewing.chart.dropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.formatSeriesValue
import org.koin.compose.koinInject

/**
 * One pane: its chips, then either the map (a position series can only be there) or a chart with
 * its time axis and draggable cursor handle underneath.
 *
 * [seriesInfo] and [paneSeries] are the record's catalogue and plottable columns as lookups rather
 * than maps, so the pane asks about the keys it holds without a column-keyed map crossing the call.
 */
@Composable
internal fun ViewerPane(
  pane: Pane,
  state: DataLogViewerUiState.Ready,
  originSecondsOfDay: Int,
  cursorIndex: Int,
  seriesInfo: (SeriesKey) -> DataLogSeries?,
  paneSeries: (SeriesKey) -> PaneSeries?,
  dragState: SeriesDragState,
  onDrop: (SeriesDrag, DropTarget?) -> Unit,
  onRemoveSeries: (SeriesKey) -> Unit,
  onRemovePane: () -> Unit,
  onTouched: () -> Unit,
  onGesture: (GestureIntent) -> Unit,
) {
  val dark = isSystemInDarkTheme()
  val tileProvider: MapTileProvider = koinInject()
  val multiEngine = remember(state.record.series) {
    state.record.series.isMultiEngine()
  }
  val chips = pane.series.mapNotNull { key ->
    val info = seriesInfo(key) ?: return@mapNotNull null
    val formattedValue = if (cursorIndex >= 0) {
      val textCol = state.data.text[key.column]
      if (textCol != null) {
        textCol.getOrNull(cursorIndex)?.trim()?.takeIf { it.isNotEmpty() }
      } else {
        state.data.numeric[key.column]?.raw?.getOrNull(cursorIndex)
          ?.takeUnless { it.isNaN() }
          ?.let { v ->
            if (info.kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE) {
              if (v == 0f) "OFF" else "ON"
            } else {
              formatSeriesValue(v)
            }
          }
      }
    } else null
    ChipInfo(
      key = key,
      shortName = info.chipLabel(multiEngine),
      unit = info.unit,
      color = SeriesPalette.colorFor(
        key,
        info.canonical_id,
        dark
      ),
      value = formattedValue,
    )
  }
  Column(
    modifier = Modifier.dropTarget(
      DropTarget.OnPane(pane.id),
      dragState
    ),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    PaneHeaderChips(
      pane = pane.id,
      chips = chips,
      dragState = dragState,
      onRemoveSeries = onRemoveSeries,
      onRemovePane = onRemovePane,
      onDrop = onDrop,
    )
    val paneKind = pane.series.firstOrNull()
      ?.let { seriesInfo(it)?.paneKind() }
      ?: PaneKind.CHART
    val positions = state.data.position
    if (paneKind == PaneKind.MAP && positions != null) {
      MapPane(
        position = positions,
        provider = tileProvider,
        timeSeconds = state.data.timeSeconds,
        view = state.view,
        cursorIndex = cursorIndex,
        isTarget = pane.id == state.layout.targetPane,
      )
    } else {
      val numericSeries = pane.series.mapNotNull { key ->
        val info = seriesInfo(key)
        if (info?.isSwimlaneSeries == true) null else paneSeries(key)
      }
      val swimlanes = remember(pane.series, state.data, dark) {
        pane.series.mapNotNull { key ->
          val info = seriesInfo(key) ?: return@mapNotNull null
          if (!info.isSwimlaneSeries) return@mapNotNull null
          val label = info.chipLabel(multiEngine)
          val color = SeriesPalette.colorFor(key, info.canonical_id, dark)
          val spans = when (info.kind) {
            DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT ->
              state.data.text[key.column]?.let {
                DiscreteSpans.fromText(
                  it,
                  state.data.timeSeconds,
                  state.record.duration_seconds
                )
              }

            DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE ->
              state.data.numeric[key.column]?.filled?.let {
                DiscreteSpans.fromDiscrete(
                  it,
                  state.data.timeSeconds,
                  state.record.duration_seconds,
                  label
                )
              }

            else ->
              state.data.numeric[key.column]?.filled?.let {
                DiscreteSpans.fromSteppedNumeric(
                  it,
                  state.data.timeSeconds,
                  state.record.duration_seconds,
                  info.unit
                )
              }
          } ?: emptyList()
          SwimlaneSeries(key = key, label = label, color = color, spans = spans)
        }
      }
      var hoveredSpan by remember(pane.id) { mutableStateOf<ProjectedSpan?>(null) }
      var pinnedSpan by remember(pane.id) { mutableStateOf<ProjectedSpan?>(null) }

      ChartPane(
        series = numericSeries,
        timeSeconds = state.data.timeSeconds,
        durationSeconds = state.record.duration_seconds,
        view = state.view,
        cursorT = state.cursorT,
        isTarget = pane.id == state.layout.targetPane,
        projectedSpan = hoveredSpan ?: pinnedSpan,
        showEmptyHint = numericSeries.isEmpty() && swimlanes.isEmpty(),
        onGesture = { intent ->
          // The last pane touched is where the sidebar adds series (PRD R25).
          onTouched()
          onGesture(intent)
        },
      )
      if (swimlanes.isNotEmpty()) {
        DiscreteSwimlanes(
          swimlanes = swimlanes,
          durationSeconds = state.record.duration_seconds,
          view = state.view,
          cursorT = state.cursorT,
          pinnedSpan = pinnedSpan,
          onHoverSpan = { hoveredSpan = it },
          onTogglePinSpan = { span ->
            onTouched()
            pinnedSpan = if (pinnedSpan == span) null else span
          },
        )
      }
      TimeAxis(
        view = state.view,
        durationSeconds = state.record.duration_seconds,
        cursorT = state.cursorT,
        clockAxis = state.clockAxis,
        originSecondsOfDay = originSecondsOfDay,
        onScrub = { fraction ->
          onTouched()
          onGesture(GestureIntent.Cursor(fraction))
        },
      )
    }
  }
}
