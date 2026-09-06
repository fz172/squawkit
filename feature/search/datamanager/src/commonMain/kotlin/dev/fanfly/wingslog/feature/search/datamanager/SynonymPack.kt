package dev.fanfly.wingslog.feature.search.datamanager

/** Acronyms and synonyms, expanded at query time in both directions for single-word targets. */
class SynonymPack(private val forward: Map<String, List<String>>) {
  private val reverse: Map<String, List<String>> = buildMap<String, MutableList<String>> {
    forward.forEach { (key, targets) ->
      targets.filter { ' ' !in it }.forEach { getOrPut(it) { mutableListOf() }.add(key) }
    }
  }

  fun expansions(token: String): List<String> = forward[token].orEmpty() + reverse[token].orEmpty()

  operator fun plus(other: SynonymPack): SynonymPack =
    SynonymPack((forward.keys + other.forward.keys).associateWith { forward[it].orEmpty() + other.forward[it].orEmpty() })
}

val GenericSynonyms = SynonymPack(
  mapOf(
    "inop" to listOf("inoperative", "not working"),
    "u/s" to listOf("unserviceable"),
    "batt" to listOf("battery"),
    "mx" to listOf("maintenance"),
    "maint" to listOf("maintenance"),
    "insp" to listOf("inspection"),
    "repl" to listOf("replaced"),
    "svc" to listOf("service"),
    "hr" to listOf("hour"),
    "hrs" to listOf("hours"),
    "qty" to listOf("quantity"),
    "temp" to listOf("temperature"),
    "press" to listOf("pressure"),
  ),
)

val AviationSynonyms = SynonymPack(
  mapOf(
    "xpdr" to listOf("transponder"),
    "xpndr" to listOf("transponder"),
    "elt" to listOf("emergency locator transmitter"),
    "mag" to listOf("magneto"),
    "mags" to listOf("magnetos"),
    "prop" to listOf("propeller"),
    "carb" to listOf("carburetor"),
    "alt" to listOf("altimeter", "alternator"),
    "ad" to listOf("airworthiness directive"),
    "sb" to listOf("service bulletin"),
    "aog" to listOf("aircraft on ground"),
    "ia" to listOf("inspection authorization"),
    "a&p" to listOf("airframe and powerplant"),
    "tso" to listOf("time since overhaul"),
    "smoh" to listOf("since major overhaul"),
    "tbo" to listOf("time between overhaul"),
    "tt" to listOf("total time"),
    "ttaf" to listOf("total time airframe"),
    "gph" to listOf("gallons per hour"),
    "cht" to listOf("cylinder head temperature"),
    "egt" to listOf("exhaust gas temperature"),
    "rpm" to listOf("revolutions per minute"),
    "vor" to listOf("vhf omnidirectional range"),
    "ils" to listOf("instrument landing system"),
    "gps" to listOf("global positioning system"),
    "ahrs" to listOf("attitude heading reference system"),
    "adsb" to listOf("ads-b"),
    "pfd" to listOf("primary flight display"),
    "mfd" to listOf("multi function display"),
    "oat" to listOf("outside air temperature"),
    "aoa" to listOf("angle of attack"),
    "ias" to listOf("indicated airspeed"),
    "tas" to listOf("true airspeed"),
    "oil" to listOf("lubricant"),
    "strut" to listOf("oleo"),
    "nosewheel" to listOf("nose wheel"),
    "spinner" to listOf("prop spinner"),
  ),
)
