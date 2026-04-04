package dev.fanfly.wingslog.feature.maintenance.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.PropellerBlade
import dev.fanfly.wingslog.core.ui.theme.Spacing
import wingslog.feature.maintenance.generated.resources.blade_abbreviation_with_index
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.maintenance.generated.resources.Res as MaintenanceRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BladeChipsOverview(blades: List<PropellerBlade>) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.small)
  ) {
    blades.forEachIndexed { index, blade ->
      Surface(
        shape = RoundedCornerShape(Spacing.small),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = cmpStringResource(
              MaintenanceRes.string.blade_abbreviation_with_index, index + 1, blade.serial
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}