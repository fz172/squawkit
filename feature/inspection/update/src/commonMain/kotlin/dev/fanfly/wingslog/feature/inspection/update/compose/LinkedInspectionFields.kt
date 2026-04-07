package dev.fanfly.wingslog.feature.inspection.update.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.unknown
import wingslog.feature.inspection.update.generated.resources.link_to_inspection
import wingslog.feature.inspection.update.generated.resources.remove_link
import wingslog.feature.inspection.update.generated.resources.schedule_with_another_work
import wingslog.feature.inspection.update.generated.resources.schedule_with_another_work_description
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.inspection.update.generated.resources.Res as InspectionRes

@Composable
fun LinkedInspectionFields(
  linkedToId: String?,
  onLinkChange: (String?) -> Unit,
  availableInspections: List<InspectionCard>,
  modifier: Modifier = Modifier,
) {
  var showLinkedPicker by remember { mutableStateOf(false) }

  Column(modifier = modifier) {
    Text(
      stringResource(InspectionRes.string.schedule_with_another_work),
      style = MaterialTheme.typography.labelLarge
    )
    Text(
      stringResource(InspectionRes.string.schedule_with_another_work_description),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(Spacing.small))

    if (linkedToId == null) {
      OutlinedButton(
        onClick = { showLinkedPicker = true },
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(Spacing.small))
        Text(stringResource(InspectionRes.string.link_to_inspection))
      }
    } else {
      val linkedInsp = availableInspections.find { it.id == linkedToId }
      InputChip(
        selected = true,
        onClick = { showLinkedPicker = true },
        label = {
          Text(
            linkedInsp?.title ?: stringResource(CoreRes.string.unknown)
          )
        },
        trailingIcon = {
          IconButton(
            onClick = { onLinkChange(null) },
            modifier = Modifier.size(InputChipDefaults.IconSize)
          ) {
            Icon(
              Icons.Default.Close,
              contentDescription = stringResource(InspectionRes.string.remove_link),
              modifier = Modifier.size(InputChipDefaults.IconSize)
            )
          }
        })
    }
  }

  if (showLinkedPicker) {
    InspectionPickerSheet(
      availableCards = availableInspections,
      selectedIds = listOfNotNull(linkedToId),
      onToggle = { id ->
        onLinkChange(if (linkedToId == id) null else id)
        showLinkedPicker = false
      },
      onDismiss = { showLinkedPicker = false },
      singleSelect = true
    )
  }
}
