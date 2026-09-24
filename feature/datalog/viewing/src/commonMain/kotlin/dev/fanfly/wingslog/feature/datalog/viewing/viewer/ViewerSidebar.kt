package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.datalog.model.chart.PaneKind
import dev.fanfly.wingslog.feature.datalog.model.chart.paneKind
import dev.fanfly.wingslog.feature.datalog.viewing.chart.DropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDrag
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDragState
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesSidebar
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogRow

/**
 * The series catalogue and facts panel — a column beside the panes on a wide window, the end drawer
 * on a phone. Null until the record has loaded, which is what tells the caller there is no drawer
 * to open yet.
 */
@Composable
internal fun viewerSidebar(
  state: DataLogViewerUiState.Ready?,
  row: DataLogRow?,
  dragState: SeriesDragState,
  onDrop: (SeriesDrag, DropTarget?) -> Unit,
  viewModel: DataLogViewerViewModel,
): (@Composable () -> Unit)? {
  if (state == null || row == null) return null
  val facts = viewerFacts(state.record, row)
  val infoByColumn = state.record.series.associateBy { it.column }
  return {
    SeriesSidebar(
      catalogue = state.record.series,
      // The map pane's series reads as charted wherever the target happens to be: it is
      // the only pane a position series can be in, so the target says nothing about it.
      inTargetPane = state.layout.panes.firstOrNull { it.id == state.layout.targetPane }?.series?.toSet()
        .orEmpty() +
        state.layout.panes.filter { pane ->
          pane.series.firstOrNull()
            ?.let { infoByColumn[it.column]?.paneKind() } == PaneKind.MAP
        }
          .flatMap { it.series },
      tab = state.sidebarTab,
      onTab = viewModel::setSidebarTab,
      query = state.seriesQuery,
      onQuery = viewModel::setSeriesQuery,
      onAdd = { key ->
        state.layout.targetPane?.let {
          viewModel.toggleSeries(
            it,
            key
          )
        } ?: viewModel.spawnPane(key)
      },
      dragState = dragState,
      onDrop = onDrop,
      facts = facts,
      identityMismatch = row.identityMismatch,
    )
  }
}
