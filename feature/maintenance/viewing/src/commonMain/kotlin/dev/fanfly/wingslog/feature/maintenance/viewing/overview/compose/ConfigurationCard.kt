package dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.maintenance.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.maintenance.viewing.generated.resources.airframe_s_n
import wingslog.feature.maintenance.viewing.generated.resources.collapse_details
import wingslog.feature.maintenance.viewing.generated.resources.edit_aircraft
import wingslog.feature.maintenance.viewing.generated.resources.expand_details

@Composable
fun ConfigurationCard(
  aircraft: Aircraft,
  onEditClick: (String) -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
  ) {
    Column(
      modifier = Modifier.padding(Spacing.extraLarge)
    ) {
      // Airframe & Edit Action
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = cmpStringResource(MaintenanceRes.string.airframe_s_n, aircraft.serial),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = { expanded = !expanded }) {
            Icon(
              imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
              contentDescription = if (expanded) cmpStringResource(MaintenanceRes.string.collapse_details) else cmpStringResource(
                MaintenanceRes.string.expand_details
              ),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          OutlinedButton(
            onClick = { onEditClick(aircraft.id) },
            shape = RoundedCornerShape(Spacing.small)
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = null,
              modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(Spacing.small))
            Text(
              text = cmpStringResource(MaintenanceRes.string.edit_aircraft),
              style = MaterialTheme.typography.labelMedium
            )
          }
        }
      }

      // Collapsible engine details
      AnimatedVisibility(visible = expanded) {
        Column(
          modifier = Modifier.padding(top = Spacing.extraLarge),
          verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
        ) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
          aircraft.engine.forEachIndexed { index, engine ->
            EngineDetails(index, engine)
          }
        }
      }
    }
  }
}