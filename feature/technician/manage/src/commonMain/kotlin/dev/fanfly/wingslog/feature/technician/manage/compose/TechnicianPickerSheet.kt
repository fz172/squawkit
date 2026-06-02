package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.ui.common.compose.PickerActionButton
import dev.fanfly.wingslog.core.ui.common.compose.PickerDoneButton
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectableRow
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectionMode
import dev.fanfly.wingslog.core.ui.common.compose.PickerSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.done
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.select_technician
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianPickerSheet(
  availableTechnicians: List<Technician>,
  selectedId: String?,
  onSelect: (Technician?) -> Unit,
  onAddClick: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) =
  PickerSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    headerSlot = {
      Text(
        text = stringResource(TechnicianRes.string.select_technician),
        style = MaterialTheme.typography.titleLarge
      )
    }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      // Technician Options
      availableTechnicians.forEach { technician ->
        val isSelected = technician.id == selectedId
        val certText =
          listOf(technician.cert_type, technician.cert_number)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        PickerSelectableRow(
          title = technician.name,
          subtitle = certText,
          selected = isSelected,
          selectionMode = PickerSelectionMode.RADIO,
          onClick = { onSelect(technician) },
        )
      }

      PickerActionButton(
        text = stringResource(TechnicianRes.string.add_technician),
        icon = Icons.Default.Add,
        onClick = onAddClick,
      )

      PickerDoneButton(
        text = stringResource(CoreRes.string.done),
        onClick = onDismiss,
      )
    }
  }
