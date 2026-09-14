package dev.fanfly.wingslog.feature.datalog.model

/**
 * The namespaced ids presets, defaults and derived rules speak (design §6.3). A recorder's own
 * column names map onto these in the datamanager's registry, so a rule written against
 * [GROUND_SPEED] survives a format change.
 */
object CanonicalSeries {
  const val POSITION = "position"
  const val LATITUDE = "position.lat"
  const val LONGITUDE = "position.lon"
  const val IAS = "flight.ias"
  const val TAS = "flight.tas"
  const val ALT_GPS = "flight.alt_gps"
  const val ALT_PRESSURE = "flight.alt_pressure"
  const val ALT_BARO = "flight.alt_baro"
  const val VERTICAL_SPEED = "flight.vs"
  const val GROUND_SPEED = "flight.ground_speed"
  const val AGL = "flight.agl"
  const val PITCH = "flight.pitch"
  const val ROLL = "flight.roll"
  const val G_NORMAL = "flight.g_normal"
  const val G_LATERAL = "flight.g_lateral"
  const val OAT = "air.oat"
  const val HEADING = "nav.heading"

  fun engine(n: Int, field: String): String = "engine[$n].$field"
  fun engine(n: Int, field: String, index: Int): String = "engine[$n].$field[$index]"
  fun fuelQty(n: Int): String = "fuel.qty[$n]"
  fun volts(n: Int): String = "elec.volts[$n]"
  fun amps(n: Int): String = "elec.amps[$n]"
}
