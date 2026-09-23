package dev.fanfly.wingslog.feature.search.datamanager

/** Acronyms and synonyms, expanded at query time in both directions. */
class SynonymPack(private val entries: List<SynonymEntry>) {
  constructor(vararg entries: SynonymEntry) : this(entries.toList())

  private val forward: Map<String, List<String>> =
    entries.groupBy({ it.word }, { it.expansion })
  private val reverse: Map<String, List<String>> =
    entries.filter { ' ' !in it.expansion }
      .groupBy({ it.expansion }, { it.word })

  fun expansions(token: String): List<String> =
    forward[token].orEmpty() + reverse[token].orEmpty()

  operator fun plus(other: SynonymPack): SynonymPack =
    SynonymPack(entries + other.entries)

  companion object {
    /** Words that mean the same thing here; each expands to all the others. */
    fun group(vararg words: String): Array<SynonymEntry> =
      words.flatMap { w ->
        words.filter { it != w }
          .map { SynonymEntry(w, it) }
      }
        .toTypedArray()
  }
}

val GenericSynonyms = SynonymPack(
  SynonymEntry("inop", "inoperative"),
  SynonymEntry("inop", "not working"),
  SynonymEntry("u/s", "unserviceable"),
  SynonymEntry("batt", "battery"),
  SynonymEntry("mx", "maintenance"),
  SynonymEntry("maint", "maintenance"),
  SynonymEntry("repl", "replaced"),
  SynonymEntry("svc", "service"),
  SynonymEntry("hr", "hour"),
  SynonymEntry("hrs", "hours"),
  SynonymEntry("qty", "quantity"),
  SynonymEntry("temp", "temperature"),
  SynonymEntry("press", "pressure"),
  *SynonymPack.group(
    "check",
    "inspect",
    "inspection",
    "insp",
    "examine",
    "exam",
    "examination"
  ),
)

val AviationSynonyms = SynonymPack(
  SynonymEntry("xpdr", "transponder"),
  SynonymEntry("xpndr", "transponder"),
  SynonymEntry("elt", "emergency locator transmitter"),
  SynonymEntry("mag", "magneto"),
  SynonymEntry("mags", "magnetos"),
  SynonymEntry("prop", "propeller"),
  SynonymEntry("carb", "carburetor"),
  SynonymEntry("alt", "altimeter"),
  SynonymEntry("alt", "alternator"),
  SynonymEntry("ad", "airworthiness directive"),
  SynonymEntry("sb", "service bulletin"),
  SynonymEntry("aog", "aircraft on ground"),
  SynonymEntry("ia", "inspection authorization"),
  SynonymEntry("a&p", "airframe and powerplant"),
  SynonymEntry("tso", "time since overhaul"),
  SynonymEntry("smoh", "since major overhaul"),
  SynonymEntry("tbo", "time between overhaul"),
  SynonymEntry("tt", "total time"),
  SynonymEntry("ttaf", "total time airframe"),
  SynonymEntry("gph", "gallons per hour"),
  SynonymEntry("cht", "cylinder head temperature"),
  SynonymEntry("egt", "exhaust gas temperature"),
  SynonymEntry("rpm", "revolutions per minute"),
  SynonymEntry("vor", "vhf omnidirectional range"),
  SynonymEntry("ils", "instrument landing system"),
  SynonymEntry("gps", "global positioning system"),
  SynonymEntry("ahrs", "attitude heading reference system"),
  SynonymEntry("adsb", "ads-b"),
  SynonymEntry("pfd", "primary flight display"),
  SynonymEntry("mfd", "multi function display"),
  SynonymEntry("oat", "outside air temperature"),
  SynonymEntry("aoa", "angle of attack"),
  SynonymEntry("ias", "indicated airspeed"),
  SynonymEntry("tas", "true airspeed"),
  SynonymEntry("oil", "lubricant"),
  SynonymEntry("strut", "oleo"),
  SynonymEntry("nosewheel", "nose wheel"),
  SynonymEntry("spinner", "prop spinner"),
)
