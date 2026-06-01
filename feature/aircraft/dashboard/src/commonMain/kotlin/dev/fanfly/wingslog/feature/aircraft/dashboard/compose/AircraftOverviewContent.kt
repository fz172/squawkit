package dev.fanfly.wingslog.feature.aircraft.dashboard.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedFloatingAction
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.AircraftDashboardTabRow
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.AircraftTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.LogsTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.MaintenanceTasksTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.OverviewTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.SquawkTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.tasks.viewing.DeleteTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.viewing.TaskDetailSheet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.core.sharedassets.generated.resources.back
import wingslog.feature.logs.sharedassets.generated.resources.add_log
import wingslog.feature.logs.viewing.generated.resources.edit_aircraft
import wingslog.feature.squawk.sharedassets.generated.resources.add_squawk
import wingslog.feature.tasks.sharedassets.generated.resources.add_task
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as LogsSharedRes
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.squawk.sharedassets.generated.resources.Res as SquawkSharedRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as TasksSharedRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftOverviewContent(
  state: AircraftOverviewUiState.Success,
  snackbarHostState: SnackbarHostState,
  onAction: (AircraftOverviewAction) -> Unit,
  onMutationAction: ((AircraftOverviewAction) -> Unit)? = onAction,
  attachmentsAvailable: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val pagerState = rememberPagerState { AircraftTab.entries.size }
  val coroutineScope = rememberCoroutineScope()
  val attachmentOpener: AttachmentOpener = koinInject()
  var taskSheetOpenError by remember { mutableStateOf<String?>(null) }

  Scaffold(
    modifier = modifier,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      if (onMutationAction != null) when (AircraftTab.entries.getOrNull(
        pagerState.currentPage
      )) {
        AircraftTab.SQUAWKS -> ConstrainedFloatingAction(ContentWidth.Form) {
          ExtendedFloatingActionButton(
            onClick = {
              onMutationAction(
                AircraftOverviewAction.AddSquawkClick(
                  state.aircraft.id
                )
              )
            },
            icon = {
              Icon(
                Icons.Default.Add, contentDescription = null
              )
            },
            text = { Text(stringResource(SquawkSharedRes.string.add_squawk)) },
          )
        }

        AircraftTab.TASKS -> ConstrainedFloatingAction(ContentWidth.Form) {
          ExtendedFloatingActionButton(
            onClick = {
              onMutationAction(
                AircraftOverviewAction.AddTaskClick(
                  state.aircraft.id
                )
              )
            },
            icon = {
              Icon(
                Icons.Default.Add, contentDescription = null
              )
            },
            text = { Text(stringResource(TasksSharedRes.string.add_task)) },
          )
        }

        AircraftTab.LOGS -> ConstrainedFloatingAction(ContentWidth.Form) {
          ExtendedFloatingActionButton(
            onClick = {
              onMutationAction(
                AircraftOverviewAction.AddLogClick(
                  state.aircraft.id
                )
              )
            },
            icon = {
              Icon(
                Icons.Default.Add, contentDescription = null
              )
            },
            text = { Text(stringResource(LogsSharedRes.string.add_log)) },
          )
        }

        else -> {}
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    topBar = {
      ConstrainedTopBar {
        TopAppBar(
          title = {}, navigationIcon = {
            IconButton(onClick = { onAction(AircraftOverviewAction.BackClick) }) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(CoreRes.string.back)
              )
            }
          }, actions = {
            if (onMutationAction != null) {
              IconButton(onClick = {
                onMutationAction(
                  AircraftOverviewAction.EditClick(
                    state.aircraft.id
                  )
                )
              }) {
                Icon(
                  Icons.Default.Settings,
                  contentDescription = stringResource(MaintenanceRes.string.edit_aircraft)
                )
              }
            }
          }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
          )
        )
      }
    }) { paddingValues ->

    state.selectedTask?.let { selectedTask ->
      TaskDetailSheet(
        cardWithStatus = selectedTask,
        logs = state.logsForSelectedTask,
        onDismiss = {
          taskSheetOpenError = null
          onAction(AircraftOverviewAction.DismissTaskDetail)
        },
        onEditClick = onMutationAction?.let { mutate ->
          {
            mutate(
              AircraftOverviewAction.EditTaskClick(
                state.aircraft.id, selectedTask.card.id
              )
            )
          }
        },
        onAttachmentTap = { attachment ->
          taskSheetOpenError = null
          // Call open() synchronously inside the click handler so AttachmentOpenerWeb can
          // reserve window.open() during the user-gesture stack. Only the flow collection
          // moves into the coroutine.
          val openFlow = attachmentOpener.open(attachment)
          coroutineScope.launch {
            openFlow.collect { openState ->
              if (openState is OpenState.Failed) taskSheetOpenError =
                openState.error.message
            }
          }
        },
        syncStates = state.syncStates,
        openError = taskSheetOpenError,
        attachmentEnabled = attachmentsAvailable && state.attachmentEnabled,
      )
    }

    state.deletingTaskId?.let { deletingId ->
      val title =
        (state.activeTasks + state.completedTasks).find { it.card.id == deletingId }?.card?.title
          ?: ""
      DeleteTaskConfirmDialog(
        title = title,
        onConfirm = { onAction(AircraftOverviewAction.ConfirmDeleteTask) },
        onDismiss = { onAction(AircraftOverviewAction.CancelDeleteTask) },
      )
    }
    Box(
      modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier.fillMaxHeight()
          .constrainedContentWidth(ContentWidth.Form)
          .padding(paddingValues)
      ) {
        AircraftDashboardTabRow(
          selectedTabIndex = pagerState.currentPage,
          modifier = Modifier.constrainedContentWidth(ContentWidth.Form),
          onTabSelected = {
            coroutineScope.launch {
              pagerState.animateScrollToPage(
                it
              )
            }
          })

        HorizontalPager(
          state = pagerState, modifier = Modifier.weight(1f)
        ) { page ->
          when (AircraftTab.entries[page]) {
            AircraftTab.OVERVIEW -> OverviewTab(
              state = state,
              onAction = onAction,
              onViewSquawksTab = {
                coroutineScope.launch {
                  pagerState.animateScrollToPage(
                    AircraftTab.SQUAWKS.ordinal
                  )
                }
              },
              onViewLogsTab = {
                coroutineScope.launch {
                  pagerState.animateScrollToPage(
                    AircraftTab.LOGS.ordinal
                  )
                }
              },
              onMutationAction = onMutationAction,
            )

            AircraftTab.SQUAWKS -> SquawkTab(
              state = state,
              onAction = onAction,
              onMutationAction = onMutationAction,
            )

            AircraftTab.TASKS -> MaintenanceTasksTab(
              state = state,
              onAction = onAction,
            )

            AircraftTab.LOGS -> LogsTab(
              aircraftId = state.aircraft.id,
              syncStates = state.syncStates,
              onNavigateToAddLog = onMutationAction?.let { mutate ->
                { mutate(AircraftOverviewAction.AddLogClick(state.aircraft.id)) }
              },
              onNavigateToEditLog = onMutationAction?.let { mutate ->
                { logId ->
                  mutate(
                    AircraftOverviewAction.EditLogClick(
                      state.aircraft.id, logId
                    )
                  )
                }
              },
              onTaskClick = { taskId ->
                onAction(AircraftOverviewAction.TaskFromLogClick(taskId))
                coroutineScope.launch {
                  pagerState.animateScrollToPage(
                    AircraftTab.TASKS.ordinal
                  )
                }
              },
              attachmentsAvailable = attachmentsAvailable,
            )
          }
        }
      }
    }
  }
}
