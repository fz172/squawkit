package dev.fanfly.wingslog.feature.aircraft.maintenance.form.compose

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import wingslog.feature.aircraft.generated.resources.airframe
import wingslog.feature.aircraft.generated.resources.done
import wingslog.feature.aircraft.generated.resources.engine
import wingslog.feature.aircraft.generated.resources.no_inspection_cards_configured
import wingslog.feature.aircraft.generated.resources.propeller
import wingslog.feature.aircraft.generated.resources.select_inspection_work
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes
import wingslog.feature.aircraft.inspection.generated.resources.Res as InspectionRes
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_ad
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_sb
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_routine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionPickerSheet(
  availableCards: List<InspectionCard>,
  selectedIds: List<String>,
  onToggle: (cardId: String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = Spacing.extraLarge)
        .padding(bottom = Spacing.huge),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      // Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = cmpStringResource(AircraftRes.string.select_inspection_work),
          style = MaterialTheme.typography.titleLarge
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = null)
        }
      }

      if (availableCards.isEmpty()) {
        Text(
          text = cmpStringResource(AircraftRes.string.no_inspection_cards_configured),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.large),
        )
      } else {
        val grouped = availableCards.groupBy { it.type }

        listOf(
          ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE to cmpStringResource(InspectionRes.string.compliance_type_ad),
          ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN to cmpStringResource(InspectionRes.string.compliance_type_sb),
          ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION to cmpStringResource(InspectionRes.string.compliance_type_routine),
        ).forEach { (type, header) ->
          val cards = grouped[type] ?: emptyList()
          if (cards.isNotEmpty()) {
            Text(
              text = header,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = Spacing.large, bottom = Spacing.small)
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
                  imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                  contentDescription = null,
                  tint = if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyLarge,
                  )
                  val componentLabel = when (card.component) {
                    InspectionComponentType.INSPECTION_COMPONENT_ENGINE -> cmpStringResource(
                      AircraftRes.string.engine
                    )

                    InspectionComponentType.INSPECTION_COMPONENT_PROPELLER -> cmpStringResource(
                      AircraftRes.string.propeller
                    )

                    InspectionComponentType.INSPECTION_COMPONENT_AVIONICS -> "Avionics"
                    else -> cmpStringResource(AircraftRes.string.airframe)
                  }
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
        Text(cmpStringResource(AircraftRes.string.done))
      }
    }
  }
}
