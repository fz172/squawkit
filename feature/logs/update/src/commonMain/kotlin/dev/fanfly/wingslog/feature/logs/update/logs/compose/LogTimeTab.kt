package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.meterLabelWithUnit
import dev.fanfly.wingslog.core.template.metersLabel
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.hours_section_description
import wingslog.feature.logs.update.generated.resources.log_tab_hours
import wingslog.feature.logs.update.generated.resources.meter_use_suggestion

@Composable
fun LogTimeTab(
  /** The value typed for each meter the template declares, by key (#730). */
  meterValues: Map<String, String>,
  /** What a meter would read if it had moved with the leading one, by key — offered, not applied. */
  meterSuggestions: Map<String, String>,
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
        // The template's word for its meters, matching the tab above it.
        header = LocalThingTemplate.current.metersLabel(
          ifAbsent = stringResource(Res.string.log_tab_hours),
        ),
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
              // The field opens holding the current reading (the form prefills it), and the new
              // one is a different number rather than an edit of this one — so tapping in offers
              // the whole value for replacement instead of a caret after the last digit.
              selectAllOnFocus = true,
              keyboardOptions = KeyboardOptions(
                // The template says whether this meter takes decimals: an odometer does not, and
                // a number pad that offers a point invites "84512.0 mi".
                keyboardType = if (meter.decimal) {
                  KeyboardType.Decimal
                } else {
                  KeyboardType.Number
                },
              ),
              // Inside the field rather than under it: the offer belongs to this meter, and a row
              // of its own would push the next field down every time the leading meter changes.
              trailingIcon = meterSuggestions[meter.key]?.let { suggested ->
                {
                  UseMeterSuggestion(
                    suggested = suggested,
                    onClick = { onMeterChange(meter.key, suggested) },
                  )
                }
              },
            )
          }
        }
      }
    }
  }
}

/**
 * "Use 3.9" — the reading this meter would show if it had moved with the leading one.
 *
 * An offer, not a correction: it fills the field the user would otherwise work out by hand, and
 * disappears once the field says what it suggests.
 */
@Composable
private fun UseMeterSuggestion(
  suggested: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(Spacing.chipCornerRadius),
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.primary,
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.primary),
    modifier = Modifier.padding(end = Spacing.small),
  ) {
    Text(
      text = stringResource(Res.string.meter_use_suggestion, suggested),
      style = MaterialTheme.typography.labelMedium,
      maxLines = 1,
      modifier = Modifier.padding(
        horizontal = Spacing.small,
        vertical = Spacing.extraSmall,
      ),
    )
  }
}
