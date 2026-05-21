package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExportSelectionRoute(
  navController: NavController,
  onNavigateToHistory: () -> Unit,
  viewModel: ExportViewModel = koinViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val exportFileSharer = rememberExportFileSharer()

  ExportSelectionScreen(
    state = state,
    onNavigateBack = { navController.popBackStack() },
    onNavigateToHistory = onNavigateToHistory,
    onToggleAircraft = viewModel::onToggleAircraft,
    onSelectAll = viewModel::onSelectAll,
    onClearAll = viewModel::onClearAll,
    onToggleFormat = viewModel::onToggleFormat,
    onDateRangeChange = viewModel::onDateRangeChange,
    onCustomStartChange = viewModel::onCustomStartChange,
    onCustomEndChange = viewModel::onCustomEndChange,
    onExportDestinationEmailChanged = viewModel::onExportDestinationEmailChanged,
    onExport = viewModel::onExport,
    onCancel = viewModel::onCancel,
    onShareExport = exportFileSharer::share,
    onDone = {
      viewModel.onDone()
      navController.popBackStack()
    },
    onRetry = viewModel::onRetry,
  )
}
