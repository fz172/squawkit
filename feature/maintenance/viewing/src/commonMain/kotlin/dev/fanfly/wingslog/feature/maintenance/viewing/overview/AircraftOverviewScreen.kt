package dev.fanfly.wingslog.feature.maintenance.viewing.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import dev.fanfly.wingslog.feature.inspection.update.compose.DeleteInspectionConfirmDialog
import dev.fanfly.wingslog.feature.inspection.viewing.CriticalAlertsSection
import dev.fanfly.wingslog.feature.inspection.viewing.InspectionDetailSheet
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.ComplianceSection
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.ConfigurationCard
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.LogStatsSection
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewEvent
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewViewModel
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.LogStats
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.back
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.delete
import wingslog.core.ui.generated.resources.error_occurred
import wingslog.feature.maintenance.sharedassets.generated.resources.add_first_maintenance_log
import wingslog.feature.maintenance.sharedassets.generated.resources.add_log
import wingslog.feature.maintenance.sharedassets.generated.resources.delete_aircraft
import wingslog.feature.maintenance.sharedassets.generated.resources.make_model_template
import wingslog.feature.maintenance.sharedassets.generated.resources.this_action_cannot_be_undone
import wingslog.feature.maintenance.viewing.generated.resources.log_details
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.maintenance.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.maintenance.viewing.generated.resources.Res as MaintenanceRes


@Composable
fun AircraftOverviewScreen(
  navController: NavController,
  viewModel: AircraftOverviewViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  val errorOccurredMessage = stringResource(CoreRes.string.error_occurred)

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is AircraftOverviewEvent.NavigateBack -> navController.popBackStack()
        is AircraftOverviewEvent.ShowError -> {
          val message = event.message ?: errorOccurredMessage
          snackbarHostState.showSnackbar(message)
        }

        is AircraftOverviewEvent.NavigateToAddInspection ->
          navController.navigate("maintenance_inspection_create/${event.aircraftId}")

        is AircraftOverviewEvent.NavigateToAddLog ->
          navController.navigate("maintenance_log_create/${event.aircraftId}")

        is AircraftOverviewEvent.NavigateToEditAircraft ->
          navController.navigate("edit_aircraft/${event.aircraftId}")

        is AircraftOverviewEvent.NavigateToEditInspection ->
          navController.navigate("maintenance_inspection_edit/${event.aircraftId}/${event.cardId}")

        is AircraftOverviewEvent.NavigateToLogDetails ->
          navController.navigate("maintenance_logs/${event.aircraftId}")
      }
    }
  }

// Show success messages from child screens (maintenance log, inspection forms)
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  LaunchedEffect(navBackStackEntry) {
    val handle = navBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
    val message = handle.get<String>("success_message").orEmpty()
    if (message.isNotEmpty()) {
      coroutineScope.launch {
        snackbarHostState.showSnackbar(message)
      }
      handle.set("success_message", "")
    }
  }

  when (val state = uiState) {
    AircraftOverviewUiState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }

    AircraftOverviewUiState.Error -> {
      // Handle error state
    }

    is AircraftOverviewUiState.Success -> {
      AircraftOverviewContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftOverviewContent(
  state: AircraftOverviewUiState.Success,
  snackbarHostState: SnackbarHostState,
  onAction: (AircraftOverviewAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var showComplied by rememberSaveable { mutableStateOf(false) }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(SharedRes.string.delete_aircraft)) },
      text = { Text(stringResource(SharedRes.string.this_action_cannot_be_undone)) },
      confirmButton = {
        TextButton(
          onClick = {
            onAction(AircraftOverviewAction.DeleteConfirm)
            showDeleteDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Text(stringResource(CoreRes.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(CoreRes.string.cancel))
        }
      })
  }

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior, title = {
          Column {
            Text(
              text = stringResource(
                SharedRes.string.make_model_template, state.aircraft.make, state.aircraft.model
              )
            )
            Text(
              text = state.aircraft.tail_number,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }, navigationIcon = {
          IconButton(onClick = { onAction(AircraftOverviewAction.BackClick) }) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(CoreRes.string.back)
            )
          }
        }, actions = {
          // Actions moved to ConfigurationCard
        }, colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          scrolledContainerColor = MaterialTheme.colorScheme.background
        )
      )
    }) { paddingValues ->

    // Inspection detail bottom sheet
    state.selectedInspection?.let { selectedInspection ->
      InspectionDetailSheet(
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

    // Delete inspection confirm dialog
    state.deletingInspectionId?.let { deletingInspectionId ->
      val title = (state.activeInspections + state.compliedInspections)
        .find { it.card.id == deletingInspectionId }?.card?.title ?: ""
      DeleteInspectionConfirmDialog(
        inspectionTitle = title,
        onConfirm = { onAction(AircraftOverviewAction.ConfirmDeleteInspection) },
        onDismiss = { onAction(AircraftOverviewAction.CancelDeleteInspection) },
      )
    }

    Box(
      modifier = Modifier.fillMaxSize().padding(paddingValues)
    ) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
      ) {

        // --- Configuration Section ---
        Column(modifier = Modifier.padding(horizontal = Spacing.screenPadding)) {
          ConfigurationCard(
            state.aircraft,
            onEditClick = { onAction(AircraftOverviewAction.EditClick(it)) })
        }

        // --- Critical Alerts Section ---
        val overdueInspections =
          state.activeInspections.filter { it.dueStatus.status == DueStatus.OVERDUE }
        if (overdueInspections.isNotEmpty()) {
          CriticalAlertsSection(
            overdueInspections = overdueInspections,
            onCardClick = { onAction(AircraftOverviewAction.InspectionCardClick(it)) },
            modifier = Modifier.padding(horizontal = Spacing.screenPadding)
          )
        }

        // --- Log Stats Section ---
        state.logStats?.let { stats ->
          if (stats.total == 0L) {
            dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.LogOnboardingCard(
              onAddLogClick = { onAction(AircraftOverviewAction.AddLogClick(state.aircraft.id)) },
              modifier = Modifier.padding(horizontal = Spacing.screenPadding)
            )
          } else {
            LogStatsSection(
              stats = stats,
              modifier = Modifier.padding(horizontal = Spacing.screenPadding)
            )
          }
        }

        // --- Compliance & Inspections Section ---
        ComplianceSection(
          activeInspections = state.activeInspections,
          compliedInspections = state.compliedInspections,
          showComplied = showComplied,
          onToggleComplied = { showComplied = it },
          onAddClick = { onAction(AircraftOverviewAction.AddInspectionClick(state.aircraft.id)) },
          onCardClick = { onAction(AircraftOverviewAction.InspectionCardClick(it)) },
          modifier = Modifier.padding(horizontal = Spacing.screenPadding),
        )

        Spacer(Modifier.height(Spacing.massive)) // Clearance for the floating bottom bar
      }

      // Floating Bottom Bar
      LogDetailsBottomBar(
        aircraft = state.aircraft,
        logStats = state.logStats,
        modifier = Modifier.align(Alignment.BottomCenter),
        onLogDetailsClick = { onAction(AircraftOverviewAction.LogDetailsClick(it)) },
        onAddLogClick = { onAction(AircraftOverviewAction.AddLogClick(it)) }
      )
    }
  }
}

@Composable
fun LogDetailsBottomBar(
  aircraft: Aircraft?,
  logStats: LogStats?,
  modifier: Modifier = Modifier,
  onLogDetailsClick: (String) -> Unit,
  onAddLogClick: (String) -> Unit = {},
) {
  if (aircraft != null) {
    val hasLogs = (logStats?.total ?: 0L) > 0L
    BottomButtons(
      modifier = modifier,
      onPrimaryClick = { onAddLogClick(aircraft.id) },
      primaryLabel = if (hasLogs) stringResource(SharedRes.string.add_log)
      else stringResource(SharedRes.string.add_first_maintenance_log),
      onSecondaryClick = if (hasLogs) ({ onLogDetailsClick(aircraft.id) }) else null,
      secondaryLabel = stringResource(MaintenanceRes.string.log_details),
    )
  }
}

