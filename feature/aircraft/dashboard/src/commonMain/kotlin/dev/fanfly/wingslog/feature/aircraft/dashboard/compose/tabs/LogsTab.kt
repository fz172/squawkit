package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.viewing.log.compose.MaintenanceLogListContent
import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListEvent
import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.sharedassets.generated.resources.add_log

@Composable
fun LogsTab(
  aircraftId: String,
  downloadingIds: Set<String>,
  onNavigateToAddLog: () -> Unit,
  onNavigateToEditLog: (logId: String) -> Unit,
  onTaskClick: (taskId: String) -> Unit,
  onAttachmentTap: (Attachment) -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel: MaintenanceLogListViewModel =
    koinViewModel(parameters = { parametersOf(aircraftId) })
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is MaintenanceLogListEvent.NavigateToCreateLog -> onNavigateToAddLog()
        is MaintenanceLogListEvent.NavigateToEditLog -> onNavigateToEditLog(event.logId)
      }
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    MaintenanceLogListContent(
      uiState = uiState,
      downloadingIds = downloadingIds,
      onSearchQueryChange = viewModel::onSearchQueryChange,
      onComponentFilterToggle = viewModel::onComponentFilterToggle,
      onClearFilter = viewModel::clearFilter,
      onRetry = viewModel::retryLoading,
      onLogClick = viewModel::onLogClick,
      onDismissDetail = viewModel::onDismissDetail,
      onEditLog = viewModel::onEditLog,
      onAddLog = viewModel::onAddLog,
      onAttachmentTap = onAttachmentTap,
      onTaskClick = onTaskClick,
    )

    ExtendedFloatingActionButton(
      onClick = viewModel::onAddLog,
      icon = { Icon(Icons.Default.Add, contentDescription = null) },
      text = { Text(stringResource(SharedRes.string.add_log)) },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(Spacing.screenPadding)
    )
  }
}
