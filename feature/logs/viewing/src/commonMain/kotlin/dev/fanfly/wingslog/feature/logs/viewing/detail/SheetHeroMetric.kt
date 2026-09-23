package dev.fanfly.wingslog.feature.logs.viewing.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.formatMeterNumber
import dev.fanfly.wingslog.core.template.meterUnit
import dev.fanfly.wingslog.core.template.primaryReading
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.thing.MaintenanceLog

@Composable
internal fun SheetHeroMetric(log: MaintenanceLog) {
  // The first meter this template declares that the log recorded. It used to switch on
  // `component_type` across the three aviation hour fields, so a car's log — which records an
  // odometer — matched no branch and rendered a blank (#761).
  val primary = LocalThingTemplate.current.primaryReading(log) ?: return
  val (meter, value) = primary
  val label = meter.label

  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.extraSmall))
    Row {
      Text(
        // The meter's own decimal place — an odometer reads "84512", not "84512.0".
        text = LocalThingTemplate.current.formatMeterNumber(meter.key, value),
        style = WingslogTypography.heroDisplay,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.alignByBaseline(),
      )
      Text(
        // The meter's own unit, beside the number as before — "MI" on a car.
        text = LocalThingTemplate.current.meterUnit(meter.key),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.alignByBaseline()
          .padding(start = Spacing.extraSmall),
      )
    }
  }
}
