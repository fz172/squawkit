package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries

/**
 * Source short names to canonical ids (design §6.3). The short name is the vocabulary G3X and
 * G1000 share, which is why it is the key. Unknown columns map to `""` and stay plottable by
 * their raw name.
 */
object CanonicalSeriesRegistry {

  private val fixed: Map<String, String> = mapOf(
    "Latitude" to CanonicalSeries.LATITUDE,
    "Longitude" to CanonicalSeries.LONGITUDE,
    "IAS" to CanonicalSeries.IAS,
    "TAS" to CanonicalSeries.TAS,
    "AltGPS" to CanonicalSeries.ALT_GPS,
    "AltP" to CanonicalSeries.ALT_PRESSURE,
    "AltInd" to CanonicalSeries.ALT_BARO,
    "VSpd" to CanonicalSeries.VERTICAL_SPEED,
    "GndSpd" to CanonicalSeries.GROUND_SPEED,
    "AGL" to CanonicalSeries.AGL,
    "Pitch" to CanonicalSeries.PITCH,
    "Roll" to CanonicalSeries.ROLL,
    "NormAc" to CanonicalSeries.G_NORMAL,
    "LatAc" to CanonicalSeries.G_LATERAL,
    "OAT" to CanonicalSeries.OAT,
    "HDG" to CanonicalSeries.HEADING,
  )

  private val engineFields: Map<String, String> = mapOf(
    "RPM" to "rpm",
    "MAP" to "map",
    "OilP" to "oil_press",
    "OilT" to "oil_temp",
    "FFlow" to "fuel_flow",
    "%Pwr" to "power_pct",
    "FPres" to "fuel_press",
  )

  private val engine = Regex("""^E(\d+) (.+)$""")
  private val engineIndexed = Regex("""^(CHT|EGT)(\d+)$""")
  private val fuelQty = Regex("""^FQty(\d+)$""")
  private val volts = Regex("""^Volts(\d+)$""")
  private val amps = Regex("""^Amps(\d+)$""")

  fun canonicalIdFor(shortName: String): String {
    val key = shortName.trim()
    fixed[key]?.let { return it }
    engine.matchEntire(key)?.let { m ->
      val n = m.groupValues[1].toInt()
      val field = m.groupValues[2]
      engineFields[field]?.let { return CanonicalSeries.engine(n, it) }
      engineIndexed.matchEntire(field)?.let { i ->
        return CanonicalSeries.engine(n, i.groupValues[1].lowercase(), i.groupValues[2].toInt())
      }
      return ""
    }
    fuelQty.matchEntire(key)?.let { return CanonicalSeries.fuelQty(it.groupValues[1].toInt()) }
    volts.matchEntire(key)?.let { return CanonicalSeries.volts(it.groupValues[1].toInt()) }
    amps.matchEntire(key)?.let { return CanonicalSeries.amps(it.groupValues[1].toInt()) }
    return ""
  }
}
