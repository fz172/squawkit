package dev.fanfly.wingslog.fleet.dashboard.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Airlines
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DoubleArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun AircraftDashboardCard(aircraft: Aircraft, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(16.dp),
    shape = RoundedCornerShape(24.dp)
  ) {
    Column {
      // Aircraft Info
      Column(Modifier.padding(24.dp)) {
        Text(
          text = stringResource(R.string.make_model_template, aircraft.make, aircraft.model),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.DoubleArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.serial_abbreviation, aircraft.serial),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Airlines,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.tail_number_display_template, aircraft.tailNumber),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Spacer(Modifier.height(24.dp))

        PowerplantSection(aircraft)

        Spacer(Modifier.height(24.dp))

        // Action Button
        Button(
          onClick = { /* Open Logbook */ },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Default.Book, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Open Logbook", modifier = Modifier.padding(vertical = 8.dp)
          )
        }
      }
    }
  }
}

@Composable
fun PowerplantSection(aircraft: Aircraft) {
  aircraft.engineList.forEachIndexed { index, engine ->
    val title = if (aircraft.engineList.size > 1) stringResource(
      R.string.engine_with_index, index + 1
    ) else stringResource(R.string.engine)
    EngineRow(title = title, engine = engine)
  }
}

@Composable
fun EngineRow(title: String, engine: Engine) {
  Spacer(Modifier.height(16.dp))

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(Modifier.padding(8.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
      ) {
        Icon(
          Icons.Default.Settings,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
      )
      SpecItem(
        title = engine.make,
        subtitle = engine.model,
        tertiaryText = stringResource(R.string.serial_abbreviation, engine.serial)
      )
      HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
      ) {
        Icon(
          Icons.Default.Air,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
          stringResource(R.string.propeller).uppercase(),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      SpecItem(
        icon = Icons.Default.Air,
        title = engine.propeller.hub.make,
        subtitle = engine.propeller.hub.model,
        tertiaryText = stringResource(R.string.serial_abbreviation, engine.serial)
      )

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 48.dp)
      ) {
        // Dynamic Blade Grid
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          maxItemsInEachRow = 2
        ) {
          engine.propeller.bladesList.forEachIndexed { index, blade ->
            Box(modifier = Modifier.fillMaxWidth(0.45f)) {
              PropDetail(
                label = "BLADE ${index + 1}",
                blade = blade
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun PropDetail(label: String, blade: PropellerBlade) {
  Column {
    Text(
      text = label,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold
    )
    Text(text = blade.serial, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
  }
}

@Composable
fun SpecItem(
  icon: ImageVector? = null,
  title: String,
  subtitle: String? = null,
  tertiaryText: String? = null
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(vertical = 8.dp)
  ) {
    if (icon != null)
      Box(
        modifier = Modifier
          .size(32.dp)
          .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp)
        )
      }
    Spacer(modifier = Modifier.width(16.dp))
    Column {
      Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
      subtitle?.let {
        Text(
          it,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 15.sp
        )
      }
      tertiaryText?.let {
        Text(
          it,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 13.sp
        )
      }
    }
  }
}

@Preview
@Composable
fun AircraftDetailCardPreview() = AircraftDashboardCard(aircraft = aircraft {
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
      blades += propellerBlade {
        serial = "B-001"
      }
      blades += propellerBlade {
        serial = "B-002"
      }
      blades += propellerBlade {
        serial = "B-003"
      }
    }
  }
  this.engine += engine {
    make = "Rotax"
    model = "915 iS"
    serial = "915-0001"
    this.propeller = propeller {
      hub = propellerHub {
        make = "Airmaster"
        model = "AP430"
        serial = "AP430-001"
      }
      blades += propellerBlade {
        serial = "B-001"
      }
      blades += propellerBlade {
        serial = "B-002"
      }
      blades += propellerBlade {
        serial = "B-0m_03"
      }
    }
  }
})