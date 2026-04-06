package dev.fanfly.wingslog.feature.maintenance.maintenance.log.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.maintenance.maintenance.util.displayName
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.inspection.sharedassets.generated.resources.affects_n_inspection_items
import wingslog.feature.inspection.sharedassets.generated.resources.unknown_date
import wingslog.feature.maintenance.generated.resources.Res as MaintenanceRes
import wingslog.feature.maintenance.generated.resources.airframe_time_abbr
import wingslog.feature.maintenance.generated.resources.engine_time_abbr
import wingslog.feature.maintenance.generated.resources.prop_time_abbr


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
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {

        // Headline: work description (dominant) + component badge right-aligned
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalAlignment = Alignment.Top,
        ) {
          Text(
            text = log.work_description,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
          )
          if (log.component_type != MaintenanceLog.ComponentType.UNKNOWN) {
            Text(
              text = log.component_type.displayName(),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Date — secondary context beneath the headline
        Text(
          text = dateStr,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Inspection badge — demoted to bodySmall, no longer dominant
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

        // Hours — stacked label/value pairs for fast scanning
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

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = Spacing.tiny),
      )
    }
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
    work_description = "Performed annual inspection and oil change.",
    engine_hour = 1234.5,
    airframe_time = 987.0,
    component_type = MaintenanceLog.ComponentType.ENGINE,
    inspection_ids = listOf("annual-card-id", "oil-change-card-id")
  )
  MaterialTheme {
    MaintenanceLogCard(log = log, onClick = {})
  }
}
