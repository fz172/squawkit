package dev.fanfly.wingslog.feature.maintenance.update.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import dev.fanfly.wingslog.core.attachments.viewing.AttachmentFormSection
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.feature.inspection.update.compose.InspectionPickerSheet
import dev.fanfly.wingslog.feature.maintenance.update.logs.compose.ComponentSection
import dev.fanfly.wingslog.feature.maintenance.update.logs.compose.InspectionWorkSection
import dev.fanfly.wingslog.feature.maintenance.update.logs.viewmodel.MaintenanceLogFormEvent
import dev.fanfly.wingslog.feature.maintenance.update.logs.viewmodel.MaintenanceLogFormViewModel
import dev.fanfly.wingslog.feature.technician.manage.compose.TechnicianPickerSheet
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.attachments.sharedassets.generated.resources.Res as AttachRes
import wingslog.core.attachments.sharedassets.generated.resources.file_added
import wingslog.core.attachments.sharedassets.generated.resources.file_read_error
import wingslog.core.attachments.sharedassets.generated.resources.link_added
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.back
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.delete
import wingslog.core.ui.generated.resources.ok
import wingslog.core.ui.generated.resources.save
import wingslog.core.ui.generated.resources.update
import wingslog.feature.maintenance.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.maintenance.sharedassets.generated.resources.add_log
import wingslog.feature.maintenance.sharedassets.generated.resources.edit_log
import wingslog.feature.maintenance.sharedassets.generated.resources.maintenance_date
import wingslog.feature.maintenance.sharedassets.generated.resources.this_action_cannot_be_undone
import wingslog.feature.maintenance.update.generated.resources.Res as MaintenanceRes
import wingslog.feature.maintenance.update.generated.resources.airframe_time_hours
import wingslog.feature.maintenance.update.generated.resources.delete_log
import wingslog.feature.maintenance.update.generated.resources.engine_time_hours
import wingslog.feature.maintenance.update.generated.resources.log_deleted
import wingslog.feature.maintenance.update.generated.resources.log_saved
import wingslog.feature.maintenance.update.generated.resources.log_updated
import wingslog.feature.maintenance.update.generated.resources.prop_time_hours
import wingslog.feature.maintenance.update.generated.resources.tap_to_change_date
import wingslog.feature.maintenance.update.generated.resources.work_description_required
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes
import wingslog.feature.technician.sharedassets.generated.resources.performed_by
import wingslog.feature.technician.sharedassets.generated.resources.select_technician

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogFormScreen(
  navController: NavController,
  viewModel: MaintenanceLogFormViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  val logUpdatedMessage = stringResource(MaintenanceRes.string.log_updated)
  val logSavedMessage = stringResource(MaintenanceRes.string.log_saved)
  val logDeletedMessage = stringResource(MaintenanceRes.string.log_deleted)
  val fileAddedMessage = stringResource(AttachRes.string.file_added)
  val linkAddedMessage = stringResource(AttachRes.string.link_added)
  val fileReadErrorMessage = stringResource(AttachRes.string.file_read_error)

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        MaintenanceLogFormEvent.SaveSuccess -> {
          val message = if (viewModel.isEditMode) logUpdatedMessage else logSavedMessage
          navController.previousBackStackEntry?.savedStateHandle?.set("success_message", message)
          navController.popBackStack()
        }

        MaintenanceLogFormEvent.DeleteSuccess -> {
          navController.previousBackStackEntry?.savedStateHandle?.set(
            "success_message",
            logDeletedMessage
          )
          navController.popBackStack()
        }

        MaintenanceLogFormEvent.FileAdded -> snackbarHostState.showSnackbar(fileAddedMessage)
        MaintenanceLogFormEvent.LinkAdded -> snackbarHostState.showSnackbar(linkAddedMessage)
        MaintenanceLogFormEvent.PickError -> snackbarHostState.showSnackbar(fileReadErrorMessage)
      }
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(MaintenanceRes.string.delete_log)) },
      text = { Text(stringResource(SharedRes.string.this_action_cannot_be_undone)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteLog()
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
      }
    )
  }

  val saveLabel =
    stringResource(if (viewModel.isEditMode) CoreRes.string.update else CoreRes.string.save)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            if (viewModel.isEditMode) stringResource(SharedRes.string.edit_log) else stringResource(
              SharedRes.string.add_log
            )
          )
        },
        navigationIcon = {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(CoreRes.string.back)
            )
          }
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
            ?: stringResource(MaintenanceRes.string.tap_to_change_date)
          OutlinedTextField(
            value = dateDisplayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(SharedRes.string.maintenance_date)) },
            leadingIcon = {
              Icon(
                Icons.Default.CalendarToday,
                contentDescription = stringResource(SharedRes.string.maintenance_date)
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
                  Text(stringResource(CoreRes.string.ok))
                }
              },
              dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                  Text(stringResource(CoreRes.string.cancel))
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
            label = { Text(stringResource(MaintenanceRes.string.work_description_required)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            isError = uiState.error != null
          )

          // Technician (Performed By)
          val technicianDisplayText = uiState.selectedTechnician?.name ?: stringResource(
            TechnicianRes.string.select_technician
          )
          OutlinedTextField(
            value = technicianDisplayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(TechnicianRes.string.performed_by)) },
            leadingIcon = {
              Icon(Icons.Default.Person, contentDescription = null)
            },
            modifier = Modifier
              .fillMaxWidth()
              .clickable { viewModel.showTechnicianPicker() },
            singleLine = true,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
              disabledTextColor = MaterialTheme.colorScheme.onSurface,
              disabledBorderColor = MaterialTheme.colorScheme.outline,
              disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
              disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          )

          if (uiState.showTechnicianPicker) {
            TechnicianPickerSheet(
              availableTechnicians = uiState.availableTechnicians,
              selectedId = uiState.selectedTechnician?.id,
              onSelect = { viewModel.onTechnicianSelect(it) },
              onAddClick = {
                viewModel.hideTechnicianPicker()
                // Navigate to edit technician screen
                navController.navigate(Screen.EditTechnician.createRoute(null))
              },
              onDismiss = { viewModel.hideTechnicianPicker() }
            )
          }

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

          // Engine Time
          OutlinedTextField(
            value = uiState.engineTime,
            onValueChange = viewModel::onEngineTimeChange,
            label = { Text(stringResource(MaintenanceRes.string.engine_time_hours)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
          )

          // Airframe Time
          OutlinedTextField(
            value = uiState.airframeTime,
            onValueChange = viewModel::onAirframeTimeChange,
            label = { Text(stringResource(MaintenanceRes.string.airframe_time_hours)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
          )

          // Prop Time
          OutlinedTextField(
            value = uiState.propTime,
            onValueChange = viewModel::onPropTimeChange,
            label = { Text(stringResource(MaintenanceRes.string.prop_time_hours)) },
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

          // Attachments
          AttachmentFormSection(
            visibleAttachments = uiState.visibleAttachments,
            isAnonymous = uiState.isAnonymous,
            filesAtLimit = uiState.filesAtLimit,
            showPickerSheet = uiState.showAttachmentPicker,
            onAddClick = viewModel::showAttachmentPicker,
            onRemove = viewModel::removeAttachment,
            onPickFiles = viewModel::addLocalFiles,
            onAddLink = viewModel::addLink,
            onDismissSheet = viewModel::hideAttachmentPicker,
            onCancelUpload = viewModel::cancelUpload,
            onRetryUpload = viewModel::retryUpload,
            onPickError = viewModel::onFilePickError,
            modifier = Modifier.fillMaxWidth(),
          )

          // Error message
          uiState.error?.let { error ->
            Text(
              text = error.asString(),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodySmall
            )
          }

          Spacer(Modifier.height(88.dp))
        }
        BottomButtons(
          modifier = Modifier.align(Alignment.BottomCenter),
          onPrimaryClick = viewModel::save,
          onSecondaryClick = { navController.popBackStack() },
          onDangerClick = if (viewModel.isEditMode) {
            { showDeleteDialog = true }
          } else null,
          dangerLabel = stringResource(CoreRes.string.delete),
          primaryEnabled = !uiState.isSaving,
          isPrimaryFunctionInProgress = uiState.isSaving,
          primaryLabel = saveLabel,
        )
      }
    }
  }
}

