package dev.fanfly.wingslog.feature.aircraft.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.feature.aircraft.inspection.data.DueStatus
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.feature.aircraft.inspection.compose.DeleteInspectionConfirmDialog
import dev.fanfly.wingslog.feature.aircraft.inspection.compose.InspectionCard
import dev.fanfly.wingslog.feature.aircraft.inspection.data.InspectionCardWithStatus
import dev.fanfly.wingslog.feature.aircraft.inspection.ui.InspectionDetailSheet
import dev.fanfly.wingslog.feature.aircraft.overview.compose.ConfigurationCard
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewEvent
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewViewModel
import dev.fanfly.wingslog.feature.aircraft.overview.data.LogStats
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.back
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.dash
import wingslog.core.ui.generated.resources.delete
import wingslog.core.ui.generated.resources.error_occurred
import wingslog.feature.aircraft.generated.resources.add_first_maintenance_log
import wingslog.feature.aircraft.generated.resources.add_log
import wingslog.feature.aircraft.generated.resources.airframe_time_label
import wingslog.feature.aircraft.generated.resources.delete_aircraft
import wingslog.feature.aircraft.generated.resources.engine_time_label
import wingslog.feature.aircraft.generated.resources.log_details
import wingslog.feature.aircraft.generated.resources.maintenance_summary
import wingslog.feature.aircraft.generated.resources.make_model_template
import wingslog.feature.aircraft.generated.resources.prop_time_label
import wingslog.feature.aircraft.generated.resources.this_action_cannot_be_undone
import wingslog.feature.aircraft.generated.resources.total_logs
import wingslog.feature.aircraft.inspection.generated.resources.add_inspection
import wingslog.feature.aircraft.inspection.generated.resources.complied
import wingslog.feature.aircraft.inspection.generated.resources.critical_airworthiness
import wingslog.feature.aircraft.inspection.generated.resources.due_date
import wingslog.feature.aircraft.inspection.generated.resources.due_engine
import wingslog.feature.aircraft.inspection.generated.resources.due_with_count
import wingslog.feature.aircraft.inspection.generated.resources.history_with_count
import wingslog.feature.aircraft.inspection.generated.resources.inspections
import wingslog.feature.aircraft.inspection.generated.resources.no_complied_yet
import wingslog.feature.aircraft.inspection.generated.resources.no_inspections_yet
import wingslog.feature.aircraft.inspection.generated.resources.on_condition
import wingslog.feature.aircraft.inspection.generated.resources.overdue
import wingslog.feature.aircraft.inspection.generated.resources.overdue_was
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes
import wingslog.feature.aircraft.inspection.generated.resources.Res as InspectionRes


@Composable
fun AircraftOverviewScreen(
  navController: NavController, viewModel: AircraftOverviewViewModel = koinViewModel(),
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
    onAddInspectionClick = { aircraftId -> navController.navigate("aircraft_inspection_create/$aircraftId") },
    onInspectionCardClick = { card -> viewModel.showInspectionDetail(card) },
    onDismissInspectionDetail = { viewModel.hideInspectionDetail() },
    onEditInspectionClick = { aircraftId, cardId -> navController.navigate("aircraft_inspection_edit/$aircraftId/$cardId") },
    onCancelDeleteInspection = { viewModel.cancelDeleteInspection() },
    onConfirmDeleteInspection = { viewModel.confirmDeleteInspection() },
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
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var showComplied by rememberSaveable { mutableStateOf(false) }

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
                  AircraftRes.string.make_model_template, aircraft.make, aircraft.model
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
              dev.fanfly.wingslog.feature.aircraft.overview.compose.LogOnboardingCard(
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
      primaryLabel = if (hasLogs) cmpStringResource(AircraftRes.string.add_log)
      else cmpStringResource(AircraftRes.string.add_first_maintenance_log),
      onSecondaryClick = if (hasLogs) ({ onLogDetailsClick(aircraft.id) }) else null,
      secondaryLabel = cmpStringResource(AircraftRes.string.log_details),
    )
  }
}

@Composable
private fun LogStatsSection(stats: LogStats, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    Text(
      text = cmpStringResource(AircraftRes.string.maintenance_summary),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
      stats.currentAirframeTime?.let {
        StatCell(
          label = cmpStringResource(AircraftRes.string.airframe_time_label),
          value = it.formatToOneDecimalPlace(),
          modifier = Modifier.weight(1f)
        )
      }
      stats.currentEngineTime?.let {
        StatCell(
          label = cmpStringResource(AircraftRes.string.engine_time_label),
          value = it.formatToOneDecimalPlace(),
          modifier = Modifier.weight(1f)
        )
      }
      stats.currentPropTime?.let {
        StatCell(
          label = cmpStringResource(AircraftRes.string.prop_time_label),
          value = it.formatToOneDecimalPlace(),
          modifier = Modifier.weight(1f)
        )
      }
      StatCell(
        label = cmpStringResource(AircraftRes.string.total_logs),
        value = stats.total.toString(),
        modifier = Modifier.weight(1f)
      )
    }
  }
}


@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.tiny)
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1
    )
  }
}

@Composable
private fun CriticalAlertsSection(
  overdueInspections: List<InspectionCardWithStatus>,
  onCardClick: (InspectionCardWithStatus) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer
    ),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    shape = RoundedCornerShape(Spacing.small)
  ) {
    Column(
      modifier = Modifier.padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
      ) {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error
        )
        Text(
          text = cmpStringResource(InspectionRes.string.critical_airworthiness),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.error,
          fontWeight = FontWeight.Black
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        overdueInspections.forEach { inspection ->
          InspectionCardItem(
            cardWithStatus = inspection,
            onClick = { onCardClick(inspection) },
            modifier = Modifier.fillMaxWidth()
          )
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
  val status = cardWithStatus.dueStatus.status
  val statusColor = when (status) {
    DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
    DueStatus.DUE_SOON -> StatusWarning
    DueStatus.COMPLIED -> StatusOk
    DueStatus.NORMAL -> {
      if (cardWithStatus.dueStatus.isOnCondition) MaterialTheme.colorScheme.onSurfaceVariant
      else StatusOk
    }
  }
  val statusText = when {
    status == DueStatus.COMPLIED -> cmpStringResource(InspectionRes.string.complied)
    cardWithStatus.dueStatus.isOnCondition -> cmpStringResource(InspectionRes.string.on_condition)
    status == DueStatus.OVERDUE -> {
      val dateStr = cardWithStatus.dueStatus.nextDueDate?.toDisplayFormat()
      if (dateStr != null) cmpStringResource(InspectionRes.string.overdue_was, dateStr)
      else cmpStringResource(InspectionRes.string.overdue)
    }

    cardWithStatus.dueStatus.nextDueDate != null -> cmpStringResource(
      InspectionRes.string.due_date,
      cardWithStatus.dueStatus.nextDueDate!!.toDisplayFormat()
    )

    cardWithStatus.dueStatus.nextDueEngine != null -> cmpStringResource(
      InspectionRes.string.due_engine,
      cardWithStatus.dueStatus.nextDueEngine!!.toDouble().formatToOneDecimalPlace()
    )

    else -> cmpStringResource(CoreRes.string.dash)
  }
  val icon = when (status) {
    DueStatus.OVERDUE -> Icons.Filled.Error
    DueStatus.COMPLIED -> Icons.Default.CheckCircle
    else -> Icons.Default.CalendarToday
  }
  InspectionCard(
    title = cardWithStatus.card.title,
    status = statusText,
    icon = icon,
    statusColor = statusColor,
    isOverdue = status == DueStatus.OVERDUE,
    onClick = onClick,
    modifier = modifier,
  )
}

@Composable
private fun ComplianceSection(
  activeInspections: List<InspectionCardWithStatus>,
  compliedInspections: List<InspectionCardWithStatus>,
  showComplied: Boolean,
  onToggleComplied: (Boolean) -> Unit,
  onAddClick: () -> Unit,
  onCardClick: (InspectionCardWithStatus) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = cmpStringResource(InspectionRes.string.inspections),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      androidx.compose.material3.TextButton(onClick = onAddClick) {
        Text(cmpStringResource(InspectionRes.string.add_inspection))
      }
    }

    // View Toggle (Due vs History)
    SingleChoiceSegmentedButtonRow(
      modifier = Modifier.fillMaxWidth()
    ) {
      SegmentedButton(
        selected = !showComplied,
        onClick = { onToggleComplied(false) },
        shape = SegmentedButtonDefaults.itemShape(
          index = 0,
          count = 2
        )
      ) {
        Text(cmpStringResource(InspectionRes.string.due_with_count, activeInspections.size))
      }
      SegmentedButton(
        selected = showComplied,
        onClick = { onToggleComplied(true) },
        shape = SegmentedButtonDefaults.itemShape(
          index = 1,
          count = 2
        )
      ) {
        Text(cmpStringResource(InspectionRes.string.history_with_count, compliedInspections.size))
      }
    }

    val displayList = if (showComplied) compliedInspections else activeInspections

    if (displayList.isEmpty()) {
      if (!showComplied) {
        Card(
          modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.medium),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = RoundedCornerShape(Spacing.cardCornerRadius)
        ) {
          Column(
            modifier = Modifier.padding(Spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              Icons.Default.CalendarToday,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
              text = cmpStringResource(InspectionRes.string.no_inspections_yet),
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = onAddClick,
              shape = RoundedCornerShape(Spacing.buttonCornerRadius)
            ) {
              Icon(Icons.Default.Add, contentDescription = null)
              Spacer(Modifier.width(Spacing.small))
              Text(cmpStringResource(InspectionRes.string.add_inspection).uppercase())
            }
          }
        }
      } else {
        Text(
          text = cmpStringResource(InspectionRes.string.no_complied_yet),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.large)
        )
      }
    } else {
      displayList.chunked(2).forEach { rowItems ->
        Row(
          modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
          horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          rowItems.forEach { item ->
            InspectionCardItem(
              cardWithStatus = item,
              onClick = { onCardClick(item) },
              modifier = Modifier.weight(1f),
            )
          }
          if (rowItems.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}
