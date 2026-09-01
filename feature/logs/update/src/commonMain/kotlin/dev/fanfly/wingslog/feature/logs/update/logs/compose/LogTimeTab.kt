package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.meterLabelWithUnit
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.hours_section_description
import wingslog.feature.logs.update.generated.resources.log_tab_hours

@Composable
fun LogTimeTab(
  /** The value typed for each meter the template declares, by key (#730). */
  meterValues: Map<String, String>,
  onMeterChange: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.massive),
  ) {
    // One field per meter the TEMPLATE declares, not three aviation hours. A home declares none,
    // so the whole section — heading and description included — does not render: a "Hours" block
    // with nothing in it is worse than no block (#730).
    //
    // `capabilities.meters` gates it too, because a capability removes UI rather than emptying it.
    val meters = if (LocalThingCapabilities.current.meters) {
      LocalThingTemplate.current?.meters.orEmpty()
    } else {
      emptyList()
    }
    if (meters.isNotEmpty()) {
      LogSection(
        header = stringResource(Res.string.log_tab_hours),
        description = stringResource(Res.string.hours_section_description),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
          meters.forEach { meter ->
            FormTextField(
              value = meterValues[meter.key].orEmpty(),
              onValueChange = { onMeterChange(meter.key, it) },
              label = LocalThingTemplate.current.meterLabelWithUnit(
                meter.key,
                ifAbsent = meter.label,
              ),
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                // The template says whether this meter takes decimals: an odometer does not, and
                // a number pad that offers a point invites "84512.0 mi".
                keyboardType = if (meter.decimal) {
                  KeyboardType.Decimal
                } else {
                  KeyboardType.Number
                },
              ),
            )
          }
        }
      }
    }
  }
}
