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

  /**
   * What the user typed, with punctuation dropped and `./-` kept inside a token. Expects
   * [normalize]d text.
   *
   * Punctuation has to go: the field side drops it too, so a query token that kept a stray quote
   * or comma could never land in any field, and every result would vanish mid-word.
   */
  fun queryTokens(normalized: String): List<String> =
    normalized.split(SPLIT).mapNotNull { raw ->
      raw.trim('.', '/', '-')
        .takeIf(String::isNotEmpty)
    }

  /**
   * [queryTokens] plus, for anything holding a `.`, `/` or `-`, its parts. Expects [normalize]d
   * text.
   *
   * Only the field side expands: indexing `91.413` as `91.413`, `91` and `413` is what lets either
   * spelling find it. Doing the same to a query would instead *add* conditions — every token has to
   * land somewhere — and widen the highlighted words to parts the user never typed.
   */
  fun tokens(normalized: String): List<String> {
    val out = ArrayList<String>()
    for (t in queryTokens(normalized)) {
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
