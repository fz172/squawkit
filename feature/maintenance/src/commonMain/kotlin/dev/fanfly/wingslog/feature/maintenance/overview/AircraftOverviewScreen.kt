package dev.fanfly.wingslog.feature.maintenance.overview

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
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentOpener
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus
import dev.fanfly.wingslog.feature.inspection.update.compose.DeleteInspectionConfirmDialog
import dev.fanfly.wingslog.feature.inspection.viewing.CriticalAlertsSection
import dev.fanfly.wingslog.feature.inspection.viewing.InspectionDetailSheet
import dev.fanfly.wingslog.feature.maintenance.overview.compose.ComplianceSection
import dev.fanfly.wingslog.feature.maintenance.overview.compose.ConfigurationCard
import dev.fanfly.wingslog.feature.maintenance.overview.compose.LogStatsSection
import dev.fanfly.wingslog.feature.maintenance.overview.data.AircraftOverviewEvent
import dev.fanfly.wingslog.feature.maintenance.overview.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.maintenance.overview.data.AircraftOverviewViewModel
import dev.fanfly.wingslog.feature.maintenance.overview.data.LogStats
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.back
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.delete
import wingslog.core.ui.generated.resources.error_occurred
import wingslog.feature.maintenance.generated.resources.add_first_maintenance_log
import wingslog.feature.maintenance.generated.resources.add_log
import wingslog.feature.maintenance.generated.resources.delete_aircraft
import wingslog.feature.maintenance.generated.resources.log_details
import wingslog.feature.maintenance.generated.resources.make_model_template
import wingslog.feature.maintenance.generated.resources.this_action_cannot_be_undone
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.maintenance.generated.resources.Res as MaintenanceRes


@Composable
fun AircraftOverviewScreen(
  navController: NavController,
  viewModel: AircraftOverviewViewModel = koinViewModel(),
  attachmentOpener: AttachmentOpener = koinInject(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()


  val errorOccurredMessage = cmpStringResource(CoreRes.string.error_occurred)

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is AircraftOverviewEvent.NavigateBack -> navController.popBackStack()
        is AircraftOverviewEvent.ShowError -> {
          val message = event.message ?: errorOccurredMessage
          snackbarHostState.showSnackbar(message)
        }
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

  val successState = uiState as? AircraftOverviewUiState.Success

  AircraftOverviewContent(
    aircraft = successState?.aircraft,
    logStats = successState?.logStats,
    activeInspections = successState?.activeInspections ?: emptyList(),
    compliedInspections = successState?.compliedInspections ?: emptyList(),
    selectedInspection = successState?.selectedInspection,
    logsForSelectedInspection = successState?.logsForSelectedInspection ?: emptyList(),
    deletingInspectionId = successState?.deletingInspectionId,
    inspectionCardTitleForDelete = (successState?.activeInspections ?: emptyList())
      .find { it.card.id == successState?.deletingInspectionId }?.card?.title
      ?: (successState?.compliedInspections ?: emptyList())
        .find { it.card.id == successState?.deletingInspectionId }?.card?.title
      ?: "",
    snackbarHostState = snackbarHostState,
    onBackClick = { navController.popBackStack() },
    onEditClick = { aircraftId -> navController.navigate("edit_aircraft/$aircraftId") },
    onDeleteConfirm = { viewModel.deleteAircraft() },
    onLogDetailsClick = { aircraftId -> navController.navigate("maintenance_logs/$aircraftId") },
    onAddLogClick = { aircraftId -> navController.navigate("maintenance_log_create/$aircraftId") },
    onAddInspectionClick = { aircraftId -> navController.navigate("maintenance_inspection_create/$aircraftId") },
    onInspectionCardClick = { card -> viewModel.showInspectionDetail(card) },
    onDismissInspectionDetail = { viewModel.hideInspectionDetail() },
    onEditInspectionClick = { aircraftId, cardId -> navController.navigate("maintenance_inspection_edit/$aircraftId/$cardId") },
    onCancelDeleteInspection = { viewModel.cancelDeleteInspection() },
    onConfirmDeleteInspection = { viewModel.confirmDeleteInspection() },
    onAttachmentTap = { attachment -> coroutineScope.launch { attachmentOpener.open(attachment).collect {} } },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftOverviewContent(
  aircraft: Aircraft?,
  logStats: LogStats?,
  activeInspections: List<InspectionCardWithStatus>,
  compliedInspections: List<InspectionCardWithStatus>,
  selectedInspection: InspectionCardWithStatus? = null,
  logsForSelectedInspection: List<dev.fanfly.wingslog.aircraft.MaintenanceLog> = emptyList(),
  deletingInspectionId: String? = null,
  inspectionCardTitleForDelete: String = "",
  snackbarHostState: SnackbarHostState,
  onBackClick: () -> Unit,
  onEditClick: (String) -> Unit,
  onDeleteConfirm: () -> Unit,
  onLogDetailsClick: (String) -> Unit,
  onAddLogClick: (String) -> Unit = {},
  onAddInspectionClick: (String) -> Unit = {},
  onInspectionCardClick: (InspectionCardWithStatus) -> Unit = {},
  onDismissInspectionDetail: () -> Unit = {},
  onEditInspectionClick: (String, String) -> Unit = { _, _ -> },
  onCancelDeleteInspection: () -> Unit = {},
  onConfirmDeleteInspection: () -> Unit = {},
  onAttachmentTap: (Attachment) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var showComplied by rememberSaveable { mutableStateOf(false) }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(cmpStringResource(MaintenanceRes.string.delete_aircraft)) },
      text = { Text(cmpStringResource(MaintenanceRes.string.this_action_cannot_be_undone)) },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteConfirm()
            showDeleteDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Text(cmpStringResource(CoreRes.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(cmpStringResource(CoreRes.string.cancel))
        }
      })
  }

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior, title = {
          if (aircraft != null) {
            Column {
              Text(
                text = cmpStringResource(
                  MaintenanceRes.string.make_model_template, aircraft.make, aircraft.model
                )
              )
              Text(
                text = aircraft.tail_number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }, navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = cmpStringResource(CoreRes.string.back)
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
    if (selectedInspection != null) {
      InspectionDetailSheet(
        cardWithStatus = selectedInspection,
        logs = logsForSelectedInspection,
        onDismiss = onDismissInspectionDetail,
        onEditClick = {
          if (aircraft != null) {
            onEditInspectionClick(aircraft.id, selectedInspection.card.id)
          }
        },
        onAttachmentTap = onAttachmentTap,
      )
    }

    // Delete inspection confirm dialog
    if (deletingInspectionId != null) {
      DeleteInspectionConfirmDialog(
        inspectionTitle = inspectionCardTitleForDelete,
        onConfirm = onConfirmDeleteInspection,
        onDismiss = onCancelDeleteInspection,
      )
    }

    if (aircraft != null) {
      Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues)
      ) {
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
          verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
        ) {

          // --- Configuration Section ---
          Column(modifier = Modifier.padding(horizontal = Spacing.screenPadding)) {
            ConfigurationCard(aircraft, onEditClick = onEditClick)
          }

          // --- Critical Alerts Section ---
          val overdueInspections =
            activeInspections.filter { it.dueStatus.status == DueStatus.OVERDUE }
          if (overdueInspections.isNotEmpty()) {
            CriticalAlertsSection(
              overdueInspections = overdueInspections,
              onCardClick = onInspectionCardClick,
              modifier = Modifier.padding(horizontal = Spacing.screenPadding)
            )
          }

          // --- Log Stats Section ---
          logStats?.let { stats ->
            if (stats.total == 0L) {
              dev.fanfly.wingslog.feature.maintenance.overview.compose.LogOnboardingCard(
                onAddLogClick = { onAddLogClick(aircraft.id) },
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
            activeInspections = activeInspections,
            compliedInspections = compliedInspections,
            showComplied = showComplied,
            onToggleComplied = { showComplied = it },
            onAddClick = { onAddInspectionClick(aircraft.id) },
            onCardClick = onInspectionCardClick,
            modifier = Modifier.padding(horizontal = Spacing.screenPadding),
          )

          Spacer(Modifier.height(Spacing.massive)) // Clearance for the floating bottom bar
        }

        // Floating Bottom Bar
        LogDetailsBottomBar(
          aircraft = aircraft,
          logStats = logStats,
          modifier = Modifier.align(Alignment.BottomCenter),
          onLogDetailsClick = onLogDetailsClick,
          onAddLogClick = onAddLogClick
        )
      }
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
      primaryLabel = if (hasLogs) cmpStringResource(MaintenanceRes.string.add_log)
      else cmpStringResource(MaintenanceRes.string.add_first_maintenance_log),
      onSecondaryClick = if (hasLogs) ({ onLogDetailsClick(aircraft.id) }) else null,
      secondaryLabel = cmpStringResource(MaintenanceRes.string.log_details),
    )
  }
}

