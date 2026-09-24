package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import dev.fanfly.wingslog.feature.datalog.model.MapTileProvider
import dev.fanfly.wingslog.feature.datalog.model.Pane
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.model.chart.PaneKind
import dev.fanfly.wingslog.feature.datalog.model.chart.paneKind
import dev.fanfly.wingslog.feature.datalog.viewing.chart.ChartPane
import dev.fanfly.wingslog.feature.datalog.viewing.chart.ChipInfo
import dev.fanfly.wingslog.feature.datalog.viewing.chart.DropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.MapPane
import dev.fanfly.wingslog.feature.datalog.viewing.chart.PaneHeaderChips
import dev.fanfly.wingslog.feature.datalog.viewing.chart.PaneSeries
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDrag
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDragState
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesPalette
import dev.fanfly.wingslog.feature.datalog.viewing.chart.dropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.formatSeriesValue
import org.koin.compose.koinInject

/**
 * One pane: its chips, then either the map (a position series can only be there) or a chart.
 *
 * [seriesInfo] and [paneSeries] are the record's catalogue and plottable columns as lookups rather
 * than maps, so the pane asks about the keys it holds without a column-keyed map crossing the call.
 */
@Composable
internal fun ViewerPane(
  pane: Pane,
  state: DataLogViewerUiState.Ready,
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
  val chips = pane.series.mapNotNull { key ->
    val info = seriesInfo(key) ?: return@mapNotNull null
    val column = state.data.numeric[key.column]
    val value =
      if (cursorIndex >= 0 && column != null) column.raw[cursorIndex].takeUnless { it.isNaN() } else null
    ChipInfo(
      key = key,
      shortName = info.short_name.ifBlank { info.name },
      unit = info.unit,
      color = SeriesPalette.colorFor(
        key,
        info.canonical_id,
        dark
      ),
      value = value?.let(::formatSeriesValue),
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
        timeSeconds = state.data.timeSeconds,
        durationSeconds = state.record.duration_seconds,
        view = state.view,
        cursorIndex = cursorIndex,
        isTarget = pane.id == state.layout.targetPane,
        provider = tileProvider,
      )
    } else {
      ChartPane(
        series = pane.series.mapNotNull { key -> paneSeries(key) },
        timeSeconds = state.data.timeSeconds,
        durationSeconds = state.record.duration_seconds,
        view = state.view,
        cursorT = state.cursorT,
        isTarget = pane.id == state.layout.targetPane,
        onGesture = { intent ->
          // The last pane touched is where the sidebar adds series (PRD R25).
          onTouched()
          onGesture(intent)
        },
      )
    }
  }
}
