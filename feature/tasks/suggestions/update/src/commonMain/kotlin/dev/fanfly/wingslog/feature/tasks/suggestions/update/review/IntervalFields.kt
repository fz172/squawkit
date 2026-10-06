package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.template.meter
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.thing.ThingTemplate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_field_every
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_field_or
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_unit_days
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_unit_months

/** A value typed into one of a row's interval fields. */
internal sealed interface IntervalEdit {
  data class Meter(val interval: Float) : IntervalEdit
  data class Months(val months: Int) : IntervalEdit
  data class Days(val days: Int) : IntervalEdit
}

/**
 * The row's intervals, open to change in place (1d): "Every hrs 50", "Or months 12". One field
 * per rule that has a number; a seasonal or on-condition rule has none to change here.
 */
@Composable
internal fun IntervalFields(
  rules: List<InspectionRule>,
  template: ThingTemplate?,
  enabled: Boolean,
  onInterval: (IntervalEdit) -> Unit,
  modifier: Modifier = Modifier,
) {
  val meter = rules.firstNotNullOfOrNull { it.meter_rule }
    ?.takeIf { it.meter_key.isNotEmpty() }
  val time = rules.firstNotNullOfOrNull { it.time_rule }
  val days = time?.interval_days?.takeIf { it > 0 }
  val months = time?.let { it.interval_months + 12 * it.interval_years }
    ?.takeIf { it > 0 }
  if (meter == null && days == null && months == null) return
  // The calendar field reads "Or months" after a meter's, as the rule does: whichever comes first.
  val calendarLabel =
    if (meter != null) Res.string.suggestion_field_or else Res.string.suggestion_field_every
  val calendarUnit = stringResource(
    if (days != null) Res.string.suggestion_unit_days else Res.string.suggestion_unit_months,
  )
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
  ) {
    if (meter != null) {
      NumberField(
        label = stringResource(
          Res.string.suggestion_field_every,
          template.meter(meter.meter_key)?.unit_label ?: meter.meter_key,
        ),
        initial = meter.interval.toFieldText(),
        max = MAX_METER_INTERVAL,
        decimal = true,
        enabled = enabled,
        onNumber = { onInterval(IntervalEdit.Meter(it.toFloat())) },
        modifier = Modifier.weight(1f),
      )
    }
    if (days != null) {
      NumberField(
        label = stringResource(calendarLabel, calendarUnit),
        initial = days.toString(),
        max = MAX_DAYS,
        decimal = false,
        enabled = enabled,
        onNumber = { onInterval(IntervalEdit.Days(it.toInt())) },
        modifier = Modifier.weight(1f),
      )
    } else if (months != null) {
      NumberField(
        label = stringResource(calendarLabel, calendarUnit),
        initial = months.toString(),
        max = MAX_MONTHS,
        decimal = false,
        enabled = enabled,
        onNumber = { onInterval(IntervalEdit.Months(it.toInt())) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

/**
 * A field for one number, in mono. Each value that reads as a number above zero and up to [max] is
 * passed on. Anything else (nothing, zero, too large) is marked as an error and passed nowhere,
 * and leaving the field puts back the last number passed on, so the field never shows something
 * other than what the row will add.
 */
@Composable
private fun NumberField(
  label: String,
  initial: String,
  max: Double,
  decimal: Boolean,
  enabled: Boolean,
  onNumber: (Double) -> Unit,
  modifier: Modifier = Modifier,
) {
  var text by remember { mutableStateOf(initial) }
  var lastValid by remember { mutableStateOf(initial) }
  OutlinedTextField(
    value = text,
    onValueChange = { typed ->
      val kept = typed.filter { it.isDigit() || (decimal && it == '.') }
      text = kept
      parseInterval(kept, max)?.let {
        lastValid = kept
        onNumber(it)
      }
    },
    label = {
      Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall
      )
    },
    textStyle = WingslogTypography.dataMedium,
    singleLine = true,
    enabled = enabled,
    isError = parseInterval(text, max) == null,
    keyboardOptions = KeyboardOptions(
      keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
    ),
    modifier = modifier.onFocusChanged {
      if (!it.isFocused && parseInterval(text, max) == null) text = lastValid
    },
  )
}

/** The number [text] reads as, when it is above zero and no more than [max]; null otherwise. */
internal fun parseInterval(text: String, max: Double): Double? =
  text.toDoubleOrNull()?.takeIf { it > 0 && it <= max }

/** 50.0 → "50", 7.5 → "7.5": a meter interval as the field shows it, with no stray ".0". */
internal fun Float.toFieldText(): String {
  val whole = toLong()
  return if (this == whole.toFloat()) whole.toString() else toString()
}

/** The most an interval field takes: a century, or a meter no Thing reaches. */
internal const val MAX_MONTHS = 1_200.0
internal const val MAX_DAYS = 36_500.0
internal const val MAX_METER_INTERVAL = 1_000_000.0
