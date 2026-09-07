package dev.fanfly.wingslog.feature.search.datamanager

/** Acronyms and synonyms as `word to expansion` pairs, expanded at query time in both directions. */
class SynonymPack(private val entries: List<Pair<String, String>>) {
  constructor(vararg entries: Pair<String, String>) : this(entries.toList())

  private val forward: Map<String, List<String>> = entries.groupBy({ it.first }, { it.second })
  private val reverse: Map<String, List<String>> =
    entries.filter { ' ' !in it.second }.groupBy({ it.second }, { it.first })

  fun expansions(token: String): List<String> = forward[token].orEmpty() + reverse[token].orEmpty()

  operator fun plus(other: SynonymPack): SynonymPack = SynonymPack(entries + other.entries)

  companion object {
    /** Words that mean the same thing here; each expands to all the others. */
    fun group(vararg words: String): Array<Pair<String, String>> =
      words.flatMap { w -> words.filter { it != w }.map { w to it } }.toTypedArray()
  }
}

val GenericSynonyms = SynonymPack(
  "inop" to "inoperative",
  "inop" to "not working",
  "u/s" to "unserviceable",
  "batt" to "battery",
  "mx" to "maintenance",
  "maint" to "maintenance",
  "repl" to "replaced",
  "svc" to "service",
  "hr" to "hour",
  "hrs" to "hours",
  "qty" to "quantity",
  "temp" to "temperature",
  "press" to "pressure",
  *SynonymPack.group("check", "inspect", "inspection", "insp", "examine", "exam", "examination"),
)

val AviationSynonyms = SynonymPack(
  "xpdr" to "transponder",
  "xpndr" to "transponder",
  "elt" to "emergency locator transmitter",
  "mag" to "magneto",
  "mags" to "magnetos",
  "prop" to "propeller",
  "carb" to "carburetor",
  "alt" to "altimeter",
  "alt" to "alternator",
  "ad" to "airworthiness directive",
  "sb" to "service bulletin",
  "aog" to "aircraft on ground",
  "ia" to "inspection authorization",
  "a&p" to "airframe and powerplant",
  "tso" to "time since overhaul",
  "smoh" to "since major overhaul",
  "tbo" to "time between overhaul",
  "tt" to "total time",
  "ttaf" to "total time airframe",
  "gph" to "gallons per hour",
  "cht" to "cylinder head temperature",
  "egt" to "exhaust gas temperature",
  "rpm" to "revolutions per minute",
  "vor" to "vhf omnidirectional range",
  "ils" to "instrument landing system",
  "gps" to "global positioning system",
  "ahrs" to "attitude heading reference system",
  "adsb" to "ads-b",
  "pfd" to "primary flight display",
  "mfd" to "multi function display",
  "oat" to "outside air temperature",
  "aoa" to "angle of attack",
  "ias" to "indicated airspeed",
  "tas" to "true airspeed",
  "oil" to "lubricant",
  "strut" to "oleo",
  "nosewheel" to "nose wheel",
  "spinner" to "prop spinner",
)
