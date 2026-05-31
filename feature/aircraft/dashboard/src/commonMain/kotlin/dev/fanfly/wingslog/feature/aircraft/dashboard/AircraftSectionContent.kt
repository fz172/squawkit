package dev.fanfly.wingslog.feature.aircraft.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.shell.ShellSection
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.LogsTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.MaintenanceTasksTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.OverviewTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.SquawkTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewViewModel
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.tasks.viewing.DeleteTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.viewing.TaskDetailSheet
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Host entry point for the adaptive shell's section bodies (M3): maps a [ShellSection] (+ optional
 * ambient [aircraftId]) to the right content. Both hosts (`AppEntry`, `WebApp`) call this from the
 * shell's `sectionContent` slot.
 *
 * - [ShellSection.SETTINGS] → a placeholder with a path to the real settings ([onOpenSettings]); the
 *   full settings screen is embedded in a later milestone.
 * - per-aircraft sections → [AircraftSectionContent], or an empty state when no aircraft exists.
 */
@Composable
fun ShellSectionBody(
  section: ShellSection,
  aircraftId: String?,
  navController: NavController,
  onNavigateToSection: (ShellSection) -> Unit,
  onOpenSettings: () -> Unit,
) {
  when {
    section == ShellSection.SETTINGS -> SettingsSectionPlaceholder(onOpenSettings)
    aircraftId != null -> AircraftSectionContent(
      aircraftId = aircraftId,
      section = section,
      navController = navController,
      onNavigateToSection = onNavigateToSection,
    )

    else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Add an aircraft to get started", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun SettingsSectionPlaceholder(onOpenSettings: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
  ) {
    Text("Settings", style = MaterialTheme.typography.headlineSmall)
    Button(onClick = onOpenSettings) { Text("Open settings") }
  }
}

/**
 * Renders the content of a single adaptive-shell [ShellSection] for a given aircraft (M3).
 *
 * Reuses the existing per-tab composables ([OverviewTab], [MaintenanceTasksTab], [LogsTab],
 * [SquawkTab]) but drives them from an [AircraftOverviewViewModel] scoped to the ambient
 * [aircraftId] (via Koin parameters, keyed per aircraft) rather than a navigation argument. A single
 * `onAction` wrapper intercepts every navigation action and drives [navController] directly, while
 * state actions fall through to the ViewModel — so add/edit for tasks, logs, squawks, and the
 * aircraft all use one deterministic path (no cross-ViewModel event relay). Cross-section jumps
 * (overview → squawks, log → task) are surfaced via [onNavigateToSection] so the shell can switch
 * sections.
 *
 * [ShellSection.SETTINGS] is global and handled by the host, not here.
 */
@Composable
fun AircraftSectionContent(
  aircraftId: String,
  section: ShellSection,
  navController: NavController,
  onNavigateToSection: (ShellSection) -> Unit = {},
) {
  val viewModel: AircraftOverviewViewModel =
    koinViewModel(key = aircraftId, parameters = { parametersOf(aircraftId) })
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val attachmentOpener: AttachmentOpener = koinInject()
  val coroutineScope = rememberCoroutineScope()
  var taskSheetOpenError by remember(aircraftId) { mutableStateOf<String?>(null) }

  // Single navigation entry point: intercept the navigation actions and drive the host navController
  // directly; delegate every other (state) action to the ViewModel. This keeps add/edit for tasks,
  // logs, squawks, and the aircraft on one deterministic path and removes the cross-ViewModel event
  // relay that previously dropped log navigation. Edit actions dismiss their detail overlay first so
  // it doesn't float above the pushed screen.
  val onAction: (AircraftOverviewAction) -> Unit =
    remember(viewModel, navController, aircraftId) {
      { action ->
        when (action) {
          is AircraftOverviewAction.AddLogClick ->
            navController.navigate(Screen.AddMaintenanceLog.createRoute(aircraftId))

          is AircraftOverviewAction.EditLogClick ->
            navController.navigate(Screen.EditMaintenanceLog.createRoute(aircraftId, action.logId))

          is AircraftOverviewAction.AddTaskClick ->
            navController.navigate(Screen.AddMaintenanceTask.createRoute(aircraftId))

          is AircraftOverviewAction.EditTaskClick -> {
            viewModel.onAction(AircraftOverviewAction.DismissTaskDetail)
            navController.navigate(Screen.EditMaintenanceTask.createRoute(aircraftId, action.cardId))
          }

          is AircraftOverviewAction.AddSquawkClick ->
            navController.navigate(Screen.AddSquawk.createRoute(aircraftId))

          is AircraftOverviewAction.EditSquawkClick -> {
            viewModel.onAction(AircraftOverviewAction.DismissSquawkDetail)
            navController.navigate(Screen.EditSquawk.createRoute(aircraftId, action.squawkId))
          }

          is AircraftOverviewAction.EditClick ->
            navController.navigate(Screen.EditAircraft.createRoute(aircraftId))

          AircraftOverviewAction.BackClick -> Unit

          else -> viewModel.onAction(action)
        }
      }
    }

  when (val state = uiState) {
    AircraftOverviewUiState.Loading ->
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }

    AircraftOverviewUiState.Error ->
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Couldn't load this aircraft")
      }

    is AircraftOverviewUiState.Success -> {
      when (section) {
        ShellSection.DASHBOARD -> OverviewTab(
          state = state,
          onAction = onAction,
          onViewSquawksTab = { onNavigateToSection(ShellSection.SQUAWKS) },
          onViewLogsTab = { onNavigateToSection(ShellSection.LOGS) },
          onMutationAction = onAction,
        )

        ShellSection.TASKS -> MaintenanceTasksTab(
          state = state,
          onAction = onAction,
          // The shell top bar already shows the section title; avoid duplicating it.
          showHeader = false,
        )

        ShellSection.SQUAWKS -> SquawkTab(
          state = state,
          onAction = onAction,
          onMutationAction = onAction,
          // The shell top bar already shows the section title; avoid duplicating it.
          showHeader = false,
        )

        ShellSection.LOGS -> LogsTab(
          aircraftId = aircraftId,
          syncStates = state.syncStates,
          // Route through the same onAction wrapper as every other section, which navigates directly.
          onNavigateToAddLog = { onAction(AircraftOverviewAction.AddLogClick(aircraftId)) },
          onNavigateToEditLog = { logId ->
            onAction(AircraftOverviewAction.EditLogClick(aircraftId, logId))
          },
          onTaskClick = { taskId ->
            onAction(AircraftOverviewAction.TaskFromLogClick(taskId))
            onNavigateToSection(ShellSection.TASKS)
          },
          attachmentsAvailable = state.attachmentEnabled,
        )

        ShellSection.SETTINGS -> Unit
      }

      // Task detail + delete confirmation overlays. SquawkTab and LogsTab render their own detail
      // overlays; the task detail lives at this level in the legacy screen too (it can be opened
      // from the Tasks tab or jumped to from a log), so render it here for the shell path.
      state.selectedTask?.let { selectedTask ->
        TaskDetailSheet(
          cardWithStatus = selectedTask,
          logs = state.logsForSelectedTask,
          onDismiss = {
            taskSheetOpenError = null
            onAction(AircraftOverviewAction.DismissTaskDetail)
          },
          onEditClick = {
            onAction(AircraftOverviewAction.EditTaskClick(aircraftId, selectedTask.card.id))
          },
          onAttachmentTap = { attachment ->
            taskSheetOpenError = null
            val openFlow = attachmentOpener.open(attachment)
            coroutineScope.launch {
              openFlow.collect { openState ->
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
    }
  }
}
