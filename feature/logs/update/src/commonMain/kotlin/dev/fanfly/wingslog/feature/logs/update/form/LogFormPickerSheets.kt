package dev.fanfly.wingslog.feature.logs.update.form

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkPickerSheet
import dev.fanfly.wingslog.feature.tasks.update.picker.TaskPickerSheet
import dev.fanfly.wingslog.feature.technician.manage.compose.TechnicianPickerSheet

/** The technician, squawk and task pickers, each shown while its flag in [uiState] is set. */
@Composable
internal fun LogFormPickerSheets(
  uiState: MaintenanceLogFormUiState,
  viewModel: MaintenanceLogFormViewModel,
  navController: NavController,
) {
if (uiState.showTechnicianPicker) {
  TechnicianPickerSheet(
    availableTechnicians = uiState.availableTechnicians,
    knownCertifications = uiState.knownCertifications,
    linkedTechnicians = uiState.linkedTechnicians,
    selfId = uiState.selfTechnicianId,
    selectedId = uiState.selectedTechnician?.id,
    onSelect = { viewModel.onTechnicianSelect(it) },
    onAddClick = {
      viewModel.hideTechnicianPicker()
      navController.navigate(Screen.EditTechnician.createRoute(null))
    },
    onDismiss = { viewModel.hideTechnicianPicker() },
  )
}

if (uiState.showSquawkPicker) {
  SquawkPickerSheet(
    openSquawks = uiState.availableSquawks,
    selectedIds = uiState.selectedSquawkIds.toSet(),
    onToggle = { id, _ -> viewModel.toggleSquawkSelection(id) },
    onDismiss = viewModel::hideSquawkPicker,
  )
}

if (uiState.showInspectionPicker) {
  TaskPickerSheet(
    availableCards = uiState.availableInspectionCards,
    selectedIds = uiState.selectedInspectionIds,
    onToggle = viewModel::toggleInspectionSelection,
    onDismiss = viewModel::hideInspectionPicker,
  )
}
}
