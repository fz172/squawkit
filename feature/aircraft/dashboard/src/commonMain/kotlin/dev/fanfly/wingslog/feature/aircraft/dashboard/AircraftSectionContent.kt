package dev.fanfly.wingslog.feature.aircraft.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.core.ui.theme.Spacing
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.core.sharedassets.generated.resources.empty_add_aircraft
import wingslog.feature.aircraft.dashboard.generated.resources.aircraft_load_error
import wingslog.feature.logs.sharedassets.generated.resources.add_log
import wingslog.feature.squawk.sharedassets.generated.resources.add_squawk
import wingslog.feature.tasks.sharedassets.generated.resources.add_task
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.aircraft.dashboard.generated.resources.Res as DashboardRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as LogsRes
import wingslog.feature.squawk.sharedassets.generated.resources.Res as SquawkRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as TasksRes

/**
 * Host entry point for the adaptive shell's **per-aircraft** section bodies: maps a [dev.fanfly.wingslog.core.ui.adaptive.ShellSection]
 * (+ optional ambient [aircraftId]) to the right content. Both hosts (`AppEntry`, `WebApp`) call this
 * from the shell's `sectionContent` slot for everything except [dev.fanfly.wingslog.core.ui.adaptive.ShellSection.SETTINGS], which is
 * global and rendered by the host directly (it depends on `feature:settings`).
 *
 * - per-aircraft sections → [AircraftSectionContent], or an empty state when no aircraft exists.
 */
@Composable
fun ShellSectionBody(
  section: ShellSection,
  aircraftId: String?,
  navController: NavController,
  onNavigateToSection: (ShellSection) -> Unit,
) {
  if (aircraftId != null) {
    AircraftSectionContent(
      aircraftId = aircraftId,
      section = section,
      navController = navController,
      onNavigateToSection = onNavigateToSection,
    )
  } else {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      Text(
        stringResource(CoreRes.string.empty_add_aircraft),
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}

/**
 * The per-section floating action button for the adaptive shell's `sectionFab` slot: Add Squawk /
 * Task / Log for the matching section, navigating into the same add screens that
 * [AircraftSectionContent]'s `onAction` uses. Dashboard has no primary add action and Settings is
 * global, so neither shows a FAB. Returns nothing until an aircraft is selected — the add routes are
 * all aircraft-scoped.
 *
 * Lives here (not in `core:ui`) because the shell cannot depend on the feature add-screen routes; it
 * is rendered inside the shell's own Scaffold FAB slot so snackbars offset around it.
 */
@Composable
fun ShellSectionFab(
  section: ShellSection,
  aircraftId: String?,
  navController: NavController,
) {
  if (aircraftId == null) return
  when (section) {
    ShellSection.SQUAWKS ->
      SectionAddFab(
        label = stringResource(SquawkRes.string.add_squawk),
        onClick = {
          navController.navigate(
            Screen.AddSquawk.createRoute(
              aircraftId
            )
          )
        },
      )

    ShellSection.TASKS ->
      SectionAddFab(
        label = stringResource(TasksRes.string.add_task),
        onClick = {
          navController.navigate(
            Screen.AddMaintenanceTask.createRoute(
              aircraftId
            )
          )
        },
      )

    ShellSection.LOGS ->
      SectionAddFab(
        label = stringResource(LogsRes.string.add_log),
        onClick = {
          navController.navigate(
            Screen.AddMaintenanceLog.createRoute(
              aircraftId
            )
          )
        },
      )

    ShellSection.DASHBOARD, ShellSection.SETTINGS -> Unit
  }
}

@Composable
private fun SectionAddFab(label: String, onClick: () -> Unit) {
  ExtendedFloatingActionButton(
    onClick = onClick,
    icon = { Icon(Icons.Default.Add, contentDescription = null) },
    text = { Text(label) },
    // Nudge the FAB inward so it clears the wide-screen Logs table's right border. Applied to the
    // shared FAB so the position stays identical across the Squawks/Tasks/Logs sections.
    modifier = Modifier.padding(end = Spacing.medium),
  )
}

/**
 * Renders the content of a single adaptive-shell [dev.fanfly.wingslog.core.ui.adaptive.ShellSection] for a given aircraft (M3).
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
 * [dev.fanfly.wingslog.core.ui.adaptive.ShellSection.SETTINGS] is global and handled by the host, not here.
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
            navController.navigate(
              Screen.AddMaintenanceLog.createRoute(
                aircraftId
              )
            )

          is AircraftOverviewAction.EditLogClick ->
            navController.navigate(
              Screen.EditMaintenanceLog.createRoute(
                aircraftId,
                action.logId
              )
            )

          is AircraftOverviewAction.AddTaskClick ->
            navController.navigate(
              Screen.AddMaintenanceTask.createRoute(
                aircraftId
              )
            )

          is AircraftOverviewAction.EditTaskClick -> {
            viewModel.onAction(AircraftOverviewAction.DismissTaskDetail)
            navController.navigate(
              Screen.EditMaintenanceTask.createRoute(
                aircraftId,
                action.cardId
              )
            )
          }

          is AircraftOverviewAction.AddSquawkClick ->
            navController.navigate(Screen.AddSquawk.createRoute(aircraftId))

          is AircraftOverviewAction.EditSquawkClick -> {
            viewModel.onAction(AircraftOverviewAction.DismissSquawkDetail)
            navController.navigate(
              Screen.EditSquawk.createRoute(
                aircraftId,
                action.squawkId
              )
            )
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
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }

    AircraftOverviewUiState.Error ->
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Text(stringResource(DashboardRes.string.aircraft_load_error))
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
          onNavigateToAddLog = {
            onAction(
              AircraftOverviewAction.AddLogClick(
                aircraftId
              )
            )
          },
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
            onAction(
              AircraftOverviewAction.EditTaskClick(
                aircraftId,
                selectedTask.card.id
              )
            )
          },
          onAttachmentTap = { attachment ->
            taskSheetOpenError = null
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
