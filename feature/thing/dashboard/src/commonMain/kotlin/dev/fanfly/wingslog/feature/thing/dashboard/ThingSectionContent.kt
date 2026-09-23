package dev.fanfly.wingslog.feature.thing.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.adaptive.listdetail.ListDetailSection
import dev.fanfly.wingslog.core.ui.adaptive.shell.LocalSnackbarHostState
import dev.fanfly.wingslog.core.ui.adaptive.shell.ShellSection
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.core.ui.common.compose.SkeletonList
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogSectionContent
import dev.fanfly.wingslog.feature.tasks.viewing.DeleteTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.viewing.SkipTaskConfirmDialog
import dev.fanfly.wingslog.feature.thing.dashboard.logs.LogsTab
import dev.fanfly.wingslog.feature.thing.dashboard.overview.OverviewTab
import dev.fanfly.wingslog.feature.thing.dashboard.squawks.SquawkTab
import dev.fanfly.wingslog.feature.thing.dashboard.tasks.MaintenanceTasksTab
import dev.fanfly.wingslog.id.ThingId
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.feature.thing.dashboard.generated.resources.thing_load_error
import wingslog.feature.thing.dashboard.generated.resources.Res as DashboardRes

/**
 * Renders the content of a single adaptive-shell [dev.fanfly.wingslog.core.ui.adaptive.shell.ShellSection] for a given thing (M3).
 *
 * Reuses the existing per-tab composables ([OverviewTab], [MaintenanceTasksTab], [LogsTab],
 * [SquawkTab]) but drives them from an [ThingOverviewViewModel] scoped to the ambient
 * [thingId] (via Koin parameters, keyed per thing) rather than a navigation argument. A single
 * `onAction` wrapper intercepts every navigation action and drives [navController] directly, while
 * state actions fall through to the ViewModel — so add/edit for tasks, logs, squawks, and the
 * thing all use one deterministic path (no cross-ViewModel event relay). Cross-section jumps
 * (overview → squawks, log → task) are surfaced via [onNavigateToSection] so the shell can switch
 * sections.
 *
 * [dev.fanfly.wingslog.core.ui.adaptive.shell.ShellSection.SETTINGS] is global and handled by the host, not here.
 */
@Composable
fun ThingSectionContent(
  thingId: String,
  section: ShellSection,
  navController: NavController,
  onNavigateToSection: (ShellSection) -> Unit = {},
  onJumpToRecord: (RecordJump) -> Unit = {},
  /** See [ShellSectionBody]'s parameter of the same name. */
  scrollToRecordId: String? = null,
  onScrollTargetConsumed: () -> Unit = {},
  onLinkAccount: () -> Unit = {},
) {
  val viewModel: ThingOverviewViewModel =
    koinViewModel(key = thingId, parameters = { parametersOf(thingId) })
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val commentThread by viewModel.commentThread.collectAsStateWithLifecycle()
  // Due status depends on the wall clock, not just on stored data, so recompute it whenever the
  // dashboard comes back into view — otherwise an app resumed the next day still shows yesterday's
  // status. Common to all three hosts: UIKit foreground on iOS, document.visibilitychange on web.
  LifecycleResumeEffect(thingId) {
    viewModel.onResumed()
    onPauseOrDispose { }
  }
  // A quick action runs inside the shell entry, so the cross-screen back-stack channel is the
  // wrong shape for its snackbar; the shell provides its host here instead (design §7). Only the
  // message events land here — navigation is driven from the onAction wrapper below.
  val snackbarHostState = LocalSnackbarHostState.current
  var pendingMessage by remember(thingId) { mutableStateOf<UiText?>(null) }
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      if (event is ThingOverviewEvent.ShowMessage) pendingMessage =
        event.message
    }
  }
  // Resolved in composition, because a UiText needs the resource table; a host that provided no
  // snackbar (a preview) drops it silently.
  val messageText = pendingMessage?.asString()
  LaunchedEffect(messageText) {
    val text = messageText ?: return@LaunchedEffect
    pendingMessage = null
    snackbarHostState?.showSnackbar(text)
  }
  // The log the Logs section should scroll to, handed down by the host (see [onJumpToRecord]). It
  // is cleared only while the Logs tab is OFF
  // screen (see below): toggling it back to null while LogsTab is mounted remounts that tab and drops
  // its list ViewModel and scroll position, which would bounce the list back to the top.
  var pendingLogScrollTarget by remember(thingId) {
    mutableStateOf<String?>(
      null
    )
  }
  LaunchedEffect(section) {
    if (section != ShellSection.LOGS) pendingLogScrollTarget = null
  }
  // Same pattern for jumping from a log's linked tasks/squawks to the item in its list: set on tap,
  // consumed by the Tasks/Squawks section (which switches to the right sub-view and scrolls to it),
  // cleared once that section is left.
  var pendingTaskScrollTarget by remember(thingId) {
    mutableStateOf<String?>(
      null
    )
  }
  LaunchedEffect(section) {
    if (section != ShellSection.TASKS) pendingTaskScrollTarget = null
  }
  var pendingSquawkScrollTarget by remember(thingId) {
    mutableStateOf<String?>(
      null
    )
  }
  LaunchedEffect(section) {
    if (section != ShellSection.SQUAWKS) pendingSquawkScrollTarget = null
  }
  // A record open in one section does not follow the user to the next: the task and squawk
  // selections live in the overview ViewModel and would otherwise still be there — as a pane
  // beside the list, or a sheet over another section — when the user came back. The log
  // selection is the Logs tab's own and is cleared where that tab starts.
  LaunchedEffect(section) {
    viewModel.onAction(ThingOverviewAction.DismissTaskDetail)
    viewModel.onAction(ThingOverviewAction.DismissSquawkDetail)
  }

  // A jump requested by the host (a tapped urgency notification) feeds the very same per-section
  // state as an in-app jump, so both reach the list — and the highlight — by one path. The host has
  // already switched [section] to match the record's kind, which is what says which list to aim at.
  // Ordered after the clear-on-leave effects above so it wins when both run for the same section.
  LaunchedEffect(scrollToRecordId, section) {
    val id = scrollToRecordId ?: return@LaunchedEffect
    when (section) {
      ShellSection.TASKS -> pendingTaskScrollTarget = id
      ShellSection.SQUAWKS -> pendingSquawkScrollTarget = id
      ShellSection.LOGS -> pendingLogScrollTarget = id
      // Nothing to scroll to in a section with no record list; drop it rather than stranding it.
      ShellSection.DASHBOARD, ShellSection.DATA_LOGS, ShellSection.SETTINGS -> Unit
    }
    onScrollTargetConsumed()
  }

  val onAction = rememberSectionActionHandler(viewModel, navController, thingId)

  when (val state = uiState) {
    // The state is assembled from every flow at once, so Success never carries an empty list that
    // is merely unloaded — this branch *is* the squawk and task tabs' loading state.
    ThingOverviewUiState.Loading -> when (section) {
      ShellSection.SQUAWKS, ShellSection.TASKS, ShellSection.LOGS -> SkeletonList()
      ShellSection.DATA_LOGS -> SkeletonList(showFilterBar = false)
      ShellSection.DASHBOARD -> DashboardSkeleton()
      ShellSection.SETTINGS -> Unit
    }

    // Every section, not just the dashboard: which sections a Thing has is itself template-declared,
    // so an uninterpretable template makes all four meaningless (design §6.2).
    is ThingOverviewUiState.Degraded -> DegradedThingContent(state.thing)

    ThingOverviewUiState.Error ->
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Text(
          stringResource(
            DashboardRes.string.thing_load_error,
            LocalThingLexicon.current.thingNoun.singular,
          )
        )
      }

    is ThingOverviewUiState.Success -> {
      val taskDetail = taskDetailFor(
        state = state,
        thingId = thingId,
        onAction = onAction,
        commentThread = commentThread,
      )

      when (section) {
        ShellSection.DASHBOARD -> OverviewTab(
          state = state,
          onAction = onAction,
          onViewSquawksTab = { onNavigateToSection(ShellSection.SQUAWKS) },
          onViewLogsTab = { onNavigateToSection(ShellSection.LOGS) },
          onMutationAction = onAction,
        )

        ShellSection.TASKS -> ListDetailSection(detail = taskDetail) {
          MaintenanceTasksTab(
            state = state,
            onAction = onAction,
            scrollToTaskId = pendingTaskScrollTarget,
            // The shell top bar already shows the section title; avoid duplicating it.
            showHeader = false,
          )
        }

        ShellSection.SQUAWKS -> SquawkTab(
          state = state,
          onAction = onAction,
          onMutationAction = onAction,
          onLogClick = { logId ->
            onAction(ThingOverviewAction.DismissSquawkDetail)
            onJumpToRecord(RecordJump(ShellSection.LOGS, logId))
          },
          onOpenDataLog = { dataLogId ->
            onAction(ThingOverviewAction.OpenDataLogClick(thingId, dataLogId))
          },
          scrollToSquawkId = pendingSquawkScrollTarget,
          commentThread = commentThread,
          // The shell top bar already shows the section title; avoid duplicating it.
          showHeader = false,
        )

        ShellSection.LOGS -> LogsTab(
          thingId = thingId,
          syncStates = state.syncStates,
          dataLogs = state.dataLogs,
          // Route through the same onAction wrapper as every other section, which navigates directly.
          onNavigateToAddLog = {
            onAction(
              ThingOverviewAction.AddLogClick(
                thingId
              )
            )
          },
          onNavigateToEditLog = { logId ->
            onAction(ThingOverviewAction.EditLogClick(thingId, logId))
          },
          onOpenDataLog = { dataLogId ->
            onAction(ThingOverviewAction.OpenDataLogClick(thingId, dataLogId))
          },
          onTaskClick = { taskId ->
            onJumpToRecord(RecordJump(ShellSection.TASKS, taskId))
          },
          onSquawkClick = { squawkId ->
            onJumpToRecord(RecordJump(ShellSection.SQUAWKS, squawkId))
          },
          scrollToLogId = pendingLogScrollTarget,
        )

        ShellSection.DATA_LOGS -> DataLogSectionContent(
          thingId = ThingId(thingId),
          onOpen = { id ->
            navController.navigate(
              Screen.DataLogViewer.createRoute(
                ThingId(thingId),
                id
              )
            )
          },
          onLinkAccount = onLinkAccount,
        )

        ShellSection.SETTINGS -> Unit
      }

      // Over any section but Tasks, which hosts it itself.
      if (section != ShellSection.TASKS) taskDetail?.invoke()

      // Here rather than in the Tasks tab: the sheet that raises it can be open over any section.
      if (state.skippingTaskId != null) {
        SkipTaskConfirmDialog(
          onConfirm = { onAction(ThingOverviewAction.ConfirmSkipTask) },
          onDismiss = { onAction(ThingOverviewAction.CancelSkipTask) },
        )
      }

      state.deletingTaskId?.let { deletingId ->
        val title = (state.activeTasks + state.completedTasks)
          .find { it.card.id == deletingId }?.card?.title ?: ""
        DeleteTaskConfirmDialog(
          title = title,
          onConfirm = { onAction(ThingOverviewAction.ConfirmDeleteTask) },
          onDismiss = { onAction(ThingOverviewAction.CancelDeleteTask) },
        )
      }
    }
  }
}
