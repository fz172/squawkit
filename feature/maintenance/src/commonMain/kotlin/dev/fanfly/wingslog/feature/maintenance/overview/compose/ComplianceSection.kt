package dev.fanfly.wingslog.feature.maintenance.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus
import dev.fanfly.wingslog.feature.inspection.viewing.InspectionCardItem
import wingslog.feature.inspection.sharedassets.generated.resources.add_inspection
import wingslog.feature.inspection.sharedassets.generated.resources.due_with_count
import wingslog.feature.inspection.sharedassets.generated.resources.history_with_count
import wingslog.feature.inspection.sharedassets.generated.resources.inspections
import wingslog.feature.inspection.sharedassets.generated.resources.no_complied_yet
import wingslog.feature.inspection.sharedassets.generated.resources.no_inspections_yet
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedInspectionRes

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
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = cmpStringResource(SharedInspectionRes.string.inspections),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      TextButton(onClick = onAddClick) {
        Text(cmpStringResource(SharedInspectionRes.string.add_inspection))
      }
    }

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
        Text(cmpStringResource(SharedInspectionRes.string.due_with_count, activeInspections.size))
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
          cmpStringResource(
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
              text = cmpStringResource(SharedInspectionRes.string.no_inspections_yet),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = onAddClick,
              shape = RoundedCornerShape(Spacing.buttonCornerRadius)
            ) {
              Icon(Icons.Default.Add, contentDescription = null)
              Spacer(Modifier.width(Spacing.small))
              Text(cmpStringResource(SharedInspectionRes.string.add_inspection).uppercase())
            }
          }
        }
      } else {
        Text(
          text = cmpStringResource(SharedInspectionRes.string.no_complied_yet),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.large)
        )
      }
    } else {
      displayList.chunked(2).forEach { rowItems ->
        Row(
          modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
          horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          rowItems.forEach { item ->
            InspectionCardItem(
              cardWithStatus = item,
              onClick = { onCardClick(item) },
              modifier = Modifier.weight(1f),
            )
          }
          if (rowItems.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}
