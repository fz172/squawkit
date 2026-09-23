package dev.fanfly.wingslog.feature.thing.dashboard

import kotlinx.datetime.LocalDate

data class LogStats(
  val total: Long,
  val airframe: Long,
  val engine: Long,
  val propeller: Long,
  /** Current value per meter key, from `MaintenanceOverview.current` (#730). */
  val readings: Map<String, Double> = emptyMap(),
  /** Date of the newest log that recorded any of [readings]; null when none has. */
  val readingsAsOf: LocalDate? = null,
) {
  /**
   * The current reading for a meter key, or null when nothing has recorded one.
   *
   * Reads whatever the overview holds. It used to map three aviation fields by name, so a car's
   * odometer had no answer to give and the dashboard drew a dash — now every declared meter lands
   * in `MaintenanceOverview.current` and this is a lookup (#730).
   *
   * Null rather than 0.0 on purpose: a meter nobody has recorded is not a meter reading zero.
   */
  fun valueFor(meterKey: String): Double? = readings[meterKey]
}
