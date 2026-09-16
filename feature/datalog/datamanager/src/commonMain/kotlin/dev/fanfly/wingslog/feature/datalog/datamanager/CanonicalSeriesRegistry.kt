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

    // --- Dynon SkyView ---
    // A SkyView has no short names at all, so its full column name is the key. They are spelled out
    // where a Garmin abbreviates, which is the whole of the difference.
    "Indicated Airspeed" to CanonicalSeries.IAS,
    "True Airspeed" to CanonicalSeries.TAS,
    "Ground Speed" to CanonicalSeries.GROUND_SPEED,
    "GPS Altitude" to CanonicalSeries.ALT_GPS,
    "Pressure Altitude" to CanonicalSeries.ALT_PRESSURE,
    "Vertical Speed" to CanonicalSeries.VERTICAL_SPEED,
    "Magnetic Heading" to CanonicalSeries.HEADING,
    "Vertical Accel" to CanonicalSeries.G_NORMAL,
    "Lateral Accel" to CanonicalSeries.G_LATERAL,
    "Oil Pressure" to CanonicalSeries.engine(1, "oil_press"),
    "Oil Temp" to CanonicalSeries.engine(1, "oil_temp"),
    "Manifold Pressure" to CanonicalSeries.engine(1, "map"),
    "Fuel Pressure" to CanonicalSeries.engine(1, "fuel_press"),
    "Percent Power" to CanonicalSeries.engine(1, "power_pct"),
    // Left and right, not one and two: a SkyView names an engine by which side it is on.
    "RPM L" to CanonicalSeries.engine(1, "rpm"),
    "RPM R" to CanonicalSeries.engine(2, "rpm"),
    "Fuel Flow 1" to CanonicalSeries.engine(1, "fuel_flow"),
    "Fuel Flow 2" to CanonicalSeries.engine(2, "fuel_flow"),
    "Fuel Level L" to CanonicalSeries.fuelQty(1),
    "Fuel Level R" to CanonicalSeries.fuelQty(2),
    "Volts 1" to CanonicalSeries.volts(1),
    "Volts 2" to CanonicalSeries.volts(2),
    "Amps" to CanonicalSeries.amps(1),

    // --- Avidyne Entegra ---
    // Terse where the others are not: four characters at most, and no engine number because the
    // format only ever described a single-engine airframe.
    "OILT" to CanonicalSeries.engine(1, "oil_temp"),
    "OILP" to CanonicalSeries.engine(1, "oil_press"),
    "RPM" to CanonicalSeries.engine(1, "rpm"),
    "MAP" to CanonicalSeries.engine(1, "map"),
    "FF" to CanonicalSeries.engine(1, "fuel_flow"),
    "TIT" to CanonicalSeries.engine(1, "tit", 1),
    "PALT" to CanonicalSeries.ALT_PRESSURE,
    "MBUS" to CanonicalSeries.volts(1),
    "EBUS" to CanonicalSeries.volts(2),
    "AMP1" to CanonicalSeries.amps(1),
    "AMP2" to CanonicalSeries.amps(2),
    "LAT" to CanonicalSeries.LATITUDE,
    "LON" to CanonicalSeries.LONGITUDE,
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

  // A SkyView spells its cylinder banks with a space and no engine number: "CHT 3", never "E1 CHT3".
  // Single-engine is the only airframe that writes them this way, so they are engine one's.
  private val dynonIndexed = Regex("""^(CHT|EGT|TIT) (\d+)$""")

  // Avidyne is terser still: E4 is exhaust gas on cylinder four and C4 is that cylinder's head.
  // Unambiguous because a Garmin never writes a bare `E4` — its engine columns are `E1 <field>`,
  // which needs the space.
  private val avidyneIndexed = Regex("""^([EC])(\d+)$""")
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
    avidyneIndexed.matchEntire(key)
      ?.let {
        val field = if (it.groupValues[1] == "E") "egt" else "cht"
        return CanonicalSeries.engine(1, field, it.groupValues[2].toInt())
      }
    dynonIndexed.matchEntire(key)
      ?.let {
        return CanonicalSeries.engine(
          1,
          it.groupValues[1].lowercase(),
          it.groupValues[2].toInt(),
        )
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
