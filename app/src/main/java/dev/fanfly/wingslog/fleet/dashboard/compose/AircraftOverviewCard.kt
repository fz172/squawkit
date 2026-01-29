package dev.fanfly.wingslog.fleet.dashboard.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.PropellerBlade
import dev.fanfly.wingslog.aircraft.aircraft
import dev.fanfly.wingslog.aircraft.engine
import dev.fanfly.wingslog.aircraft.propeller
import dev.fanfly.wingslog.aircraft.propellerBlade
import dev.fanfly.wingslog.aircraft.propellerHub

@Composable
fun AircraftDashboardCard(
        aircraft: Aircraft,
        onClick: (String) -> Unit,
        modifier: Modifier = Modifier
) {
  Card(
          modifier = modifier.fillMaxWidth().padding(16.dp),
          shape = RoundedCornerShape(28.dp),
          colors =
                  CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.surfaceContainer,
                  ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column {
      // --- Header Section ---
      Column(
              modifier =
                      Modifier.fillMaxWidth()
                              .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
      ) {
        Text(
                text = stringResource(R.string.make_model_template, aircraft.make, aircraft.model),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          if (aircraft.tailNumber.isNotBlank()) {
            Badge(
                    text = aircraft.tailNumber,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
          Text(
                  text = "S/N: ${aircraft.serial}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      HorizontalDivider(
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
              modifier = Modifier.padding(horizontal = 24.dp)
      )

      // --- Powerplant Section ---
      Column(
              modifier = Modifier.padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(24.dp)
      ) { aircraft.engineList.forEachIndexed { index, engine -> EngineItem(engine = engine) } }

      // --- Action Footer ---
      // Pinned to bottom of card, full width button for easy reach
      Box(
              modifier =
                      Modifier.fillMaxWidth()
                              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                              .padding(horizontal = 16.dp, vertical = 16.dp)
      ) {
        Button(
                onClick = { onClick(aircraft.id) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                        )
        ) {
          Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(12.dp))
          Text(
                  text = "Open Logbook",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold
          )
        }
      }
    }
  }
}

@Composable
fun Badge(
        text: String,
        containerColor: androidx.compose.ui.graphics.Color,
        contentColor: androidx.compose.ui.graphics.Color
) {
  Surface(
          color = containerColor,
          shape = RoundedCornerShape(8.dp),
  ) {
    Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
    )
  }
}

@Composable
fun EngineItem(engine: Engine) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Engine Header
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
              Icons.Default.Settings,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(20.dp)
      )
      Spacer(Modifier.width(12.dp))
      Column {
        Text(
                text = "${engine.make} ${engine.model}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
        )
        Text(
                text = "S/N: ${engine.serial}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // Propeller Details (Indented)
    Row(verticalAlignment = Alignment.Top) {
      // Visual tree connection line could go here, but keeping it clean for now
      Spacer(Modifier.width(32.dp))
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
                  Icons.Default.Air,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(18.dp)
          )
          Spacer(Modifier.width(8.dp))
          Text(
                  text = "${engine.propeller.hub.make} ${engine.propeller.hub.model}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface
          )
        }

        // Blades (Compact FlowRow)
        if (engine.propeller.bladesList.isNotEmpty()) {
          BladeChips(engine.propeller.bladesList)
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BladeChips(blades: List<PropellerBlade>) {
  FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    blades.forEachIndexed { index, blade ->
      Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              border =
                      androidx.compose.foundation.BorderStroke(
                              1.dp,
                              MaterialTheme.colorScheme.outlineVariant
                      )
      ) {
        Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
                  text = "B${index + 1}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
          Spacer(Modifier.width(4.dp))
          Text(
                  text = blade.serial,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun AircraftDetailCardPreview() =
        AircraftDashboardCard(
                aircraft =
                        aircraft {
                          id = "123"
                          make = "Sling"
                          model = "TSi"
                          serial = "SLING532"
                          tailNumber = "N532SL"
                          this.engine += engine {
                            make = "Rotax"
                            model = "915 iSa - 3"
                            serial = "915-0001"
                            this.propeller = propeller {
                              hub = propellerHub {
                                make = "Airmaster"
                                model = "AP430"
                                serial = "AP430-001"
                              }
                              blades += propellerBlade { serial = "B-001" }
                              blades += propellerBlade { serial = "B-002" }
                              blades += propellerBlade { serial = "B-003" }
                            }
                          }
                        },
                onClick = {}
        )
