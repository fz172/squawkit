package dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import dev.fanfly.wingslog.feature.inspection.update.compose.DeleteInspectionConfirmDialog
import dev.fanfly.wingslog.feature.inspection.viewing.CriticalAlertsSection
import dev.fanfly.wingslog.feature.inspection.viewing.InspectionDetailSheet
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.back
import wingslog.feature.maintenance.sharedassets.generated.resources.Res
import wingslog.feature.maintenance.sharedassets.generated.resources.make_model_template
import wingslog.feature.maintenance.viewing.generated.resources.edit_aircraft
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.maintenance.viewing.generated.resources.Res as MaintenanceRes

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
  var showComplied by rememberSaveable { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior, title = {
          Column {
            Text(
              text = stringResource(
                Res.string.make_model_template, state.aircraft.make, state.aircraft.model
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
          IconButton(onClick = { onAction(AircraftOverviewAction.EditClick(state.aircraft.id)) }) {
            Icon(
              Icons.Default.Settings,
              contentDescription = stringResource(MaintenanceRes.string.edit_aircraft)
            )
          }
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
          ConfigurationCard(state.aircraft)
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
            LogOnboardingCard(
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