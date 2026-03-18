package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.feature.aircraft.R

@Composable
fun EngineDetails(index: Int, engine: Engine) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Engine Header
    Column {
      Text(
        text = stringResource(R.string.engine_with_index, index + 1),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Companion.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = stringResource(
          R.string.model_and_sn,
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
        text = stringResource(R.string.propeller),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Companion.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = stringResource(
          R.string.model_and_sn,
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