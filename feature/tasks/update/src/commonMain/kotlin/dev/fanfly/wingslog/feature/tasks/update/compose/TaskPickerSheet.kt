package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.ui.common.compose.PickerDoneButton
import dev.fanfly.wingslog.core.ui.common.compose.PickerSectionHeader
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectableRow
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectionMode
import dev.fanfly.wingslog.core.ui.common.compose.PickerSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.done
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.compliance_type_ad
import wingslog.feature.tasks.update.generated.resources.compliance_type_routine
import wingslog.feature.tasks.update.generated.resources.compliance_type_sb
import wingslog.feature.tasks.update.generated.resources.no_tasks_configured
import wingslog.feature.tasks.update.generated.resources.reference_and_component
import wingslog.feature.tasks.update.generated.resources.select_task_work
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

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
          ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE to stringResource(
            Res.string.compliance_type_ad
          ),
          ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN to stringResource(Res.string.compliance_type_sb),
          ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION to stringResource(
            Res.string.compliance_type_routine
          ),
        ).forEach { (type, header) ->
          val cards = grouped[type] ?: emptyList()
          if (cards.isNotEmpty()) {
            PickerSectionHeader(text = header)
            cards.forEach { card ->
              val isSelected = card.id in selectedIds
              val componentLabel = card.component.displayName()
              val subtitle = if (card.reference_number.isNotBlank()) {
                stringResource(
                  Res.string.reference_and_component,
                  card.reference_number,
                  componentLabel,
                )
              } else {
                componentLabel
              }
              PickerSelectableRow(
                title = card.title,
                subtitle = subtitle,
                selected = isSelected,
                selectionMode = if (singleSelect) {
                  PickerSelectionMode.RADIO
                } else {
                  PickerSelectionMode.CHECKBOX
                },
                onClick = { onToggle(card.id) },
              )
            }
          }
        }
      }

      PickerDoneButton(
        text = stringResource(CoreRes.string.done),
        onClick = onDismiss,
      )
    }
  }
