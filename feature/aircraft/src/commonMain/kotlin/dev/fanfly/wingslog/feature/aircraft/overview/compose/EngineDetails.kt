package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import org.jetbrains.compose.resources.StringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes
import wingslog.feature.aircraft.generated.resources.*
import wingslog.core.ui.generated.resources.Res as CoreUiRes
import wingslog.core.ui.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Engine

@Composable
fun EngineDetails(index: Int, engine: Engine) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Engine Header
    Column {
      Text(
        text = cmpStringResource(AircraftRes.string.engine_with_index, index + 1),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Companion.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = cmpStringResource(
          AircraftRes.string.model_and_sn,
          engine.make,
          engine.model,
          engine.serial
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Propeller Details
    Column {
      Text(
        text = cmpStringResource(AircraftRes.string.propeller),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Companion.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = cmpStringResource(
          AircraftRes.string.model_and_sn,
          engine.propeller?.hub?.make ?: "",
          engine.propeller?.hub?.model ?: "",
          engine.propeller?.hub?.serial ?: ""
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Blades
    val blades = engine.propeller?.blades ?: emptyList()
    if (blades.isNotEmpty()) {
      BladeChipsOverview(blades)
    }
  }
}