package dev.fanfly.wingslog.feature.export.update

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryOutcome
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_history_delivery_failed
import wingslog.feature.export.sharedassets.generated.resources.export_history_delivery_in_progress
import wingslog.feature.export.sharedassets.generated.resources.export_history_delivery_sent
import wingslog.feature.export.sharedassets.generated.resources.export_history_delivery_throttled

@Composable
fun ExportSelectionRoute(
  navController: NavController,
  onNavigateToHistory: () -> Unit,
  onSeePlans: () -> Unit,
  viewModel: ExportViewModel = koinViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val exportFileSharer = rememberExportFileSharer()
  val snackbarHostState = remember { SnackbarHostState() }

  val sentMessage = stringResource(Res.string.export_history_delivery_sent)
  val failedMessage = stringResource(Res.string.export_history_delivery_failed)
  val throttledMessage = stringResource(Res.string.export_history_delivery_throttled)
  val inProgressMessage = stringResource(Res.string.export_history_delivery_in_progress)

  LaunchedEffect(viewModel) {
    viewModel.deliveryEvents.collect { outcome ->
      val message = when (outcome) {
        is ExportDeliveryOutcome.Sent -> sentMessage
        is ExportDeliveryOutcome.Throttled -> throttledMessage
        is ExportDeliveryOutcome.InProgress -> inProgressMessage
        is ExportDeliveryOutcome.Failed ->
          if (outcome.reason.isNotBlank()) "$failedMessage: ${outcome.reason}" else failedMessage
      }
      snackbarHostState.showSnackbar(message)
    }
  }

  ExportSelectionScreen(
    state = state,
    onNavigateBack = { navController.popBackStack() },
    onNavigateToHistory = onNavigateToHistory,
    onToggleAircraft = viewModel::onToggleAircraft,
    onSelectAll = viewModel::onSelectAll,
    onClearAll = viewModel::onClearAll,
    onToggleFormat = viewModel::onToggleFormat,
    onDateRangeChange = viewModel::onDateRangeChange,
    onCustomRangeChange = viewModel::onCustomRangeChange,
    onExport = viewModel::onExport,
    onCancel = viewModel::onCancel,
    onShareExport = exportFileSharer::share,
    onSendToEmail = viewModel::onSendToEmail,
    onDone = {
      viewModel.onDone()
      navController.popBackStack()
    },
    onRetry = viewModel::onRetry,
    onSeePlans = onSeePlans,
    snackbarHostState = snackbarHostState,
  )
}
