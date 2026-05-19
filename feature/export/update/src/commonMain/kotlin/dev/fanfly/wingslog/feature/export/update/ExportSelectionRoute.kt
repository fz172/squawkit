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
  viewModel: ExportViewModel = koinViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  ExportSelectionScreen(
    state = state,
    onNavigateBack = { navController.popBackStack() },
    onToggleAircraft = viewModel::onToggleAircraft,
    onSelectAll = viewModel::onSelectAll,
    onClearAll = viewModel::onClearAll,
    onDateRangeChange = viewModel::onDateRangeChange,
    onCustomStartChange = viewModel::onCustomStartChange,
    onCustomEndChange = viewModel::onCustomEndChange,
    onToggleIncludeOpenSquawks = viewModel::onToggleIncludeOpenSquawks,
    onExport = viewModel::onExport,
    onCancel = viewModel::onCancel,
    onDone = {
      viewModel.onDone()
      navController.popBackStack()
    },
    onRetry = viewModel::onRetry,
  )
}
