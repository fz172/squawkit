package dev.fanfly.wingslog.feature.dashboard.host.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.formatMeterNumber
import dev.fanfly.wingslog.core.template.formatMeterValue
import dev.fanfly.wingslog.core.template.meterLabelWithUnit
import dev.fanfly.wingslog.core.ui.form.FormTextField
import dev.fanfly.wingslog.core.ui.popup.AlertDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.thing.MeterDef
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.save
import wingslog.feature.dashboard.host.generated.resources.meter_reading_field
import wingslog.feature.dashboard.host.generated.resources.meter_reading_lower_warning
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.dashboard.host.generated.resources.Res as DashboardRes

/**
 * Sets what one meter reads now (#1368).
 *
 * One number and nothing else: the reading is taken as of now, by whoever is signed in, and a log
 * is where anything more than that belongs.
 *
 * A reading below the current one is **warned about, not refused**. A meter does not normally run
 * backwards, so it is most often a slip — but a mistyped earlier reading and a replaced tach are
 * both corrected by exactly this, and a dialog that refused it would leave no way to fix either.
 *
 * @param current what the meter reads now, or null when nothing has recorded it — the dialog is
 *   also how a Thing with no logs gets its first reading.
 */
@Composable
internal fun MeterReadingDialog(
  meter: MeterDef,
  current: Double?,
  onSave: (Double) -> Unit,
  onDismiss: () -> Unit,
) {
  val template = LocalThingTemplate.current
  // Opens holding the current reading: the new one is nearly always that plus a little.
  var text by rememberSaveable(meter.key) {
    mutableStateOf(
      current?.let { template.formatMeterNumber(meter.key, it) }
        .orEmpty()
    )
  }
  val reading = parseMeterInput(text)
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  AlertDialog(
    onDismissRequest = onDismiss,
    // The template's own name for the meter, with its unit: "Engine Time (hrs)".
    title = { Text(template.meterLabelWithUnit(meter.key, ifAbsent = meter.label)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        FormTextField(
          label = stringResource(DashboardRes.string.meter_reading_field),
          value = text,
          onValueChange = { text = filterMeterInput(it, meter.decimal) },
          modifier = Modifier.focusRequester(focusRequester),
          textStyle = WingslogTypography.dataLarge,
          // Typing replaces the number rather than appending to it.
          selectAllOnFocus = true,
          keyboardOptions = KeyboardOptions(
            keyboardType = if (meter.decimal) {
              KeyboardType.Decimal
            } else {
              KeyboardType.Number
            },
          ),
        )
        if (reading != null && current != null && reading < current) {
          Text(
            text = stringResource(
              DashboardRes.string.meter_reading_lower_warning,
              template.formatMeterValue(meter.key, current),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.statusColors.caution.accent,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { reading?.let(onSave) },
        enabled = reading != null,
      ) {
        Text(stringResource(CoreRes.string.save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  )
}
