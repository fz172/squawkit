package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.viewing.chart.DropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.NewPaneTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.PaneSeries
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDrag
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDragState
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogRow

/**
 * The scrolling column of panes: the record's header, the visible window, a pane each, the drop
 * target that spawns a new pane — and, when there is no sidebar footer to hold it, the ad slot
 * under them all.
 *
 * The chip in flight is drawn here rather than inside a pane so it can cross pane boundaries; it is
 * positioned against this column's own origin in the window.
 */
@Composable
internal fun ViewerPanes(
  state: DataLogViewerUiState.Ready,
  row: DataLogRow,
  cursorIndex: Int,
  seriesInfo: (SeriesKey) -> DataLogSeries?,
  paneSeries: (SeriesKey) -> PaneSeries?,
  dragState: SeriesDragState,
  onDrop: (SeriesDrag, DropTarget?) -> Unit,
  viewModel: DataLogViewerViewModel,
  adUnderPanes: Boolean,
  adSlot: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  var boxOrigin by remember { mutableStateOf(Offset.Zero) }
  Box(modifier = modifier.onGloballyPositioned {
    boxOrigin = it.positionInWindow()
  }) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
        horizontal = Spacing.screenPadding,
        vertical = Spacing.large
      ),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      item { ViewerHeaderItem(row, state.record) }

      if (state.view != null) {
        item {
          ViewerRangeRow(state.view) { viewModel.onGesture(GestureIntent.Reset) }
        }
      }
      items(state.layout.panes, key = { it.id.value }) { pane ->
        ViewerPane(
          pane = pane,
          state = state,
          originSecondsOfDay = row.startLocal.time.toSecondOfDay(),
          cursorIndex = cursorIndex,
          seriesInfo = seriesInfo,
          paneSeries = paneSeries,
          dragState = dragState,
          onDrop = onDrop,
          onRemoveSeries = { key -> viewModel.removeSeries(pane.id, key) },
          onRemovePane = { viewModel.removePane(pane.id) },
          onTouched = { viewModel.setTargetPane(pane.id) },
          onGesture = viewModel::onGesture,
        )
      }
      item {
        NewPaneTarget(
          dragState = dragState,
          onTap = { viewModel.spawnPane() })
      }
      // Phones, and wide layouts with the sidebar collapsed, carry the slot here under the panes.
      // Otherwise it is the sidebar's footer.
      if (adUnderPanes) item { adSlot() }
    }
    dragState.drag?.let { drag -> DragChip(drag, boxOrigin) }
  }
}
