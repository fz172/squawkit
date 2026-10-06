package dev.fanfly.wingslog.feature.dashboard.api

import kotlinx.datetime.LocalDate

data class LogStats(
  val total: Long,
  val airframe: Long,
  val engine: Long,
  val propeller: Long,
  /**
   * Current value per meter key: the most recent of the logs’ readings and the one set by hand on
   * this card, if any (#1368).
   */
  val readings: Map<String, Double> = emptyMap(),
  /** The newest day any of [readings] was taken, by a log or by hand; null when there are none. */
  val readingsAsOf: LocalDate? = null,
) {
  /**
   * The current reading for a meter key, or null when nothing has recorded one.
   *
   * It used to map three aviation fields by name, so a car's odometer had no answer to give and
   * the dashboard drew a dash — now every declared meter is keyed and this is a lookup (#730).
   *
   * Null rather than 0.0 on purpose: a meter nobody has recorded is not a meter reading zero.
   */
  fun valueFor(meterKey: String): Double? = readings[meterKey]
}
