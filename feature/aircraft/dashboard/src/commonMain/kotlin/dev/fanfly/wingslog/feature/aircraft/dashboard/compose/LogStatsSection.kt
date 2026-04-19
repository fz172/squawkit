package dev.fanfly.wingslog.feature.aircraft.dashboard.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.LogStats
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.sharedassets.generated.resources.airframe_time_label
import wingslog.feature.logs.sharedassets.generated.resources.engine_time_label
import wingslog.feature.logs.sharedassets.generated.resources.prop_time_label
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.logs.viewing.generated.resources.maintenance_summary
import wingslog.feature.logs.viewing.generated.resources.total_logs


@Composable
fun LogStatsSection(stats: LogStats, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    Text(
      text = stringResource(MaintenanceRes.string.maintenance_summary),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(Spacing.cardCornerRadius),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth()
          .padding(horizontal = Spacing.extraLarge, vertical = Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
      ) {
        stats.currentAirframeTime?.let {
          StatCell(
            label = stringResource(SharedRes.string.airframe_time_label),
            value = it.formatToOneDecimalPlace(),
            modifier = Modifier.weight(1f)
          )
        }
        stats.currentEngineTime?.let {
          StatCell(
            label = stringResource(SharedRes.string.engine_time_label),
            value = it.formatToOneDecimalPlace(),
            modifier = Modifier.weight(1f)
          )
        }
        stats.currentPropTime?.let {
          StatCell(
            label = stringResource(SharedRes.string.prop_time_label),
            value = it.formatToOneDecimalPlace(),
            modifier = Modifier.weight(1f)
          )
        }
        StatCell(
          label = stringResource(MaintenanceRes.string.total_logs),
          value = stats.total.toString(),
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}


@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.tiny)
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1
    )
  }
}
