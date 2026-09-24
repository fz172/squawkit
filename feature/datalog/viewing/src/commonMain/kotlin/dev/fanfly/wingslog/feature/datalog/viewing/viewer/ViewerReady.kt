package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.model.AdUnitSize
import dev.fanfly.wingslog.feature.ads.viewing.AdSlot
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.model.chart.Decimation
import dev.fanfly.wingslog.feature.datalog.model.chart.isPlottable
import dev.fanfly.wingslog.feature.datalog.viewing.chart.DropTarget
import dev.fanfly.wingslog.feature.datalog.viewing.chart.PaneSeries
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDrag
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDragState
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SidebarWidth
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogRow
import org.koin.compose.koinInject

/**
 * The loaded viewer: the panes, and beside them the sidebar on anything wider than a phone (where
 * the sidebar is the drawer the caller wraps around the whole scaffold instead).
 */
@Composable
internal fun ViewerReady(
  state: DataLogViewerUiState.Ready,
  row: DataLogRow,
  dragState: SeriesDragState,
  onDrop: (SeriesDrag, DropTarget?) -> Unit,
  viewModel: DataLogViewerViewModel,
  sidebar: (@Composable () -> Unit)?,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  val byColumn = remember(state.record, state.data) {
    state.record.series.filter { it.isPlottable }
      .mapNotNull { info ->
        state.data.numeric[info.column]?.let { column ->
          info.column to PaneSeries(
            SeriesKey(info.column),
            info.unit,
            info.canonical_id,
            column.filled
          )
        }
      }
      .toMap()
  }
  val cursorIndex = remember(state.cursorT, state.data) {
    state.cursorT?.let { Decimation.indexAt(state.data.timeSeconds, it) }
      ?: -1
  }
  val adsManager: AdsManager = koinInject()
  val showAds by adsManager.shouldShowsAds()
    .collectAsState(initial = false)
  // PRD R44a: one fixed unit, never in a pane and never over a chart. Android and iOS
  // only — shouldShowsAds() is already false where AppCapability has no ad product.
  val adSlot: @Composable () -> Unit = {
    if (showAds) {
      AdSlot(
        surface = AdSurface.DATA_LOGS,
        slotIndex = 0,
        size = AdUnitSize.BANNER,
        // The sidebar footer is a fixed column; a two-up band would run past its edge.
        maxUnits = 1,
      )
    }
  }
  val panes: @Composable (Modifier) -> Unit = { paneModifier ->
    ViewerPanes(
      state = state,
      row = row,
      cursorIndex = cursorIndex,
      seriesInfo = { key -> state.record.series.firstOrNull { it.column == key.column } },
      paneSeries = { byColumn[it.column] },
      dragState = dragState,
      onDrop = onDrop,
      viewModel = viewModel,
      compact = compact,
      adSlot = adSlot,
      modifier = paneModifier,
    )
  }
  if (compact) {
    // The sidebar is the drawer around the whole scaffold on this tier.
    panes(modifier)
  } else {
    Row(modifier = modifier) {
      panes(
        Modifier.weight(1f)
          .fillMaxSize()
      )
      VerticalDivider()
      Column(
        Modifier.width(SidebarWidth)
          .fillMaxSize()
      ) {
        Box(Modifier.weight(1f)) { sidebar?.invoke() }
        adSlot()
      }
    }
  }
  if (state.deleting) {
    DeleteDataLogDialog(
      onConfirm = viewModel::confirmDelete,
      onDismiss = viewModel::cancelDelete,
    )
  }
}
