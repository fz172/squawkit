package dev.fanfly.wingslog.feature.export.update

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryOutcome
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportHistoryViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_download_failed
import wingslog.feature.export.sharedassets.generated.resources.export_download_success
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
  val downloader = rememberExportFileDownloader()
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  // LaunchedEffect below only launches once and its collect loop runs for the composable's whole
  // lifetime, so it must read these through rememberUpdatedState rather than close over the plain
  // stringResource() values directly — otherwise it's stuck with whatever they resolved to on that
  // first composition (which can be blank if resources hadn't finished loading yet, e.g. on web).
  val sentMessage by rememberUpdatedState(stringResource(Res.string.export_history_delivery_sent))
  val failedMessage by rememberUpdatedState(stringResource(Res.string.export_history_delivery_failed))
  val throttledMessage by rememberUpdatedState(stringResource(Res.string.export_history_delivery_throttled))
  val inProgressMessage by rememberUpdatedState(stringResource(Res.string.export_history_delivery_in_progress))
  val downloadedMessage by rememberUpdatedState(stringResource(Res.string.export_download_success))
  val downloadFailedMessage by rememberUpdatedState(stringResource(Res.string.export_download_failed))

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
    onDownloadExport = { exportId, filePath, fileName ->
      coroutineScope.launch {
        val success = downloader.download(filePath, fileName) {
          viewModel.fetchArchiveBytes(exportId)
        }
        snackbarHostState.showSnackbar(if (success) downloadedMessage else downloadFailedMessage)
      }
    },
    onResendDelivery = { record -> viewModel.onResendDelivery(record.export_id) },
    onRetryDelivery = { record -> viewModel.onRetryDelivery(record.export_id) },
    onSaveToDevice = { record -> viewModel.onSaveToDevice(record.export_id) },
    onDelete = { record -> viewModel.onDelete(record.export_id) },
    snackbarHostState = snackbarHostState,
  )
}
