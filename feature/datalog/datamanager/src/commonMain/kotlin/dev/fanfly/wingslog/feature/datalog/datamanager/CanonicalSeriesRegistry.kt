package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries

/**
 * Source short names to canonical ids (design §6.3). The short name is the vocabulary G3X and
 * G1000 share, which is why it is the key. Unknown columns map to `""` and stay plottable by
 * their raw name — an unmapped column loses the preset and the palette slot, nothing else, so a
 * name whose meaning is not certain is better left out than guessed at.
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
    // The G1000's name for the same trace. `AltMSL` is deliberately absent: it is a third altitude
    // with no canonical id of its own, and mapping it onto one of these would put two different
    // measurements on one series.
    "AltB" to CanonicalSeries.ALT_BARO,
    // A G1000 names its two tanks by side rather than by number.
    "FQtyL" to CanonicalSeries.fuelQty(1),
    "FQtyR" to CanonicalSeries.fuelQty(2),
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
    // Turbines. A G1000 in an SF50 writes these instead of the piston set above, and an airframe
    // with two engines writes each of them twice.
    "Torq" to "torque",
    "NG" to "ng",
    "ITT" to "itt",
    "N1" to "n1",
    "N2" to "n2",
  )

  private val engine = Regex("""^E(\d+) (.+)$""")
  private val engineIndexed = Regex("""^(CHT|EGT|TIT)(\d+)$""")
  private val fuelQty = Regex("""^FQty(\d+)$""")
  // `Volts1` on a G3X, `volt1` on a G1000 — the same reading under two spellings of one name.
  private val volts = Regex("""^[Vv]olts?(\d+)$""")
  private val amps = Regex("""^[Aa]mps?(\d+)$""")

  fun canonicalIdFor(shortName: String): String {
    val key = shortName.trim()
    fixed[key]?.let { return it }
    engine.matchEntire(key)
      ?.let { m ->
        val n = m.groupValues[1].toInt()
        val field = m.groupValues[2]
        engineFields[field]?.let { return CanonicalSeries.engine(n, it) }
        engineIndexed.matchEntire(field)
          ?.let { i ->
            return CanonicalSeries.engine(
              n,
              i.groupValues[1].lowercase(),
              i.groupValues[2].toInt()
            )
          }
        return ""
      }
    fuelQty.matchEntire(key)
      ?.let { return CanonicalSeries.fuelQty(it.groupValues[1].toInt()) }
    volts.matchEntire(key)
      ?.let { return CanonicalSeries.volts(it.groupValues[1].toInt()) }
    amps.matchEntire(key)
      ?.let { return CanonicalSeries.amps(it.groupValues[1].toInt()) }
    return ""
  }
}
