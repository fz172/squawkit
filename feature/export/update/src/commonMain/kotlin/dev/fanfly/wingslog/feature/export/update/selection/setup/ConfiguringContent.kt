package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.update.selection.DateRangeOption
import dev.fanfly.wingslog.feature.export.update.selection.ExportUiState
import kotlinx.datetime.LocalDate

@Composable
internal fun ConfiguringContent(
  state: ExportUiState.Configuring,
  modifier: Modifier,
  onToggleThing: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onToggleFormat: (ExportFormat) -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
  onNavigateToHistory: () -> Unit,
  onExport: () -> Unit,
) {
  if (!state.isLoadingThings && state.things.isEmpty()) {
    EmptyThingContent(modifier, onNavigateToHistory)
    return
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    ExportSetupList(
      state = state,
      onToggleThing = onToggleThing,
      onSelectAll = onSelectAll,
      onClearAll = onClearAll,
      onToggleFormat = onToggleFormat,
      onDateRangeChange = onDateRangeChange,
      onCustomRangeChange = onCustomRangeChange,
      modifier = Modifier
        .fillMaxHeight()
        .constrainedContentWidth(ContentWidth.Form)
        .padding(horizontal = Spacing.screenPadding),
      bottomPadding = ExportBottomBarReservedHeight,
    )
    if (state.things.isNotEmpty()) {
      Box(
        modifier = Modifier.align(Alignment.BottomCenter),
      ) {
        ExportBottomBar(state, onExport)
      }
    }
  }
}

private val ExportBottomBarReservedHeight = 176.dp
