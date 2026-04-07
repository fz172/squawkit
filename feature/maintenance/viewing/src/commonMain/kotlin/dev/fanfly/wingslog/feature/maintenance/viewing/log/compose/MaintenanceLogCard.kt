package dev.fanfly.wingslog.feature.maintenance.viewing.log.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.maintenance.sharedassets.util.displayName
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.inspection.sharedassets.generated.resources.affects_n_inspection_items
import wingslog.feature.inspection.sharedassets.generated.resources.unknown_date
import wingslog.feature.maintenance.sharedassets.generated.resources.Res as MaintenanceRes
import wingslog.feature.maintenance.sharedassets.generated.resources.airframe_time_abbr
import wingslog.feature.maintenance.sharedassets.generated.resources.engine_time_abbr
import wingslog.feature.maintenance.sharedassets.generated.resources.prop_time_abbr


@Composable
fun MaintenanceLogCard(
  log: MaintenanceLog,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val dateStr = log.timestamp?.toLocalDate()?.toDisplayFormat()
    ?: cmpStringResource(SharedRes.string.unknown_date)

  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {

      // Headline: work description truncated to 1 line, component chip, and arrow
      // all in a single row so the arrow aligns with the primary content.
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = log.work_description,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (log.component_type != MaintenanceLog.ComponentType.UNKNOWN) {
          ComponentChip(log.component_type.displayName())
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
      }

      // Date — secondary context
      Text(
        text = dateStr,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Inspection badge
      if (log.inspection_ids.isNotEmpty()) {
        Text(
          text = cmpStringResource(
            SharedRes.string.affects_n_inspection_items,
            log.inspection_ids.size,
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      // Hours — stacked label/value pairs
      val hasHours = log.engine_hour > 0.0 || log.airframe_time > 0.0 || log.prop_time > 0.0
      if (hasHours) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.large)) {
          if (log.engine_hour > 0.0) {
            HourItem(
              label = cmpStringResource(MaintenanceRes.string.engine_time_abbr),
              value = log.engine_hour.formatToOneDecimalPlace(),
            )
          }
          if (log.airframe_time > 0.0) {
            HourItem(
              label = cmpStringResource(MaintenanceRes.string.airframe_time_abbr),
              value = log.airframe_time.formatToOneDecimalPlace(),
            )
          }
          if (log.prop_time > 0.0) {
            HourItem(
              label = cmpStringResource(MaintenanceRes.string.prop_time_abbr),
              value = log.prop_time.formatToOneDecimalPlace(),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ComponentChip(label: String) {
  Box(
    modifier = Modifier
      .background(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        shape = RoundedCornerShape(4.dp),
      )
      .padding(horizontal = 6.dp, vertical = 2.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun HourItem(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = WingslogTypography.dataSmall,
    )
  }
}

@Preview
@Composable
private fun MaintenanceLogCardPreview() {
  val log = MaintenanceLog(
    id = "preview-id",
    work_description = "Performed annual inspection and oil change on the Lycoming IO-360.",
    engine_hour = 1234.5,
    airframe_time = 987.0,
    component_type = MaintenanceLog.ComponentType.ENGINE,
    inspection_ids = listOf("annual-card-id", "oil-change-card-id")
  )
  MaterialTheme {
    MaintenanceLogCard(log = log, onClick = {})
  }
}
