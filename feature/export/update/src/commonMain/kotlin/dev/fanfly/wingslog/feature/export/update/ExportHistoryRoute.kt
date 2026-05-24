package dev.fanfly.wingslog.feature.export.update

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryOutcome
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportHistoryViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_history_delivery_failed
import wingslog.feature.export.sharedassets.generated.resources.export_history_delivery_in_progress
import wingslog.feature.export.sharedassets.generated.resources.export_history_delivery_sent
import wingslog.feature.export.sharedassets.generated.resources.export_history_delivery_throttled

@Composable
fun ExportHistoryRoute(
  navController: NavController,
  viewModel: ExportHistoryViewModel = koinViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val exportFileSharer = rememberExportFileSharer()
  val snackbarHostState = remember { SnackbarHostState() }

  val sentMessage = stringResource(Res.string.export_history_delivery_sent)
  val failedMessage = stringResource(Res.string.export_history_delivery_failed)
  val throttledMessage =
    stringResource(Res.string.export_history_delivery_throttled)
  val inProgressMessage =
    stringResource(Res.string.export_history_delivery_in_progress)

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

  ExportHistoryScreen(
    state = state,
    onNavigateBack = { navController.popBackStack() },
    onNew = { navController.popBackStack() },
    onShareExport = exportFileSharer::share,
    onResendDelivery = { record -> viewModel.onResendDelivery(record.export_id) },
    onRetryDelivery = { record -> viewModel.onRetryDelivery(record.export_id) },
    onSaveToDevice = { record -> viewModel.onSaveToDevice(record.export_id) },
    onDelete = { record -> viewModel.onDelete(record.export_id) },
    snackbarHostState = snackbarHostState,
  )
}
