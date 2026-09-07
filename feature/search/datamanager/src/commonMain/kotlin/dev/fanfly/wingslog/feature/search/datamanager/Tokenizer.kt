package dev.fanfly.wingslog.feature.search.datamanager

import dev.fanfly.wingslog.feature.search.datamanager.Tokenizer.normalize


/** Lowercase, accent-folded tokens; serials and references are kept whole and also split. */
object Tokenizer {
  private val SPLIT = Regex("[^a-z0-9./-]+")
  private val INNER = Regex("[./-]")
  private val ACCENTS = mapOf(
    'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a',
    'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
    'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
    'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
    'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
    'ç' to 'c', 'ñ' to 'n', 'ß' to 's',
  )

  fun normalize(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text.lowercase()) sb.append(ACCENTS[c] ?: c)
    return sb.toString()
  }

  /** Expects [normalize]d text. */
  fun tokens(normalized: String): List<String> {
    val out = ArrayList<String>()
    for (raw in normalized.split(SPLIT)) {
      val t = raw.trim('.', '/', '-')
      if (t.isEmpty()) continue
      out.add(t)
      if (INNER.containsMatchIn(t)) t.split(INNER)
        .filter { it.isNotEmpty() }
        .forEach(out::add)
    }
    return out
  }

  /** Never stemmed or fuzzed: serials, references, readings. */
  fun isNumeric(token: String): Boolean = token.any { it.isDigit() }

  fun isAlphabetic(token: String): Boolean = token.all { it in 'a'..'z' }
}
