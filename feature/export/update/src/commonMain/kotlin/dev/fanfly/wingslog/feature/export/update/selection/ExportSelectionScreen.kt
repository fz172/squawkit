package dev.fanfly.wingslog.feature.export.update.selection

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.bar.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.layout.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.update.selection.result.ErrorResult
import dev.fanfly.wingslog.feature.export.update.selection.result.SuccessResult
import dev.fanfly.wingslog.feature.export.update.selection.running.RunningContent
import dev.fanfly.wingslog.feature.export.update.selection.setup.ConfiguringContent
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_history_action
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ExportSelectionScreen(
  state: ExportUiState,
  onNavigateBack: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onToggleThing: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onToggleFormat: (ExportFormat) -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
  onExport: () -> Unit,
  onCancel: () -> Unit,
  onDownloadExport: (exportId: String, filePath: String, fileName: String) -> Unit,
  onSendToEmail: () -> Unit,
  onDone: () -> Unit,
  onRetry: () -> Unit,
  onSeePlans: () -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  // There's no explicit "Done" action on the success screen anymore — the same cleanup (reset to
  // the last editable configuration) now happens on back navigation, gesture included.
  BackHandler(enabled = state is ExportUiState.Success) { onDone() }

  // The success screen's action bar isn't a real Scaffold bottomBar (it's pinned to the bottom of
  // the content column instead, matching ConfiguringContent's pattern), so Scaffold can't push the
  // snackbar above it automatically — measure it and pad the snackbar host ourselves.
  var successActionsHeight by remember { mutableStateOf(0.dp) }

  Scaffold(
    topBar = {
      ConstrainedTopBar(ContentWidth.Form) {
        WingsLogTopAppBar(
          title = stringResource(Res.string.feature_name_export_logs),
          onBackClick = when (state) {
            is ExportUiState.Running -> onCancel
            is ExportUiState.Success -> onDone
            else -> onNavigateBack
          },
          actions = {
            if (state is ExportUiState.Configuring && state.things.isNotEmpty()) {
              IconButton(onClick = onNavigateToHistory) {
                Icon(
                  imageVector = Icons.Default.History,
                  contentDescription = stringResource(Res.string.export_history_action),
                )
              }
            }
          },
        )
      }
    },
    snackbarHost = {
      SnackbarHost(
        snackbarHostState,
        modifier = Modifier.padding(
          bottom = if (state is ExportUiState.Success) successActionsHeight else 0.dp
        ),
      )
    },
  ) { innerPadding ->
    val layoutDirection = LocalLayoutDirection.current
    when (state) {
      is ExportUiState.Configuring -> ConfiguringContent(
        state = state,
        // The pinned bottom bar runs edge-to-edge and adds its own navigation-bar inset, so the
        // content keeps only the top/horizontal scaffold insets — applying the bottom one here too
        // would double-pad the bar above the nav bar.
        modifier = Modifier.padding(
          top = innerPadding.calculateTopPadding(),
          start = innerPadding.calculateStartPadding(layoutDirection),
          end = innerPadding.calculateEndPadding(layoutDirection),
        ),
        onToggleThing = onToggleThing,
        onSelectAll = onSelectAll,
        onClearAll = onClearAll,
        onToggleFormat = onToggleFormat,
        onDateRangeChange = onDateRangeChange,
        onCustomRangeChange = onCustomRangeChange,
        onNavigateToHistory = onNavigateToHistory,
        onExport = onExport,
      )

      is ExportUiState.Running -> RunningContent(
        state = state,
        modifier = Modifier.padding(innerPadding),
        onCancel = onCancel,
      )

      is ExportUiState.Success -> SuccessResult(
        state = state,
        modifier = Modifier.padding(innerPadding),
        onDownload = onDownloadExport,
        onSendToEmail = onSendToEmail,
        onHistory = onNavigateToHistory,
        onSeePlans = onSeePlans,
        onActionsHeightChanged = { successActionsHeight = it },
      )

      is ExportUiState.Error -> ErrorResult(
        modifier = Modifier.padding(innerPadding),
        onRetry = onRetry,
        onBack = onNavigateBack,
      )
    }
  }
}
