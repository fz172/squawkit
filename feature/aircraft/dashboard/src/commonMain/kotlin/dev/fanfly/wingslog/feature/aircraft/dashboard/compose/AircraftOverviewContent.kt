package dev.fanfly.wingslog.feature.aircraft.dashboard.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.AircraftDashboardTabRow
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.LogsTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.MaintenanceTasksTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.OverviewTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.tasks.update.compose.DeleteTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.viewing.TaskDetailSheet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.back
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.logs.viewing.generated.resources.edit_aircraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftOverviewContent(
  state: AircraftOverviewUiState.Success,
  snackbarHostState: SnackbarHostState,
  onAction: (AircraftOverviewAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  val pagerState = rememberPagerState { 3 }
  val coroutineScope = rememberCoroutineScope()
  val attachmentOpener: AttachmentOpener = koinInject()
  var taskSheetOpenError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(state.showLegacyAttachmentBanner) {
    if (state.showLegacyAttachmentBanner) {
      snackbarHostState.showSnackbar("Some attachments were created before this version and may need to be re-downloaded.")
      onAction(AircraftOverviewAction.DismissLegacyBanner)
    }
  }

  Scaffold(
    modifier = modifier,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.surface,
    topBar = {
      TopAppBar(
        title = {},
        navigationIcon = {
          IconButton(onClick = { onAction(AircraftOverviewAction.BackClick) }) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(CoreRes.string.back)
            )
          }
        },
        actions = {
          IconButton(onClick = { onAction(AircraftOverviewAction.EditClick(state.aircraft.id)) }) {
            Icon(
              Icons.Default.Settings,
              contentDescription = stringResource(MaintenanceRes.string.edit_aircraft)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color.Transparent,
          scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
      )
    }
  ) { paddingValues ->

    state.selectedTask?.let { selectedTask ->
      TaskDetailSheet(
        cardWithStatus = selectedTask,
        logs = state.logsForSelectedTask,
        onDismiss = {
          taskSheetOpenError = null
          onAction(AircraftOverviewAction.DismissTaskDetail)
        },
        onEditClick = {
          onAction(
            AircraftOverviewAction.EditTaskClick(
              state.aircraft.id,
              selectedTask.card.id
            )
          )
        },
        onAttachmentTap = { attachment ->
          taskSheetOpenError = null
          coroutineScope.launch {
            attachmentOpener.open(attachment).collect { openState ->
              if (openState is OpenState.Failed) taskSheetOpenError = openState.error.message
            }
          }
        },
        syncStates = state.syncStates,
        openError = taskSheetOpenError,
        attachmentEnabled = state.attachmentEnabled,
      )
    }

    state.deletingTaskId?.let { deletingId ->
      val title = (state.activeTasks + state.completedTasks)
        .find { it.card.id == deletingId }?.card?.title ?: ""
      DeleteTaskConfirmDialog(
        title = title,
        onConfirm = { onAction(AircraftOverviewAction.ConfirmDeleteTask) },
        onDismiss = { onAction(AircraftOverviewAction.CancelDeleteTask) },
      )
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      AircraftDashboardTabRow(
        selectedTabIndex = pagerState.currentPage,
        onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } }
      )

      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f)
      ) { page ->
        when (page) {
          0 -> OverviewTab(
            state = state,
            onAction = onAction
          )

          1 -> MaintenanceTasksTab(
            state = state,
            onAction = onAction
          )

          2 -> LogsTab(
            aircraftId = state.aircraft.id,
            syncStates = state.syncStates,
            onNavigateToAddLog = { onAction(AircraftOverviewAction.AddLogClick(state.aircraft.id)) },
            onNavigateToEditLog = { logId ->
              onAction(
                AircraftOverviewAction.EditLogClick(
                  state.aircraft.id,
                  logId
                )
              )
            },
            onTaskClick = { taskId ->
              onAction(AircraftOverviewAction.TaskFromLogClick(taskId))
              coroutineScope.launch { pagerState.animateScrollToPage(1) }
            },
          )
        }
      }
    }
  }
}
