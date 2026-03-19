package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import org.jetbrains.compose.resources.StringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes
import wingslog.feature.aircraft.generated.resources.*
import wingslog.core.ui.generated.resources.Res as CoreUiRes
import wingslog.core.ui.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Aircraft

@Composable
fun ConfigurationCard(aircraft: Aircraft) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
  ) {
    Column(
      modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // Airframe
      Column {
        Text(
          text = cmpStringResource(AircraftRes.string.airframe_s_n, aircraft.serial),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

      // Engines
      aircraft.engine.forEachIndexed { index, engine ->
        EngineDetails(index, engine)
      }
    }
  }
}