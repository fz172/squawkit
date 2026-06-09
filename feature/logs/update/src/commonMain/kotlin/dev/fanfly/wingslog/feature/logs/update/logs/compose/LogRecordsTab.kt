package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.ui.common.compose.FormValueField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.performed_by_description
import wingslog.feature.logs.update.generated.resources.squawks_section_header
import wingslog.feature.logs.update.generated.resources.tasks_section_header
import wingslog.feature.technician.sharedassets.generated.resources.performed_by
import wingslog.feature.technician.sharedassets.generated.resources.select_technician
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

@Composable
fun LogRecordsTab(
  technicianEnabled: Boolean,
  selectedTechnician: Technician?,
  onTechnicianClick: () -> Unit,
  selectedSquawkIds: List<String>,
  availableSquawks: List<Squawk>,
  onAddSquawkClick: () -> Unit,
  onRemoveSquawk: (String) -> Unit,
  selectedInspectionIds: List<String>,
  availableInspectionCards: List<MaintenanceTask>,
  onAddTaskClick: () -> Unit,
  onRemoveTask: (String) -> Unit,
  attachmentUploadEnabled: Boolean,
  attachmentSection: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.massive),
  ) {
    if (technicianEnabled) {
      LogSection(
        header = stringResource(TechnicianRes.string.performed_by),
        description = stringResource(Res.string.performed_by_description),
      ) {
        val displayText = selectedTechnician?.name
          ?: stringResource(TechnicianRes.string.select_technician)
        FormValueField(
          value = displayText,
          label = stringResource(TechnicianRes.string.performed_by),
          onClick = onTechnicianClick,
          accessibilityDescription = stringResource(TechnicianRes.string.performed_by),
          leadingIcon = {
            Icon(
              Icons.Default.Person,
              contentDescription = null
            )
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    LogSection(header = stringResource(Res.string.squawks_section_header)) {
      SquawkWorkSection(
        selectedIds = selectedSquawkIds,
        availableSquawks = availableSquawks,
        onAddClick = onAddSquawkClick,
        onRemove = onRemoveSquawk,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    LogSection(header = stringResource(Res.string.tasks_section_header)) {
      TaskWorkSection(
        selectedIds = selectedInspectionIds,
        availableCards = availableInspectionCards,
        onAddClick = onAddTaskClick,
        onRemove = onRemoveTask,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (attachmentUploadEnabled) {
      attachmentSection()
    }
  }
}
