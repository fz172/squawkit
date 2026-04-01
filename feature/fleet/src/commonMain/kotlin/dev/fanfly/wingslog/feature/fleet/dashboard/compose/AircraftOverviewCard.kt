package dev.fanfly.wingslog.feature.fleet.dashboard.compose

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.Propeller
import dev.fanfly.wingslog.aircraft.PropellerBlade
import dev.fanfly.wingslog.aircraft.PropellerHub
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun AircraftDashboardCard(
  aircraft: Aircraft,
  onClick: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = { onClick(aircraft.id) },
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.extraLarge),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${aircraft.make} ${aircraft.model}",
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

      // High-level status or summary could go here (e.g. "Airworthy" or "Overdue")
      // For now, keeping it minimal as requested.
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
      )
    }
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
    onClick = {}
  )
