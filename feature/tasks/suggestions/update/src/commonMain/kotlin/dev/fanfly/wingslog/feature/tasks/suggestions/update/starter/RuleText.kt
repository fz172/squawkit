package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.meter
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.TimeRule
import dev.fanfly.wingslog.thing.ThingTemplate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_either
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_days
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_meter
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_month
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_months
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_year
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_years

/**
 * "Every 50 hrs or every year": the rule a suggestion would schedule by, the part worth scanning
 * for. A seasonal rule says nothing here, as the starter pack never did; an on-condition rule
 * shows its own words. Null for a rule with nothing to say.
 */
@Composable
internal fun ruleText(rules: List<InspectionRule>, template: ThingTemplate?): String? {
  val calendar = rules.firstNotNullOfOrNull { it.time_rule }
    ?.let { calendarText(it) }
  val meter = rules.firstNotNullOfOrNull { it.meter_rule }
    ?.takeIf { it.meter_key.isNotEmpty() && it.interval > 0f }
    ?.let {
      stringResource(
        Res.string.starter_rule_every_meter,
        formatInterval(it.interval),
        template.meter(it.meter_key)?.unit_label ?: it.meter_key,
      )
    }
  val onCondition =
    rules.firstNotNullOfOrNull { it.on_condition_rule }?.description?.takeIf { it.isNotBlank() }
  return when {
    meter != null && calendar != null -> stringResource(
      Res.string.starter_rule_either,
      meter,
      calendar
    )

    else -> meter ?: calendar ?: onCondition
  }
}

@Composable
private fun calendarText(rule: TimeRule): String? {
  val months = rule.interval_months + 12 * rule.interval_years
  return when {
    months == 1 -> stringResource(Res.string.starter_rule_every_month)
    months == 12 -> stringResource(Res.string.starter_rule_every_year)
    months > 0 && months % 12 == 0 -> stringResource(
      Res.string.starter_rule_every_years,
      months / 12
    )

    months > 0 -> stringResource(Res.string.starter_rule_every_months, months)
    rule.interval_days > 0 -> stringResource(
      Res.string.starter_rule_every_days,
      rule.interval_days
    )

    else -> null
  }
}

/** 5000 → "5,000"; 7.5 → "7.5". Grouping by hand because `String.format` is not common code. */
private fun formatInterval(value: Float): String {
  val whole = value.toLong()
  if (value != whole.toFloat()) return value.toString()
  return whole.toString()
    .reversed()
    .chunked(3)
    .joinToString(",")
    .reversed()
}
