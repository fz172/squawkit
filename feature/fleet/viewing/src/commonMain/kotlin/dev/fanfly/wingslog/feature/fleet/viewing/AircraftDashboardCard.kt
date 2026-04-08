package dev.fanfly.wingslog.feature.fleet.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.Propeller
import dev.fanfly.wingslog.aircraft.PropellerBlade
import dev.fanfly.wingslog.aircraft.PropellerHub
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.core.ui.theme.StatusWarningContainer
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.inspection.sharedassets.generated.resources.due_soon
import wingslog.feature.inspection.sharedassets.generated.resources.overdue
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedInspectionRes

@Composable
fun AircraftDashboardCard(
  aircraft: Aircraft,
  onClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  healthStatus: DueStatus? = null,
) {
  Card(
    onClick = { onClick(aircraft.id) },
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${aircraft.make} ${aircraft.model}",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = aircraft.tail_number,
          style = WingslogTypography.dataMedium,
          color = MaterialTheme.colorScheme.primary
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        when (healthStatus) {
          DueStatus.OVERDUE -> HealthStatusBadge(
            label = stringResource(SharedInspectionRes.string.overdue),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
          )

          DueStatus.DUE_SOON -> HealthStatusBadge(
            label = stringResource(SharedInspectionRes.string.due_soon),
            containerColor = StatusWarningContainer,
            contentColor = StatusWarning,
          )

          else -> {}
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
      }
    }
  }
}

@Composable
private fun HealthStatusBadge(
  label: String,
  containerColor: Color,
  contentColor: Color,
) {
  Surface(
    shape = RoundedCornerShape(4.dp),
    color = containerColor,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Medium,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
    )
  }
}

@Preview
@Composable
fun AircraftDetailCardPreview() =
  AircraftDashboardCard(
    aircraft = Aircraft(
      id = "123",
      make = "Sling",
      model = "TSi",
      serial = "SLING532",
      tail_number = "N532SL",
      engine = listOf(
        Engine(
          make = "Rotax",
          model = "915 iSa - 3",
          serial = "915-0001",
          propeller = Propeller(
            hub = PropellerHub(
              make = "Airmaster",
              model = "AP430",
              serial = "AP430-001"
            ),
            blades = listOf(
              PropellerBlade(serial = "B-001"),
              PropellerBlade(serial = "B-002"),
              PropellerBlade(serial = "B-003")
            )
          )
        )
      )
    ),
    onClick = {},
    healthStatus = DueStatus.OVERDUE
  )
