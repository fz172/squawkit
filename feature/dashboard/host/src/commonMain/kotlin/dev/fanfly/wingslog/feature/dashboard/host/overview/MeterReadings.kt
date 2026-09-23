package dev.fanfly.wingslog.feature.dashboard.host.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.formatMeterValue
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.dashboard.api.LogStats
import dev.fanfly.wingslog.thing.MeterDef
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.dashboard.host.generated.resources.overview_meters
import wingslog.feature.dashboard.host.generated.resources.overview_meters_as_of
import wingslog.feature.dashboard.host.generated.resources.Res as DashboardRes

/**
 * What each meter last read, and when. Plain numbers: the app knows no overhaul interval to count
 * down to, so a progress bar here would be inventing one.
 */
@Composable
internal fun MeterReadings(meters: List<MeterDef>, stats: LogStats) {
  val template = LocalThingTemplate.current
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(DashboardRes.string.overview_meters),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      stats.readingsAsOf?.let { asOf ->
        Text(
          text = stringResource(
            DashboardRes.string.overview_meters_as_of,
            asOf.toDisplayFormat()
          ),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
      meters.forEach { meter ->
        Column(modifier = Modifier.weight(1f)) {
          Text(
            // The template formats it: hours take a decimal place and "HRS", an odometer neither.
            // A declared meter nothing has recorded shows a dash — zero would read as a measurement.
            text = stats.valueFor(meter.key)
              ?.let { template.formatMeterValue(meter.key, it) }
              ?: NO_READING,
            style = WingslogTypography.dataLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = meter.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** Shown for a meter the template declares but nothing has recorded a reading for yet. */
private const val NO_READING = "\u2014"
