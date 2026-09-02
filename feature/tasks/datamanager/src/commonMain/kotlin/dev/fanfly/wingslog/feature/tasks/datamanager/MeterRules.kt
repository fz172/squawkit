package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterReading

/**
 * The meter an aviation card measures against when nothing names one.
 *
 * `EngineHourRule` named its meter in its type rather than carrying it, so which one it meant came
 * from the card's component: an airframe task tracked airframe time, everything else engine hours.
 * The rule is gone (#761) but the default outlives it — a task created with no meter rule still
 * has to force an override into *some* meter, and this is the one an aviation card has always
 * meant (#759).
 */
fun MaintenanceTask.defaultMeterKey(): String =
  if (component == ComponentType.COMPONENT_AIRFRAME) {
    MeterKeys.AIRFRAME_HOURS
  } else {
    MeterKeys.ENGINE_HOURS
  }

/**
 * The meter key and interval [rule] schedules against, or null when it is not a meter rule.
 *
 * A `MeterRule` carries its own key — that is the whole point, and what lets a car be scheduled on
 * mileage.
 */
fun MaintenanceTask.meterIntervalFor(rule: InspectionRule): Pair<String, Float>? =
  rule.meter_rule
    ?.takeIf { it.meter_key.isNotEmpty() && it.interval > 0f }
    ?.let { it.meter_key to it.interval }

/**
 * The forced due value this task carries, and the meter it is measured in, or null when no
 * override is set (#759).
 *
 * An override with a value but no key still falls back to [defaultMeterKey]: the key is a string,
 * so "unset" and "empty" are the same thing, and an aviation override is what an empty one means.
 */
fun MaintenanceTask.forcedDueMeter(): Pair<String, Float>? =
  force_due_meter
    ?.takeIf { it.value_ > 0.0 }
    ?.let { forced ->
      (forced.meter_key.takeIf { it.isNotEmpty() }
        ?: defaultMeterKey()) to forced.value_.toFloat()
    }

/** This task with its forced due value set to [value] in [meterKey], or cleared when null. */
fun MaintenanceTask.withForcedDueMeter(
  meterKey: String,
  value: Float?,
): MaintenanceTask = copy(
  force_due_meter = value?.let {
    MeterReading(meter_key = meterKey, value_ = it.toDouble())
  },
)

/**
 * The meter [rules] schedule against, for a card whose component is [component].
 *
 * A forced override is measured in the same meter the rules are, so this is what a caller setting
 * one has to know. Falls back to [defaultMeterKey] when no rule names a meter.
 */
fun meterKeyFor(component: ComponentType, rules: List<InspectionRule>): String {
  rules.forEach { rule ->
    rule.meter_rule?.meter_key?.takeIf { it.isNotEmpty() }
      ?.let { return it }
  }
  return MaintenanceTask(component = component).defaultMeterKey()
}
