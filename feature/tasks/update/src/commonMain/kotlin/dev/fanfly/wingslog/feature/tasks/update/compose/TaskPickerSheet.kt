package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.ui.common.compose.PickerSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.done
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.compliance_type_ad
import wingslog.feature.tasks.update.generated.resources.compliance_type_routine
import wingslog.feature.tasks.update.generated.resources.compliance_type_sb
import wingslog.feature.tasks.update.generated.resources.no_tasks_configured
import wingslog.feature.tasks.update.generated.resources.select_task_work

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskPickerSheet(
  availableCards: List<MaintenanceTask>,
  selectedIds: List<String>,
  onToggle: (cardId: String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  singleSelect: Boolean = false,
) =
  PickerSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    headerSlot = {
      Text(
        text = stringResource(Res.string.select_task_work),
        style = MaterialTheme.typography.titleLarge
      )
    }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.none),
    ) {
      if (availableCards.isEmpty()) {
        Text(
          text = stringResource(Res.string.no_tasks_configured),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.large),
        )
      } else {
        val grouped = availableCards.groupBy { it.type }

        listOf(
          ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE to stringResource(Res.string.compliance_type_ad),
          ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN to stringResource(Res.string.compliance_type_sb),
          ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION to stringResource(Res.string.compliance_type_routine),
        ).forEach { (type, header) ->
          val cards = grouped[type] ?: emptyList()
          if (cards.isNotEmpty()) {
            Text(
              text = header,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(
                top = Spacing.large,
                bottom = Spacing.small
              )
            )
            cards.forEach { card ->
              val isSelected = card.id in selectedIds
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onToggle(card.id) }
                  .padding(vertical = Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
              ) {
                Icon(
                  imageVector = when {
                    singleSelect && isSelected -> Icons.Default.RadioButtonChecked
                    singleSelect && !isSelected -> Icons.Default.RadioButtonUnchecked
                    isSelected -> Icons.Default.CheckBox
                    else -> Icons.Default.CheckBoxOutlineBlank
                  },
                  contentDescription = null,
                  tint = if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyLarge,
                  )
                  val componentLabel = card.component.displayName()
                  Text(
                    text = if (card.reference_number.isNotBlank()) "${card.reference_number} • $componentLabel" else componentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              HorizontalDivider()
            }
          }
        }
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
