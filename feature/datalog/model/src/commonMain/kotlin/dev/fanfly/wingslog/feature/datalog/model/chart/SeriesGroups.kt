package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/**
 * One row of the series list: a single series, or a per-cylinder family (EGT1…EGTn, CHT1…CHTn,
 * TIT1…TITn) bundled under one label so picking it charts every cylinder at once.
 */
data class SeriesGroup(
  val label: String,
  val members: List<DataLogSeries>,
  val title: String = label,
) {
  val keys: List<SeriesKey> get() = members.map { SeriesKey(it.column) }
  val first: DataLogSeries get() = members.first()
  val isBundle: Boolean get() = members.size > 1
}

// Every `]` is escaped: Kotlin/JS compiles regexes with the `u` flag, where a bare `]` is a syntax
// error ("Lone quantifier brackets") even though the JVM accepts it.
private val CylinderFamily = Regex("""^engine\[(\d+)\]\.(egt|cht|tit)\[\d+\]$""")
private val EngineCanonical = Regex("""^engine\[(\d+)\]\.(.+)$""")
private val IndexedEngineField = Regex("""^(egt|cht|tit)\[(\d+)\]$""")
private val FuelQtyCanonical = Regex("""^fuel\.qty\[(\d+)\]$""")
private val VoltsCanonical = Regex("""^elec\.volts\[(\d+)\]$""")
private val AmpsCanonical = Regex("""^elec\.amps\[(\d+)\]$""")

private val WordBoundaryRegex = Regex("""\b[A-Za-z]+\b""")

private val CanonicalTitles: Map<String, String> = mapOf(
  CanonicalSeries.POSITION to "Position",
  CanonicalSeries.LATITUDE to "Latitude",
  CanonicalSeries.LONGITUDE to "Longitude",
  CanonicalSeries.IAS to "Indicated Airspeed",
  CanonicalSeries.TAS to "True Airspeed",
  CanonicalSeries.GROUND_SPEED to "Ground Speed",
  CanonicalSeries.ALT_GPS to "GPS Altitude",
  CanonicalSeries.ALT_PRESSURE to "Pressure Altitude",
  CanonicalSeries.ALT_BARO to "Barometric Altitude",
  CanonicalSeries.VERTICAL_SPEED to "Vertical Speed",
  CanonicalSeries.AGL to "Height Above Ground",
  CanonicalSeries.PITCH to "Pitch",
  CanonicalSeries.ROLL to "Roll",
  CanonicalSeries.G_NORMAL to "Normal Acceleration",
  CanonicalSeries.G_LATERAL to "Lateral Acceleration",
  CanonicalSeries.OAT to "Outside Air Temperature",
  CanonicalSeries.HEADING to "Magnetic Heading",
)

private val EngineFieldTitles: Map<String, String> = mapOf(
  "oil_press" to "Oil Pressure",
  "oil_temp" to "Oil Temperature",
  "map" to "Manifold Pressure",
  "fuel_press" to "Fuel Pressure",
  "fuel_flow" to "Fuel Flow",
  "power_pct" to "Engine Power",
  "rpm" to "RPM",
  "carb_temp" to "Carburetor Temperature",
  "coolant_temp" to "Coolant Temperature",
  "itt" to "Interstage Turbine Temperature",
  "torque" to "Torque",
  "ng" to "Gas Generator Speed (NG)",
  "n1" to "N1",
  "n2" to "N2",
)

private val FullNameReplacements: Map<String, String> = mapOf(
  "Oil Press" to "Oil Pressure",
  "Oil Temp" to "Oil Temperature",
  "Manifold Press" to "Manifold Pressure",
  "Fuel Press" to "Fuel Pressure",
  "Outside Air Temp" to "Outside Air Temperature",
  "Coolant Temp" to "Coolant Temperature",
  "Man Temp" to "Manifold Temperature",
  "Feed Psi" to "Feed Pressure",
  "Fuel Qty L" to "Fuel Quantity (Left)",
  "Fuel Qty R" to "Fuel Quantity (Right)",
  "Fuel Qty 1" to "Fuel Quantity 1",
  "Fuel Qty 2" to "Fuel Quantity 2",
  "FQtyL" to "Fuel Quantity (Left)",
  "FQtyR" to "Fuel Quantity (Right)",
  "FQty1" to "Fuel Quantity 1",
  "FQty2" to "Fuel Quantity 2",
  "Efis Bkup Volts" to "EFIS Backup Volts",
  "Nav Bkup Volts" to "NAV Backup Volts",
  "Batt Amps" to "Battery Amps",
  "Vertical Accel" to "Vertical Acceleration",
  "Lateral Accel" to "Lateral Acceleration",
  "Baro Setting" to "Barometric Setting",
  "Baro Altitude" to "Barometric Altitude",
  "GndSpd" to "Ground Speed",
  "VSpd" to "Vertical Speed",
  "AltB" to "Barometric Altitude",
  "AltInd" to "Barometric Altitude",
  "AltP" to "Pressure Altitude",
  "PALT" to "Pressure Altitude",
  "AltGPS" to "GPS Altitude",
  "AltMSL" to "MSL Altitude",
  "LatAc" to "Lateral Acceleration",
  "NormAc" to "Normal Acceleration",
  "HDG" to "Magnetic Heading",
  "TRK" to "Ground Track",
  "OAT" to "Outside Air Temperature",
  "IAS" to "Indicated Airspeed",
  "TAS" to "True Airspeed",
  "MAP" to "Manifold Pressure",
  "OilP" to "Oil Pressure",
  "OilT" to "Oil Temperature",
  "%Pwr" to "Engine Power",
  "FPres" to "Fuel Pressure",
  "FFlow" to "Fuel Flow",
  "FF" to "Fuel Flow",
  "MBUS" to "Main Bus Volts",
  "EBUS" to "Essential Bus Volts",
)

private val WordReplacements: Map<String, String> = mapOf(
  "Press" to "Pressure",
  "Temp" to "Temperature",
  "Qty" to "Quantity",
  "Bkup" to "Backup",
  "Batt" to "Battery",
  "Accel" to "Acceleration",
  "Spd" to "Speed",
  "Psi" to "Pressure",
  "Baro" to "Barometric",
  "Alt" to "Altitude",
)

/**
 * Full unabbreviated title for the filter panel (e.g. "Oil Pressure" instead of "Oil Press" or "E1 OilP").
 * Strips "E1 " on a single-engine aircraft while retaining "E1 "/"E2 " on a multi-engine aircraft.
 */
fun DataLogSeries.expandedTitle(multiEngine: Boolean = false): String {
  CanonicalTitles[canonical_id]?.let { return it }

  EngineCanonical.matchEntire(canonical_id)?.let { m ->
    val engineNum = m.groupValues[1]
    val field = m.groupValues[2]
    val baseTitle = EngineFieldTitles[field]
      ?: IndexedEngineField.matchEntire(field)?.let { idx ->
        "${idx.groupValues[1].uppercase()} ${idx.groupValues[2]}"
      }
    if (baseTitle != null) {
      return if (multiEngine) "E$engineNum $baseTitle" else baseTitle
    }
  }

  FuelQtyCanonical.matchEntire(canonical_id)?.let { m ->
    val index = m.groupValues[1]
    val isLeft = short_name.endsWith("L", ignoreCase = true) ||
      name.contains(" L", ignoreCase = true) ||
      name.contains("Left", ignoreCase = true) ||
      short_name.contains("FQtyL", ignoreCase = true)
    val isRight = short_name.endsWith("R", ignoreCase = true) ||
      name.contains(" R", ignoreCase = true) ||
      name.contains("Right", ignoreCase = true) ||
      short_name.contains("FQtyR", ignoreCase = true)
    return when {
      isLeft -> "Fuel Quantity (Left)"
      isRight -> "Fuel Quantity (Right)"
      else -> "Fuel Quantity $index"
    }
  }

  if (!name.contains("Bkup", ignoreCase = true) && !short_name.contains("Bkup", ignoreCase = true)) {
    VoltsCanonical.matchEntire(canonical_id)?.let { return "Volts ${it.groupValues[1]}" }
    AmpsCanonical.matchEntire(canonical_id)?.let { return "Amps ${it.groupValues[1]}" }
  }

  val raw = name.ifBlank { short_name }
  val unprefixed = if (!multiEngine) raw.removePrefix("E1 ") else raw
  val trimmed = unprefixed.trim()

  FullNameReplacements[trimmed]?.let { return it }

  return WordBoundaryRegex.replace(unprefixed) { match ->
    WordReplacements[match.value] ?: match.value
  }
}

/**
 * The catalogue as list rows, in catalogue order. Per-cylinder series of one engine group by their
 * canonical id (`engine[1].egt[3]` joins `engine[1].egt`), so the bundle works the same on every
 * format the registry maps. A family with a single member stays a plain row, and series with no
 * canonical id are never grouped.
 */
fun List<DataLogSeries>.seriesGroups(multiEngine: Boolean = isMultiEngine()): List<SeriesGroup> {
  val families = LinkedHashMap<String, MutableList<DataLogSeries>>()
  val order = ArrayList<Pair<String?, DataLogSeries>>()
  for (series in this) {
    val match = CylinderFamily.matchEntire(series.canonical_id)
    if (match == null) {
      order += null to series
      continue
    }
    val familyKey = "${match.groupValues[1]}:${match.groupValues[2]}"
    val members = families.getOrPut(familyKey) {
      order += familyKey to series
      mutableListOf()
    }
    members += series
  }
  return order.map { (familyKey, series) ->
    val members = familyKey?.let { families.getValue(it) }
    if (members == null || members.size < 2) {
      SeriesGroup(
        label = series.chipLabel(multiEngine),
        members = listOf(series),
        title = series.expandedTitle(multiEngine),
      )
    } else {
      val match = CylinderFamily.matchEntire(series.canonical_id)!!
      val field = match.groupValues[2].uppercase()
      val label = if (multiEngine) "E${match.groupValues[1]} $field" else field
      SeriesGroup(
        label = label,
        members = members,
        title = "$label (${members.size})",
      )
    }
  }
}
