package dev.fanfly.wingslog.feature.fleet.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.Propeller
import dev.fanfly.wingslog.aircraft.PropellerBlade
import dev.fanfly.wingslog.aircraft.PropellerHub
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.core.ui.theme.StatusWarningContainer
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.make_model_template
import wingslog.feature.tasks.sharedassets.generated.resources.Res
import wingslog.feature.tasks.sharedassets.generated.resources.maintenance_due_title
import wingslog.core.ui.generated.resources.Res as CoreRes

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
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.extraLarge),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(
            CoreRes.string.make_model_template,
            aircraft.make,
            aircraft.model,
          ),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.extraSmall))
        Text(
          text = aircraft.tail_number,
          style = WingslogTypography.dataMedium,
          color = MaterialTheme.colorScheme.primary
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
      ) {
        when (healthStatus) {
          DueStatus.OVERDUE -> HealthStatusBadge(
            label = stringResource(Res.string.maintenance_due_title),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
          )

          DueStatus.DUE_SOON -> HealthStatusBadge(
            label = stringResource(Res.string.maintenance_due_title),
            containerColor = StatusWarningContainer,
            contentColor = StatusWarning,
          )

          else -> {}
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
    shape = RoundedCornerShape(Spacing.badgeCornerRadius),
    color = containerColor,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Medium,
      color = contentColor,
      modifier = Modifier.padding(
        horizontal = Spacing.extraSmall,
        vertical = Spacing.extraSmall
      )
    )
  }
}

@Preview
@Composable
fun AircraftDetailCardPreview() =
  WingslogTheme {
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
  }

@Preview
@Composable
fun AircraftDetailCardDueSoonPreview() =
  WingslogTheme {
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
      healthStatus = DueStatus.DUE_SOON
    )
  }
