package dev.fanfly.wingslog.feature.export.update.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.adaptive.layout.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.export.ExportRecord
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_history_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportHistoryScreen(
  state: ExportHistoryUiState,
  onNavigateBack: () -> Unit,
  onNew: () -> Unit,
  onDownloadExport: (exportId: String, filePath: String, fileName: String) -> Unit,
  onResendDelivery: (ExportRecord) -> Unit,
  onRetryDelivery: (ExportRecord) -> Unit,
  onSaveToDevice: (ExportRecord) -> Unit,
  onDelete: (ExportRecord) -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  Scaffold(
    topBar = {
      ConstrainedTopBar {
        WingsLogTopAppBar(
          title = stringResource(Res.string.export_history_title),
          onBackClick = onNavigateBack,
        )
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    val contentModifier = Modifier.padding(innerPadding)
      .fillMaxSize()
    when (state) {
      is ExportHistoryUiState.Loading -> LoadingContent(contentModifier)
      is ExportHistoryUiState.Loaded ->
        if (state.exports.isEmpty()) {
          EmptyContent(contentModifier, onNew)
        } else {
          ExportList(
            exports = state.exports,
            canEmailDelivery = state.canEmailDelivery,
            modifier = contentModifier,
            onDownloadExport = onDownloadExport,
            onResendDelivery = onResendDelivery,
            onRetryDelivery = onRetryDelivery,
            onSaveToDevice = onSaveToDevice,
            onDelete = onDelete,
          )
        }
    }
  }
}
