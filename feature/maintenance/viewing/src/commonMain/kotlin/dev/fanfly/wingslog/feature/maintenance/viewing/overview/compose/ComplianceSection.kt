package dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus
import dev.fanfly.wingslog.feature.inspection.viewing.InspectionCardItem
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedInspectionRes
import wingslog.feature.inspection.sharedassets.generated.resources.add_inspection
import wingslog.feature.inspection.sharedassets.generated.resources.due_with_count
import wingslog.feature.inspection.sharedassets.generated.resources.history_with_count
import wingslog.feature.inspection.sharedassets.generated.resources.inspections
import wingslog.feature.inspection.sharedassets.generated.resources.no_complied_yet
import wingslog.feature.inspection.sharedassets.generated.resources.no_inspections_yet

@Composable
fun ComplianceSection(
  activeInspections: List<InspectionCardWithStatus>,
  compliedInspections: List<InspectionCardWithStatus>,
  showComplied: Boolean,
  onToggleComplied: (Boolean) -> Unit,
  onAddClick: () -> Unit,
  onCardClick: (InspectionCardWithStatus) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    Text(
      text = stringResource(SharedInspectionRes.string.inspections),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )

    // View Toggle (Due vs History)
    SingleChoiceSegmentedButtonRow(
      modifier = Modifier.fillMaxWidth()
    ) {
      SegmentedButton(
        selected = !showComplied,
        onClick = { onToggleComplied(false) },
        shape = SegmentedButtonDefaults.itemShape(
          index = 0,
          count = 2
        )
      ) {
        Text(stringResource(SharedInspectionRes.string.due_with_count, activeInspections.size))
      }
      SegmentedButton(
        selected = showComplied,
        onClick = { onToggleComplied(true) },
        shape = SegmentedButtonDefaults.itemShape(
          index = 1,
          count = 2
        )
      ) {
        Text(
          stringResource(
            SharedInspectionRes.string.history_with_count,
            compliedInspections.size
          )
        )
      }
    }

    val displayList = if (showComplied) compliedInspections else activeInspections

    if (displayList.isEmpty()) {
      if (!showComplied) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = RoundedCornerShape(Spacing.cardCornerRadius)
        ) {
          Column(
            modifier = Modifier.padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
          ) {
            Text(
              text = stringResource(SharedInspectionRes.string.no_inspections_yet),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = onAddClick,
              shape = RoundedCornerShape(Spacing.buttonCornerRadius)
            ) {
              Icon(Icons.Default.Add, contentDescription = null)
              Spacer(Modifier.width(Spacing.small))
              Text(stringResource(SharedInspectionRes.string.add_inspection).uppercase())
            }
          }
        }
      } else {
        Text(
          text = stringResource(SharedInspectionRes.string.no_complied_yet),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.large)
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        displayList.forEach { item ->
          InspectionCardItem(
            cardWithStatus = item,
            onClick = { onCardClick(item) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}
