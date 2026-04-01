package dev.fanfly.wingslog.feature.aircraft.maintenance.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.compose.InspectionPickerSheet
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.data.MaintenanceLogFormEvent
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.data.MaintenanceLogFormViewModel
import dev.fanfly.wingslog.feature.aircraft.maintenance.util.displayName
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.aircraft.generated.resources.add
import wingslog.feature.aircraft.generated.resources.add_log
import wingslog.feature.aircraft.generated.resources.airframe_serial
import wingslog.feature.aircraft.generated.resources.airframe_time_hours
import wingslog.feature.aircraft.generated.resources.back
import wingslog.feature.aircraft.generated.resources.cancel
import wingslog.feature.aircraft.generated.resources.component_type
import wingslog.feature.aircraft.generated.resources.delete
import wingslog.feature.aircraft.generated.resources.delete_log
import wingslog.feature.aircraft.generated.resources.delete_log_content_description
import wingslog.feature.aircraft.generated.resources.edit_log
import wingslog.feature.aircraft.generated.resources.engine
import wingslog.feature.aircraft.generated.resources.inspection_work
import wingslog.feature.aircraft.generated.resources.loading_aircraft
import wingslog.feature.aircraft.generated.resources.maintenance_date
import wingslog.feature.aircraft.generated.resources.no_engines_found
import wingslog.feature.aircraft.generated.resources.no_inspection_work_recorded
import wingslog.feature.aircraft.generated.resources.no_propeller_components_found
import wingslog.feature.aircraft.generated.resources.ok
import wingslog.feature.aircraft.generated.resources.prop_time_hours
import wingslog.feature.aircraft.generated.resources.propeller_component
import wingslog.feature.aircraft.generated.resources.remove
import wingslog.feature.aircraft.generated.resources.save
import wingslog.feature.aircraft.generated.resources.tach_time_hours
import wingslog.feature.aircraft.generated.resources.tap_to_change_date
import wingslog.feature.aircraft.generated.resources.this_action_cannot_be_undone
import wingslog.feature.aircraft.generated.resources.unknown_inspection
import wingslog.feature.aircraft.generated.resources.update
import wingslog.feature.aircraft.generated.resources.work_description_required
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogFormScreen(
  navController: NavController,
  viewModel: MaintenanceLogFormViewModel = koinViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        MaintenanceLogFormEvent.SaveSuccess -> navController.popBackStack()
        MaintenanceLogFormEvent.DeleteSuccess -> navController.popBackStack()
      }
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(cmpStringResource(AircraftRes.string.delete_log)) },
      text = { Text(cmpStringResource(AircraftRes.string.this_action_cannot_be_undone)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteLog()
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
      }
    )
  }

  val saveLabel =
    cmpStringResource(if (viewModel.isEditMode) AircraftRes.string.update else AircraftRes.string.save)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            if (viewModel.isEditMode) cmpStringResource(AircraftRes.string.edit_log) else cmpStringResource(
              AircraftRes.string.add_log
            )
          )
        },
        navigationIcon = {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = cmpStringResource(AircraftRes.string.back)
            )
          }
        },
        actions = {
          if (viewModel.isEditMode) {
            IconButton(onClick = { showDeleteDialog = true }) {
              Icon(
                Icons.Default.Delete,
                contentDescription = cmpStringResource(AircraftRes.string.delete_log_content_description),
                tint = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      )
    }
  ) { innerPadding ->
    if (uiState.isLoading) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Maintenance Date
          val dateDisplayText = uiState.maintenanceDate?.toDisplayFormat()
            ?: cmpStringResource(AircraftRes.string.tap_to_change_date)
          OutlinedTextField(
            value = dateDisplayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(cmpStringResource(AircraftRes.string.maintenance_date)) },
            leadingIcon = {
              Icon(
                Icons.Default.CalendarToday,
                contentDescription = cmpStringResource(AircraftRes.string.maintenance_date)
              )
            },
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showDatePicker = true },
            singleLine = true,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
              disabledTextColor = MaterialTheme.colorScheme.onSurface,
              disabledBorderColor = MaterialTheme.colorScheme.outline,
              disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
              disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          )

          if (showDatePicker) {
            val initialMs = uiState.maintenanceDate?.let { date ->
              date.let {
                LocalDateTime(it.year, it.month, it.day, 12, 0, 0)
                  .let { ldt ->
                    Instant.fromEpochSeconds(
                      ldt.date.toEpochDays() * 86400L
                    ).toEpochMilliseconds()
                  }
              }
            }
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
            DatePickerDialog(
              onDismissRequest = { showDatePicker = false },
              confirmButton = {
                TextButton(onClick = {
                  val selectedMs = datePickerState.selectedDateMillis
                  if (selectedMs != null) {
                    val selectedDate = Instant.fromEpochMilliseconds(selectedMs)
                      .toLocalDateTime(TimeZone.UTC).date
                    viewModel.onMaintenanceDateChange(selectedDate)
                  }
                  showDatePicker = false
                }) {
                  Text(cmpStringResource(AircraftRes.string.ok))
                }
              },
              dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                  Text(cmpStringResource(AircraftRes.string.cancel))
                }
              }
            ) {
              DatePicker(state = datePickerState)
            }
          }

          // Work Description (required)
          OutlinedTextField(
            value = uiState.workDescription,
            onValueChange = viewModel::onWorkDescriptionChange,
            label = { Text(cmpStringResource(AircraftRes.string.work_description_required)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            isError = uiState.error == "Work description is required"
          )

          // Inspection Work section
          InspectionWorkSection(
            selectedIds = uiState.selectedInspectionIds,
            availableCards = uiState.availableInspectionCards,
            onAddClick = viewModel::showInspectionPicker,
            onRemove = viewModel::removeInspectionId,
            modifier = Modifier.fillMaxWidth(),
          )

          // Inspection picker bottom sheet
          if (uiState.showInspectionPicker) {
            InspectionPickerSheet(
              availableCards = uiState.availableInspectionCards,
              selectedIds = uiState.selectedInspectionIds,
              onToggle = viewModel::toggleInspectionSelection,
              onDismiss = viewModel::hideInspectionPicker,
            )
          }

          // Tach Time
          OutlinedTextField(
            value = uiState.tachTime,
            onValueChange = viewModel::onTachTimeChange,
            label = { Text(cmpStringResource(AircraftRes.string.tach_time_hours)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
          )

          // Airframe Time
          OutlinedTextField(
            value = uiState.airframeTime,
            onValueChange = viewModel::onAirframeTimeChange,
            label = { Text(cmpStringResource(AircraftRes.string.airframe_time_hours)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
          )

          // Prop Time
          OutlinedTextField(
            value = uiState.propTime,
            onValueChange = viewModel::onPropTimeChange,
            label = { Text(cmpStringResource(AircraftRes.string.prop_time_hours)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
          )

          // Component section
          ComponentSection(
            aircraft = uiState.aircraft,
            selectedComponentType = uiState.selectedComponentType,
            selectedSubComponent = uiState.selectedSubComponent,
            onComponentTypeChange = viewModel::onComponentTypeChange,
            onSubComponentChange = viewModel::onSubComponentChange,
            modifier = Modifier.fillMaxWidth()
          )

          // Error message
          if (uiState.error != null) {
            Text(
              text = uiState.error!!,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall
            )
          }

          Spacer(Modifier.height(88.dp))
        }
        BottomButtons(
          modifier = Modifier.align(Alignment.BottomCenter),
          onSaveClick = viewModel::save,
          onCancelClick = { navController.popBackStack() },
          saveEnabled = !uiState.isSaving,
          isSaving = uiState.isSaving,
          saveLabel = saveLabel,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentSection(
  aircraft: Aircraft?,
  selectedComponentType: MaintenanceLog.ComponentType,
  selectedSubComponent: String?,
  onComponentTypeChange: (MaintenanceLog.ComponentType) -> Unit,
  onSubComponentChange: (String?) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Component Type dropdown
    ComponentTypeDropdown(
      selected = selectedComponentType,
      onSelected = onComponentTypeChange,
      modifier = Modifier.fillMaxWidth()
    )

    when (selectedComponentType) {
      MaintenanceLog.ComponentType.AIRFRAME -> {
        // Display aircraft serial (read-only)
        val serial = aircraft?.serial ?: ""
        OutlinedTextField(
          value = serial,
          onValueChange = {},
          readOnly = true,
          label = { Text(cmpStringResource(AircraftRes.string.airframe_serial)) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }

      MaintenanceLog.ComponentType.ENGINE -> {
        if (aircraft == null) {
          Text(
            text = cmpStringResource(AircraftRes.string.loading_aircraft),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          val engines = aircraft.engine
          if (engines.isEmpty()) {
            Text(
              text = cmpStringResource(AircraftRes.string.no_engines_found),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            val options = engines.map { engine ->
              val label = buildString {
                if (engine.make.isNotEmpty()) append(engine.make)
                if (engine.model.isNotEmpty()) {
                  if (isNotEmpty()) append(" ")
                  append(engine.model)
                }
                if (engine.serial.isNotEmpty()) append(" (${engine.serial})")
              }
              label to engine.serial
            }
            SubComponentDropdown(
              label = cmpStringResource(AircraftRes.string.engine),
              options = options,
              selectedSerial = selectedSubComponent,
              onSelected = onSubComponentChange,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }

      MaintenanceLog.ComponentType.PROPELLER -> {
        if (aircraft == null) {
          Text(
            text = cmpStringResource(AircraftRes.string.loading_aircraft),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          // Collect all propeller components from all engines
          val options = mutableListOf<Pair<String, String>>()
          aircraft.engine.forEach { engine ->
            val prop = engine.propeller
            val hub = prop?.hub
            if (hub?.serial?.isNotEmpty() == true) {
              val label = buildString {
                append("Hub")
                if (hub.make.isNotEmpty()) append(" - ${hub.make}")
                if (hub.model.isNotEmpty()) append(" ${hub.model}")
                append(" (${hub.serial})")
              }
              options.add(label to hub.serial)
            }
            prop?.blades?.forEach { blade ->
              if (blade.serial.isNotEmpty()) {
                val label = buildString {
                  append("Blade")
                  if (blade.make.isNotEmpty()) append(" - ${blade.make}")
                  if (blade.model.isNotEmpty()) append(" ${blade.model}")
                  append(" (${blade.serial})")
                }
                options.add(label to blade.serial)
              }
            }
          }

          if (options.isEmpty()) {
            Text(
              text = cmpStringResource(AircraftRes.string.no_propeller_components_found),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            SubComponentDropdown(
              label = cmpStringResource(AircraftRes.string.propeller_component),
              options = options,
              selectedSerial = selectedSubComponent,
              onSelected = onSubComponentChange,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }

      else -> {
        // UNKNOWN — no sub-component
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubComponentDropdown(
  label: String,
  options: List<Pair<String, String>>, // display label to serial
  selectedSerial: String?,
  onSelected: (String?) -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel = options.firstOrNull { it.second == selectedSerial }?.first ?: ""

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier
  ) {
    OutlinedTextField(
      value = selectedLabel,
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { (displayLabel, serial) ->
        DropdownMenuItem(
          text = { Text(displayLabel) },
          onClick = {
            onSelected(serial)
            expanded = false
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentTypeDropdown(
  selected: MaintenanceLog.ComponentType,
  onSelected: (MaintenanceLog.ComponentType) -> Unit,
  modifier: Modifier = Modifier
) {
  val options = listOf(
    MaintenanceLog.ComponentType.UNKNOWN,
    MaintenanceLog.ComponentType.AIRFRAME,
    MaintenanceLog.ComponentType.ENGINE,
    MaintenanceLog.ComponentType.PROPELLER
  )
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier
  ) {
    OutlinedTextField(
      value = selected.displayName(),
      onValueChange = {},
      readOnly = true,
      label = { Text(cmpStringResource(AircraftRes.string.component_type)) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(option.displayName()) },
          onClick = {
            onSelected(option)
            expanded = false
          }
        )
      }
    }
  }
}

@Composable
private fun InspectionWorkSection(
  selectedIds: List<String>,
  availableCards: List<dev.fanfly.wingslog.aircraft.InspectionCard>,
  onAddClick: () -> Unit,
  onRemove: (cardId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = cmpStringResource(AircraftRes.string.inspection_work),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
      )
      OutlinedButton(
        onClick = onAddClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
          horizontal = 12.dp, vertical = 4.dp
        ),
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
          cmpStringResource(AircraftRes.string.add),
          style = MaterialTheme.typography.labelMedium
        )
      }
    }

    if (selectedIds.isEmpty()) {
      Text(
        text = cmpStringResource(AircraftRes.string.no_inspection_work_recorded),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      selectedIds.forEach { cardId ->
        val card = availableCards.firstOrNull { it.id == cardId }
        val title = card?.title ?: cmpStringResource(AircraftRes.string.unknown_inspection, cardId)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { onRemove(cardId) }) {
            Icon(
              Icons.Default.Close,
              contentDescription = cmpStringResource(AircraftRes.string.remove),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        HorizontalDivider()
      }
    }
  }
}
