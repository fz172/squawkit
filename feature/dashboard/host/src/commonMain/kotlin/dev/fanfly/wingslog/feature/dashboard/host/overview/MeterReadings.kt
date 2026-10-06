package dev.fanfly.wingslog.feature.dashboard.host.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.formatMeterValue
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.dashboard.api.LogStats
import dev.fanfly.wingslog.thing.MeterDef
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.dashboard.host.generated.resources.meter_reading_update
import wingslog.feature.dashboard.host.generated.resources.overview_meters
import wingslog.feature.dashboard.host.generated.resources.overview_meters_as_of
import wingslog.feature.dashboard.host.generated.resources.Res as DashboardRes

/**
 * What each meter last read, and when. Plain numbers: the app knows no overhaul interval to count
 * down to, so a progress bar here would be inventing one.
 *
 * With [onSetReading] each reading is a control: tapping it opens [MeterReadingDialog] to set what
 * the meter reads now, without writing a log (#1368). Without it they are plain read-outs.
 */
@Composable
internal fun MeterReadings(
  meters: List<MeterDef>,
  stats: LogStats,
  onSetReading: ((meterKey: String, value: Double) -> Unit)? = null,
) {
  val template = LocalThingTemplate.current
  // By key, so the dialog survives a rotation and follows the meter if the template reorders.
  var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
  val editing = meters.firstOrNull { it.key == editingKey }
  if (editing != null && onSetReading != null) {
    MeterReadingDialog(
      meter = editing,
      current = stats.valueFor(editing.key),
      onSave = { value ->
        onSetReading(editing.key, value)
        editingKey = null
      },
      onDismiss = { editingKey = null },
    )
  }
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
        Column(
          modifier = Modifier.weight(1f)
            .then(
              if (onSetReading == null) Modifier else Modifier
                .clip(RoundedCornerShape(Spacing.smallCornerRadius))
                .clickable(
                  onClickLabel = stringResource(DashboardRes.string.meter_reading_update),
                  role = Role.Button,
                ) { editingKey = meter.key }
            )
        ) {
          Text(
            // The template formats it: hours take a decimal place and "HRS", an odometer neither.
            // A declared meter nothing has recorded shows a dash — zero would read as a measurement.
            text = stats.valueFor(meter.key)
              ?.let { template.formatMeterValue(meter.key, it) }
              ?: NO_READING,
            style = WingslogTypography.dataLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
          ) {
            Text(
              text = meter.label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            // What says the number can be changed: a tappable value is drawn as a control, and a
            // ripple alone only shows after the tap.
            if (onSetReading != null) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(Spacing.medium),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

/** Shown for a meter the template declares but nothing has recorded a reading for yet. */
private const val NO_READING = "\u2014"
