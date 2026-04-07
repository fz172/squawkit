package dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Engine
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.component_propeller
import wingslog.feature.maintenance.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.maintenance.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.maintenance.sharedassets.generated.resources.engine_with_index
import wingslog.feature.maintenance.viewing.generated.resources.model_and_sn

@Composable
fun EngineDetails(index: Int, engine: Engine) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Engine Header
    Column {
      Text(
        text = cmpStringResource(SharedRes.string.engine_with_index, index + 1),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Companion.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = cmpStringResource(
          MaintenanceRes.string.model_and_sn,
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
        text = cmpStringResource(CoreRes.string.component_propeller),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Companion.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = cmpStringResource(
          MaintenanceRes.string.model_and_sn,
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