package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.ui.common.compose.PickerSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.select_technician
import wingslog.core.ui.generated.resources.done
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes
import wingslog.core.ui.generated.resources.Res as CoreRes

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
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(technician) }
            .padding(vertical = Spacing.medium),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          Icon(
            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = technician.name,
              style = MaterialTheme.typography.bodyLarge,
            )
            if (technician.cert_type.isNotBlank() || technician.cert_number.isNotBlank()) {
              val certText = listOf(technician.cert_type, technician.cert_number)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
              Text(
                text = certText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        HorizontalDivider()
      }

      TextButton(
        onClick = onAddClick,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = Spacing.small),
      ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null)
        Text(stringResource(TechnicianRes.string.add_technician), modifier = Modifier.padding(start = Spacing.small))
      }

      Button(
        onClick = onDismiss,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = Spacing.large),
      ) {
        Text(stringResource(CoreRes.string.done))
      }
    }
  }
