package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.MaintenanceTask

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
