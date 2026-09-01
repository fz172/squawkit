package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterReading

/**
 * The meter this card's legacy rules are measured against.
 *
 * `EngineHourRule` names the meter in its type rather than carrying it, so which meter it *meant*
 * came from the card's component: an airframe task tracked airframe time, everything else engine
 * hours. Preserved exactly here so an aviation task computes as it always has (#759).
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
 * mileage. An `EngineHourRule` falls back to [defaultMeterKey], so nothing already in the field
 * changes behaviour.
 */
fun MaintenanceTask.meterIntervalFor(rule: InspectionRule): Pair<String, Float>? {
  rule.meter_rule?.let { meter ->
    if (meter.meter_key.isNotEmpty() && meter.interval > 0f) {
      return meter.meter_key to meter.interval
    }
  }
  rule.engine_hour_rule?.let { engine ->
    if (engine.interval_hours > 0f) return defaultMeterKey() to engine.interval_hours
  }
  return null
}

/**
 * The forced due value this task carries, and the meter it is measured in.
 *
 * Reads `force_due_meter` first and falls back to `force_due_engine_hour`, which could only ever
 * mean the meter [defaultMeterKey] names. Null when no override is set (#759).
 */
fun MaintenanceTask.forcedDueMeter(): Pair<String, Float>? {
  force_due_meter?.let { forced ->
    if (forced.value_ > 0.0) {
      val key = forced.meter_key.takeIf { it.isNotEmpty() } ?: defaultMeterKey()
      return key to forced.value_.toFloat()
    }
  }
  if (force_due_engine_hour > 0f) return defaultMeterKey() to force_due_engine_hour
  return null
}

/**
 * This task with its forced due value set to [value] in [meterKey], or cleared when null.
 *
 * **Writes both fields.** The keyed one is what readers prefer; the legacy float is kept in step so
 * a client that predates `force_due_meter` still sees the override rather than losing it — the same
 * bargain the overview's aviation fields make. Clearing zeroes both, or an old build would keep
 * showing an override the user removed.
 */
fun MaintenanceTask.withForcedDueMeter(
  meterKey: String,
  value: Float?,
): MaintenanceTask = copy(
  force_due_meter = value?.let {
    MeterReading(meter_key = meterKey, value_ = it.toDouble())
  },
  force_due_engine_hour = value ?: 0f,
)

/**
 * The meter [rules] schedule against, for a card whose component is [component].
 *
 * A forced override is measured in the same meter the rules are, so this is what a caller setting
 * one has to know. Falls back to the component's default when no rule names a meter — the value is
 * then in whatever `EngineHourRule` always meant.
 */
fun meterKeyFor(component: ComponentType, rules: List<InspectionRule>): String {
  rules.forEach { rule ->
    rule.meter_rule?.meter_key?.takeIf { it.isNotEmpty() }?.let { return it }
  }
  return MaintenanceTask(component = component).defaultMeterKey()
}
