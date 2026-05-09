package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.logs.viewing.log.compose.MaintenanceLogListContent
import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListEvent
import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LogsTab(
  aircraftId: String,
  syncStates: Map<String, BlobSyncState> = emptyMap(),
  onNavigateToAddLog: () -> Unit,
  onNavigateToEditLog: (logId: String) -> Unit,
  onTaskClick: (taskId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel: MaintenanceLogListViewModel =
    koinViewModel(parameters = { parametersOf(aircraftId) })
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val attachmentOpener: AttachmentOpener = koinInject()
  val coroutineScope = rememberCoroutineScope()
  var openError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is MaintenanceLogListEvent.NavigateToCreateLog -> onNavigateToAddLog()
        is MaintenanceLogListEvent.NavigateToEditLog -> onNavigateToEditLog(event.logId)
      }
    }
  }

  MaintenanceLogListContent(
    uiState = uiState,
    syncStates = syncStates,
    onSearchQueryChange = viewModel::onSearchQueryChange,
    onComponentFilterToggle = viewModel::onComponentFilterToggle,
    onClearFilter = viewModel::clearFilter,
    onRetry = viewModel::retryLoading,
    onLogClick = viewModel::onLogClick,
    onDismissDetail = {
      openError = null
      viewModel.onDismissDetail()
    },
    onEditLog = viewModel::onEditLog,
    onAddLog = viewModel::onAddLog,
    onAttachmentTap = { attachment ->
      openError = null
      coroutineScope.launch {
        attachmentOpener.open(attachment).collect { state ->
          if (state is OpenState.Failed) openError = state.error.message
        }
      }
    },
    openError = openError,
    onTaskClick = onTaskClick,
    modifier = modifier,
  )
}
