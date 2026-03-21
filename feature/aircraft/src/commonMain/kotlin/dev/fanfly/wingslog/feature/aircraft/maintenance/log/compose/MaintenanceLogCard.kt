package dev.fanfly.wingslog.feature.aircraft.maintenance.log.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.aircraft.maintenance.util.displayName
import org.jetbrains.compose.ui.tooling.preview.Preview
import wingslog.feature.aircraft.generated.resources.affects_n_inspection_items
import wingslog.feature.aircraft.generated.resources.airframe_time_format
import wingslog.feature.aircraft.generated.resources.edit_log_content_description
import wingslog.feature.aircraft.generated.resources.prop_time_format
import wingslog.feature.aircraft.generated.resources.tach_format
import wingslog.feature.aircraft.generated.resources.unknown_date
import kotlinx.datetime.Instant
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.datetime.toLocalDate
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@Composable
fun MaintenanceLogCard(
  log: MaintenanceLog,
  onEdit: () -> Unit,
  modifier: Modifier = Modifier
) {
  val dateStr = log.timestamp?.toLocalDate()?.toDisplayFormat() ?: cmpStringResource(AircraftRes.string.unknown_date)

  Card(
    modifier = modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = dateStr,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onEdit) {
          Icon(
            Icons.Default.Edit,
            contentDescription = cmpStringResource(AircraftRes.string.edit_log_content_description)
          )
        }
      }

      if (log.inspection_ids.isNotEmpty()) {
        Text(
          text = cmpStringResource(
            AircraftRes.string.affects_n_inspection_items,
            log.inspection_ids.size
          ),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary
        )
      }

      Text(
        text = log.work_description,
        style = MaterialTheme.typography.bodyMedium
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        if (log.tach_time > 0.0) {
          Text(
            text = cmpStringResource(AircraftRes.string.tach_format, log.tach_time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        if (log.airframe_time > 0.0) {
          Text(
            text = cmpStringResource(AircraftRes.string.airframe_time_format, log.airframe_time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        if (log.prop_time > 0.0) {
          Text(
            text = cmpStringResource(AircraftRes.string.prop_time_format, log.prop_time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        if (log.component_type != MaintenanceLog.ComponentType.UNKNOWN) {
          Text(
            text = log.component_type.displayName(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Preview
@Composable
private fun MaintenanceLogCardPreview() {
  val log = dev.fanfly.wingslog.aircraft.MaintenanceLog(
    id = "preview-id",
    work_description = "Performed annual inspection and oil change.",
    tach_time = 1234.5,
    component_type = dev.fanfly.wingslog.aircraft.MaintenanceLog.ComponentType.ENGINE,
    inspection_ids = listOf("annual-card-id", "oil-change-card-id")
  )
  MaterialTheme {
    MaintenanceLogCard(log = log, onEdit = {})
  }
}
