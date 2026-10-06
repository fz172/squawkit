package dev.fanfly.wingslog.core.nav

/**
 * How the suggestions screen ([Screen.StarterPack]) was opened (task population design §9.1). The
 * route carries [wire], a lowercase word, so links read as before the enum existed.
 */
enum class SuggestionsMode(val wire: String) {
  /** The empty task list: the curated list, with the AI button. The default. */
  STARTER("starter"),

  /** The task list's *Suggest tasks*: the same, asked for from a list that has tasks. */
  SUGGEST("suggest"),

  /** *From a document*: the sources sheet opens by itself, with the picker or a given file. */
  DOCUMENT("document"),

  /**
   * *Suggest* on the Add Tasks sheet: the model run starts at once, with the documents picked
   * there ([Screen.SUGGESTIONS_DOCUMENT], a list), over the curated list it carries.
   */
  ADD("add"),
  ;

  companion object {
    /** The mode [wire] names; missing, old or mistyped is [STARTER], so the link still opens. */
    fun fromWire(wire: String?): SuggestionsMode = entries.firstOrNull { it.wire == wire } ?: STARTER
  }
}
