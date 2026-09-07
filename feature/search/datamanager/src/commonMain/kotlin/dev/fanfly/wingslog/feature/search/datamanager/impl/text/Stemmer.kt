package dev.fanfly.wingslog.feature.search.datamanager.impl.text

/** A dozen suffix rules for comparing words, never for display (design §4.2). */
object Stemmer {
  fun stem(word: String): String = dropSilentE(
    when {
      word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
      word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
      word.length > 4 && word.endsWith("ed") -> word.dropLast(2)
      word.length > 4 && (word.endsWith("xes") || word.endsWith("ses") || word.endsWith("ches") || word.endsWith("shes")) -> word.dropLast(2)
      word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
      else -> word
    },
  )

  /** `replace` and `replaced` both land on `replac`. */
  private fun dropSilentE(word: String) = if (word.length > 4 && word.endsWith("e")) word.dropLast(1) else word
}
