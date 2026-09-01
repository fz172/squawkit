package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.dashboard.data.LogStats
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.sharedassets.generated.resources.airframe_time_label
import wingslog.feature.logs.sharedassets.generated.resources.engine_time_label
import wingslog.feature.logs.sharedassets.generated.resources.prop_time_label
import wingslog.feature.logs.viewing.generated.resources.maintenance_summary
import wingslog.feature.logs.viewing.generated.resources.total_logs
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes


@Composable
fun LogStatsSection(
  stats: LogStats,
  modifier: Modifier = Modifier,
) {
  val meters = LocalThingCapabilities.current.meters
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
  ) {
    Text(
      text = stringResource(MaintenanceRes.string.maintenance_summary),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(Spacing.cardCornerRadius),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      border = BorderStroke(
        Spacing.hairline,
        MaterialTheme.colorScheme.outlineVariant
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth()
          .padding(
            horizontal = Spacing.extraLarge,
            vertical = Spacing.large
          ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
      ) {
        // A capability removes UI rather than blanking it: a homeowner should never see
        // "Engine Time 0.0" on a template that declares no meters at all (PRD §4.8). The three
        // aviation meters are still hardcoded — #703 and #730 render the template's own meter set —
        // but showing them on a home was wrong data, not just the wrong labels.
        if (meters) {
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
        }
        // Not a meter — a log count is meaningful for every template, so it survives the gate.
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
private fun StatCell(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
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
