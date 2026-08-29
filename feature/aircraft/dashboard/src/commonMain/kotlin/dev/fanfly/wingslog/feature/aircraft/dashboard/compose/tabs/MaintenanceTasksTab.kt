package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalNavPillClearance
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.ComplianceSection
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.ThingOverviewUiState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable
fun MaintenanceTasksTab(
  state: ThingOverviewUiState.Success,
  onAction: (AircraftOverviewAction) -> Unit,
  /** Jumped-to task (from a log's Affected Tasks): switch to its sub-view and scroll to it. */
  scrollToTaskId: String? = null,
  showHeader: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  var showComplied by rememberSaveable { mutableStateOf(false) }
  val analytics = LocalAnalytics.current

  // Jump-to-task from a log: switch to the sub-view holding the target, then scroll it into view.
  // See SquawkTab for the root-coordinate offset scheme.
  var contentTopY by remember { mutableStateOf(0f) }
  var targetCardY by remember(scrollToTaskId) { mutableStateOf<Float?>(null) }
  // null = not found in either list yet, true = in history, false = active — three states so a
  // not-yet-synced tap and a status flip both re-trigger below (see SquawkTab for why: a tapped
  // notification can arrive and be acted on before the local sync pull carrying the very status
  // change it announced has landed).
  val taskInHistory: Boolean? = scrollToTaskId?.let { id ->
    when {
      state.completedTasks.any { it.card.id == id } -> true
      state.activeTasks.any { it.card.id == id } -> false
      else -> null
    }
  }
  LaunchedEffect(scrollToTaskId, taskInHistory) {
    val inHistory = taskInHistory ?: return@LaunchedEffect
    showComplied = inHistory
    // Reset on the re-run too — the target card just moved between sub-views, so its old on-screen
    // position no longer means anything.
    targetCardY = null
    val cardY = snapshotFlow { targetCardY }.filterNotNull()
      .first()
    scrollState.animateScrollTo(
      (scrollState.value + (cardY - contentTopY)).roundToInt()
        .coerceAtLeast(0)
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .onGloballyPositioned { contentTopY = it.positionInRoot().y }
      .padding(horizontal = Spacing.screenPadding)
      // Clear the floating pill this content now scrolls beneath (0 on non-compact tiers).
      .padding(bottom = LocalNavPillClearance.current),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
  ) {
    Spacer(Modifier.height(Spacing.medium))

    ComplianceSection(
      activeTasks = state.activeTasks,
      completedTasks = state.completedTasks,
      showComplied = showComplied,
      onToggleComplied = {
        showComplied = it
        analytics.logScreenView("shell/tasks/${if (it) "complied" else "active"}")
      },
      onCardClick = { onAction(AircraftOverviewAction.TaskCardClick(it)) },
      scrollTargetId = scrollToTaskId,
      onTargetPositioned = { targetCardY = it },
      showHeader = showHeader,
    )

    Spacer(Modifier.height(Spacing.buttonHeight + Spacing.screenPadding))
  }
}
