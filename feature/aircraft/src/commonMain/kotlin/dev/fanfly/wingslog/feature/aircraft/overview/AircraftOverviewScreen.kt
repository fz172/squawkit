package dev.fanfly.wingslog.feature.aircraft.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.feature.aircraft.overview.compose.ConfigurationCard
import dev.fanfly.wingslog.feature.aircraft.overview.compose.InspectionCard
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewEvent
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewViewModel
import dev.fanfly.wingslog.feature.aircraft.overview.data.InspectionCardWithStatus
import dev.fanfly.wingslog.feature.aircraft.overview.data.LogStats
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.aircraft.generated.resources.add_inspection
import wingslog.feature.aircraft.generated.resources.airframe
import wingslog.feature.aircraft.generated.resources.airframe_time_label
import wingslog.feature.aircraft.generated.resources.annual
import wingslog.feature.aircraft.generated.resources.back
import wingslog.feature.aircraft.generated.resources.cancel
import wingslog.feature.aircraft.generated.resources.delete
import wingslog.feature.aircraft.generated.resources.delete_aircraft
import wingslog.feature.aircraft.generated.resources.due_dec_2024
import wingslog.feature.aircraft.generated.resources.due_in_14h
import wingslog.feature.aircraft.generated.resources.edit_aircraft
import wingslog.feature.aircraft.generated.resources.engine
import wingslog.feature.aircraft.generated.resources.hundred_hr
import wingslog.feature.aircraft.generated.resources.inspection_status
import wingslog.feature.aircraft.generated.resources.log_details
import wingslog.feature.aircraft.generated.resources.maintenance_summary
import wingslog.feature.aircraft.generated.resources.make_model_template
import wingslog.feature.aircraft.generated.resources.no_inspections_yet
import wingslog.feature.aircraft.generated.resources.pitot_static
import wingslog.feature.aircraft.generated.resources.prop_inspection
import wingslog.feature.aircraft.generated.resources.prop_time_label
import wingslog.feature.aircraft.generated.resources.propeller
import wingslog.feature.aircraft.generated.resources.settings
import wingslog.feature.aircraft.generated.resources.tach_time_hours_format
import wingslog.feature.aircraft.generated.resources.tach_time_label
import wingslog.feature.aircraft.generated.resources.this_action_cannot_be_undone
import wingslog.feature.aircraft.generated.resources.total_logs
import wingslog.feature.aircraft.generated.resources.transponder
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@Composable
fun AircraftOverviewScreen(
  navController: NavController, viewModel: AircraftOverviewViewModel = koinViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is AircraftOverviewEvent.NavigateBack -> navController.popBackStack()
        is AircraftOverviewEvent.ShowError -> {
          coroutineScope.launch {
            snackbarHostState.showSnackbar(event.message)
          }
        }
      }
    }
  }

  val successState = uiState as? AircraftOverviewUiState.Success

  AircraftOverviewContent(
    aircraft = successState?.aircraft,
    logStats = successState?.logStats,
    inspectionCards = successState?.inspectionCards ?: emptyList(),
    showAddInspectionSheet = successState?.showAddInspectionSheet ?: false,
    selectedInspection = successState?.selectedInspection,
    logsForSelectedInspection = successState?.logsForSelectedInspection ?: emptyList(),
    editingInspection = successState?.editingInspection,
    deletingInspectionId = successState?.deletingInspectionId,
    inspectionCardTitleForDelete = successState?.inspectionCards
      ?.find { it.card.id == successState.deletingInspectionId }?.card?.title ?: "",
    snackbarHostState = snackbarHostState,
    onBackClick = { navController.popBackStack() },
    onEditClick = { aircraftId -> navController.navigate("edit_aircraft/$aircraftId") },
    onDeleteConfirm = { viewModel.deleteAircraft() },
    onLogDetailsClick = { aircraftId -> navController.navigate("maintenance_logs/$aircraftId") },
    onAddInspectionClick = { viewModel.showAddInspectionSheet() },
    onDismissAddInspectionSheet = { viewModel.hideAddInspectionSheet() },
    onSaveInspection = { title, component, rules ->
      viewModel.saveNewInspection(title, component, rules)
    },
    onInspectionCardClick = { card -> viewModel.showInspectionDetail(card) },
    onDismissInspectionDetail = { viewModel.hideInspectionDetail() },
    onEditInspectionClick = { card -> viewModel.openEditInspection(card) },
    onDismissEditInspection = { viewModel.closeEditInspection() },
    onSaveEditedInspection = { cardId, title, component, rules, forceDueDate, forceDueTach ->
      viewModel.saveEditedInspection(cardId, title, component, rules, forceDueDate, forceDueTach)
    },
    onDeleteInspectionRequest = { cardId -> viewModel.requestDeleteInspection(cardId) },
    onCancelDeleteInspection = { viewModel.cancelDeleteInspection() },
    onConfirmDeleteInspection = { viewModel.confirmDeleteInspection() },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftOverviewContent(
  aircraft: Aircraft?,
  logStats: LogStats?,
  inspectionCards: List<InspectionCardWithStatus>,
  showAddInspectionSheet: Boolean,
  selectedInspection: InspectionCardWithStatus? = null,
  logsForSelectedInspection: List<dev.fanfly.wingslog.aircraft.MaintenanceLog> = emptyList(),
  editingInspection: InspectionCardWithStatus? = null,
  deletingInspectionId: String? = null,
  inspectionCardTitleForDelete: String = "",
  snackbarHostState: SnackbarHostState,
  onBackClick: () -> Unit,
  onEditClick: (String) -> Unit,
  onDeleteConfirm: () -> Unit,
  onLogDetailsClick: (String) -> Unit,
  onAddInspectionClick: () -> Unit = {},
  onDismissAddInspectionSheet: () -> Unit = {},
  onSaveInspection: (title: String, component: dev.fanfly.wingslog.aircraft.InspectionComponentType, rules: List<dev.fanfly.wingslog.aircraft.InspectionRule>) -> Unit = { _, _, _ -> },
  onInspectionCardClick: (InspectionCardWithStatus) -> Unit = {},
  onDismissInspectionDetail: () -> Unit = {},
  onEditInspectionClick: (InspectionCardWithStatus) -> Unit = {},
  onDismissEditInspection: () -> Unit = {},
  onSaveEditedInspection: (cardId: String, title: String, component: dev.fanfly.wingslog.aircraft.InspectionComponentType, rules: List<dev.fanfly.wingslog.aircraft.InspectionRule>, forceDueDate: com.squareup.wire.Instant?, forceDueTach: Float) -> Unit = { _, _, _, _, _, _ -> },
  onDeleteInspectionRequest: (cardId: String) -> Unit = {},
  onCancelDeleteInspection: () -> Unit = {},
  onConfirmDeleteInspection: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  var showSettingsMenu by rememberSaveable { mutableStateOf(false) }
  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(cmpStringResource(AircraftRes.string.delete_aircraft)) },
      text = { Text(cmpStringResource(AircraftRes.string.this_action_cannot_be_undone)) },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteConfirm()
            showDeleteDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Text(cmpStringResource(AircraftRes.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(cmpStringResource(AircraftRes.string.cancel))
        }
      })
  }

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
          if (aircraft != null) {
            Column {
              Text(
                text = cmpStringResource(
                  AircraftRes.string.make_model_template,
                  aircraft.make,
                  aircraft.model
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
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
              contentDescription = cmpStringResource(AircraftRes.string.back)
            )
          }
        }, actions = {
          IconButton(onClick = { showSettingsMenu = !showSettingsMenu }) {
            Icon(
              Icons.Default.Settings,
              contentDescription = cmpStringResource(AircraftRes.string.settings)
            )
          }
          DropdownMenu(
            expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
            DropdownMenuItem(
              text = { Text(cmpStringResource(AircraftRes.string.edit_aircraft)) },
              onClick = {
                showSettingsMenu = false
                if (aircraft != null) {
                  onEditClick(aircraft.id)
                }
              })
            DropdownMenuItem(
              text = {
                Text(
                  cmpStringResource(AircraftRes.string.delete),
                  color = MaterialTheme.colorScheme.error
                )
              },
              onClick = {
                showSettingsMenu = false
                showDeleteDialog = true
              })
          }
        }, colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          scrolledContainerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { paddingValues ->
    // Add inspection bottom sheet
    if (showAddInspectionSheet) {
      dev.fanfly.wingslog.feature.aircraft.overview.compose.AddInspectionSheet(
        onDismiss = onDismissAddInspectionSheet,
        onSave = { title, component, rules ->
          onSaveInspection(title, component, rules)
        },
      )
    }

    // Inspection detail bottom sheet
    if (selectedInspection != null) {
      dev.fanfly.wingslog.feature.aircraft.overview.compose.InspectionDetailSheet(
        cardWithStatus = selectedInspection,
        logs = logsForSelectedInspection,
        onDismiss = onDismissInspectionDetail,
        onEditClick = { onEditInspectionClick(selectedInspection) },
      )
    }

    // Edit inspection bottom sheet
    if (editingInspection != null) {
      dev.fanfly.wingslog.feature.aircraft.overview.compose.EditInspectionSheet(
        cardWithStatus = editingInspection,
        onDismiss = onDismissEditInspection,
        onSave = onSaveEditedInspection,
        onDeleteRequest = onDeleteInspectionRequest,
      )
    }

    // Delete inspection confirm dialog
    if (deletingInspectionId != null) {
      dev.fanfly.wingslog.feature.aircraft.overview.compose.DeleteInspectionConfirmDialog(
        inspectionTitle = inspectionCardTitleForDelete,
        onConfirm = onConfirmDeleteInspection,
        onDismiss = onCancelDeleteInspection,
      )
    }

    if (aircraft != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
          verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

          // --- Configuration Section ---
          Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            ConfigurationCard(aircraft)
          }
          // --- Log Stats Section ---
          logStats?.let { stats ->
            LogStatsSection(stats = stats, modifier = Modifier.padding(horizontal = 16.dp))
          }

          // --- Inspection Status Section ---
          InspectionStatusSection(
            inspectionCards = inspectionCards,
            onAddClick = onAddInspectionClick,
            onCardClick = onInspectionCardClick,
            modifier = Modifier.padding(horizontal = 16.dp),
          )

          Spacer(Modifier.height(88.dp)) // Clearance for the floating bottom bar
        }

        // Floating Bottom Bar
        LogDetailsBottomBar(
          aircraft = aircraft,
          modifier = Modifier.align(Alignment.BottomCenter),
          onLogDetailsClick = onLogDetailsClick
        )
      }
    }
  }
}


@Composable
fun LogDetailsBottomBar(
  aircraft: Aircraft?,
  modifier: Modifier = Modifier,
  onLogDetailsClick: (String) -> Unit
) {
  if (aircraft != null) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .background(Color.Transparent)
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      Button(
        onClick = { onLogDetailsClick(aircraft.id) },
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxWidth()
          .height(64.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
      ) {
        Icon(
          Icons.Default.Info,
          contentDescription = null,
          modifier = Modifier.padding(end = 8.dp)
        )
        Text(
          text = cmpStringResource(AircraftRes.string.log_details).uppercase(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun LogStatsSection(stats: LogStats, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = cmpStringResource(AircraftRes.string.maintenance_summary),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
    // Flight time metrics — displayed as full-width cards when available
    stats.currentTachTime?.let { tachTime ->
      FlightTimeCard(
        label = cmpStringResource(AircraftRes.string.tach_time_label),
        hours = tachTime,
        modifier = Modifier.fillMaxWidth()
      )
    }
    stats.currentAirframeTime?.let { afTime ->
      FlightTimeCard(
        label = cmpStringResource(AircraftRes.string.airframe_time_label),
        hours = afTime,
        modifier = Modifier.fillMaxWidth()
      )
    }
    stats.currentPropTime?.let { propTime ->
      FlightTimeCard(
        label = cmpStringResource(AircraftRes.string.prop_time_label),
        hours = propTime,
        modifier = Modifier.fillMaxWidth()
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      StatCard(
        label = cmpStringResource(AircraftRes.string.total_logs),
        value = stats.total.toString(),
        modifier = Modifier.weight(1f)
      )
      StatCard(
        label = cmpStringResource(AircraftRes.string.airframe),
        value = stats.airframe.toString(),
        modifier = Modifier.weight(1f)
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      StatCard(
        label = cmpStringResource(AircraftRes.string.engine),
        value = stats.engine.toString(),
        modifier = Modifier.weight(1f)
      )
      StatCard(
        label = cmpStringResource(AircraftRes.string.propeller),
        value = stats.propeller.toString(),
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun FlightTimeCard(label: String, hours: Double, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(
        Icons.Default.Timer,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer
      )
      Column {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
          text = cmpStringResource(AircraftRes.string.tach_time_hours_format, hours.formatToOneDecimalPlace()),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
    }
  }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun InspectionStatusSection(
  inspectionCards: List<InspectionCardWithStatus>,
  onAddClick: () -> Unit,
  onCardClick: (InspectionCardWithStatus) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = cmpStringResource(AircraftRes.string.inspection_status),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      androidx.compose.material3.TextButton(onClick = onAddClick) {
        Text(cmpStringResource(AircraftRes.string.add_inspection))
      }
    }
    if (inspectionCards.isEmpty()) {
      Text(
        text = cmpStringResource(AircraftRes.string.no_inspections_yet),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      inspectionCards.chunked(2).forEach { rowItems ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          rowItems.forEach { item ->
            InspectionCardItem(
              cardWithStatus = item,
              onClick = { onCardClick(item) },
              modifier = Modifier.weight(1f),
            )
          }
          if (rowItems.size == 1) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun InspectionCardItem(
  cardWithStatus: InspectionCardWithStatus,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val statusColor = when {
    cardWithStatus.dueStatus.isOverdue -> MaterialTheme.colorScheme.error
    cardWithStatus.dueStatus.isOnCondition -> MaterialTheme.colorScheme.onSurfaceVariant
    cardWithStatus.dueStatus.nextDueDate != null -> StatusOk
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  val statusText = when {
    cardWithStatus.dueStatus.isOnCondition -> "On Condition"
    cardWithStatus.dueStatus.isOverdue -> "Overdue"
    cardWithStatus.dueStatus.nextDueDate != null -> "Due ${cardWithStatus.dueStatus.nextDueDate}"
    cardWithStatus.dueStatus.nextDueTach != null -> "Due @ ${cardWithStatus.dueStatus.nextDueTach}h"
    else -> "—"
  }
  InspectionCard(
    title = cardWithStatus.card.title,
    status = statusText,
    icon = Icons.Default.CalendarToday,
    statusColor = statusColor,
    onClick = onClick,
    modifier = modifier,
  )
}

@Suppress("unused")
@Composable
private fun InspectionGrid(aircraft: Aircraft, modifier: Modifier = Modifier) {
  val hasEngines = aircraft.engine.isNotEmpty()
  val hasPropeller = aircraft.engine.any { engine ->
    (engine.propeller?.hub?.serial
      ?: "").isNotBlank() || (engine.propeller?.blades?.isNotEmpty() == true)
  }

  // Build the list of applicable inspection cards
  data class InspectionItem(
    val title: String,
    val status: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val statusColor: androidx.compose.ui.graphics.Color
  )

  val items = buildList {
    // Always shown — airframe-level
    add(
      InspectionItem(
        title = cmpStringResource(AircraftRes.string.annual),
        status = cmpStringResource(AircraftRes.string.due_dec_2024),
        icon = Icons.Default.CalendarToday,
        statusColor = StatusOk
      )
    )
    add(
      InspectionItem(
        title = cmpStringResource(AircraftRes.string.pitot_static),
        status = cmpStringResource(AircraftRes.string.due_in_14h),
        icon = Icons.Default.Speed,
        statusColor = StatusWarning
      )
    )
    add(
      InspectionItem(
        title = cmpStringResource(AircraftRes.string.transponder),
        status = cmpStringResource(AircraftRes.string.due_dec_2024),
        icon = Icons.Default.Radio,
        statusColor = StatusOk
      )
    )
    // Engine-dependent
    if (hasEngines) {
      add(
        InspectionItem(
          title = cmpStringResource(AircraftRes.string.hundred_hr),
          status = cmpStringResource(AircraftRes.string.due_in_14h),
          icon = Icons.Default.Schedule,
          statusColor = StatusWarning
        )
      )
    }
    // Propeller-dependent
    if (hasPropeller) {
      add(
        InspectionItem(
          title = cmpStringResource(AircraftRes.string.prop_inspection),
          status = cmpStringResource(AircraftRes.string.due_dec_2024),
          icon = Icons.Default.Settings,
          statusColor = StatusOk
        )
      )
    }
  }

  Column(
    modifier = modifier.padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    items.chunked(2).forEach { rowItems ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        rowItems.forEach { item ->
          InspectionCard(
            title = item.title,
            status = item.status,
            icon = item.icon,
            statusColor = item.statusColor,
            modifier = Modifier.weight(1f)
          )
        }
        // If the row has only one item, add a spacer to fill the second slot
        if (rowItems.size == 1) {
          androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Preview
@Composable
private fun StatCardPreview() {
  MaterialTheme {
    StatCard(label = "Total Logs", value = "42")
  }
}
