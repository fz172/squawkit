package dev.fanfly.wingslog.feature.inspection.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.inspection.viewing.generated.resources.Res as ViewingRes
import wingslog.feature.inspection.viewing.generated.resources.critical_airworthiness


@Composable
fun CriticalAlertsSection(
  overdueInspections: List<InspectionCardWithStatus>,
  onCardClick: (InspectionCardWithStatus) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer
    ),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    shape = RoundedCornerShape(Spacing.small)
  ) {
    Column(
      modifier = Modifier.padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
      ) {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error
        )
        Text(
          text = stringResource(ViewingRes.string.critical_airworthiness),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.error,
          fontWeight = FontWeight.Black
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        overdueInspections.forEach { inspection ->
          InspectionCardItem(
            cardWithStatus = inspection,
            onClick = { onCardClick(inspection) },
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
  }
}