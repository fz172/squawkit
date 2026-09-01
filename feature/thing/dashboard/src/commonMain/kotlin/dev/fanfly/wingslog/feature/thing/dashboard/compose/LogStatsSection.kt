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
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.dashboard.data.LogStats
import org.jetbrains.compose.resources.stringResource
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
  val template = LocalThingTemplate.current
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
        // One cell per meter the TEMPLATE declares, not three hardcoded aviation ones. A bike
        // showed "Airframe / Engine Time / Prop Time" because the cells were fixed and only their
        // values came from data; reading the meter set is what makes an automotive thing able to
        // show an odometer instead.
        //
        // A declared meter with no reading yet draws nothing rather than "0.0" — logs only carry
        // the three aviation hour fields until #730 stores readings per meter key, so a car's
        // odometer has no source and an invented zero would read as a real measurement.
        //
        // The whole block is behind `meters` because a capability removes UI: a homeowner should
        // never see a meter cell at all (PRD §4.8).
        if (meters) {
          template?.meters.orEmpty().forEach { meter ->
            val value = stats.valueFor(meter.key) ?: return@forEach
            StatCell(
              label = meter.label,
              value = value.formatToOneDecimalPlace(),
              modifier = Modifier.weight(1f),
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
