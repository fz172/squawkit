package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/**
 * One row of the series list: a single series, or a per-cylinder family (EGT1…EGTn, CHT1…CHTn,
 * TIT1…TITn) bundled under one label so picking it charts every cylinder at once.
 */
data class SeriesGroup(
  val label: String,
  val members: List<DataLogSeries>,
) {
  val keys: List<SeriesKey> get() = members.map { SeriesKey(it.column) }
  val first: DataLogSeries get() = members.first()
  val isBundle: Boolean get() = members.size > 1
}

// Every `]` is escaped: Kotlin/JS compiles regexes with the `u` flag, where a bare `]` is a syntax
// error ("Lone quantifier brackets") even though the JVM accepts it.
private val CylinderFamily = Regex("""^engine\[(\d+)\]\.(egt|cht|tit)\[\d+\]$""")

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
      SeriesGroup(series.chipLabel(multiEngine), listOf(series))
    } else {
      val match = CylinderFamily.matchEntire(series.canonical_id)!!
      val field = match.groupValues[2].uppercase()
      val label = if (multiEngine) "E${match.groupValues[1]} $field" else field
      SeriesGroup(label, members)
    }
  }
}
