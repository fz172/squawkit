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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.AircraftDashboardTabRow
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.LogsTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.MaintenanceTasksTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs.OverviewTab
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.tasks.update.compose.DeleteTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.viewing.TaskDetailSheet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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

    state.selectedInspection?.let { selectedInspection ->
      TaskDetailSheet(
        cardWithStatus = selectedInspection,
        logs = state.logsForSelectedInspection,
        onDismiss = { onAction(AircraftOverviewAction.DismissInspectionDetail) },
        onEditClick = {
          onAction(
            AircraftOverviewAction.EditInspectionClick(
              state.aircraft.id,
              selectedInspection.card.id
            )
          )
        },
        onAttachmentTap = { onAction(AircraftOverviewAction.AttachmentTap(it)) },
        downloadingIds = state.downloadingIds,
      )
    }

    state.deletingInspectionId?.let { deletingInspectionId ->
      val title = (state.activeInspections + state.compliedInspections)
        .find { it.card.id == deletingInspectionId }?.card?.title ?: ""
      DeleteTaskConfirmDialog(
        inspectionTitle = title,
        onConfirm = { onAction(AircraftOverviewAction.ConfirmDeleteInspection) },
        onDismiss = { onAction(AircraftOverviewAction.CancelDeleteInspection) },
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
          0 -> OverviewTab(state = state, onAction = onAction)
          1 -> MaintenanceTasksTab(state = state, onAction = onAction)
          2 -> LogsTab(
            aircraftId = state.aircraft.id,
            onNavigateToAddLog = { onAction(AircraftOverviewAction.AddLogClick(state.aircraft.id)) },
            onNavigateToEditLog = { logId ->
              onAction(AircraftOverviewAction.EditLogClick(state.aircraft.id, logId))
            }
          )
        }
      }
    }
  }
}
