package dev.fanfly.wingslog.feature.search.datamanager.impl

import dev.fanfly.wingslog.feature.search.datamanager.FieldText
import dev.fanfly.wingslog.feature.search.datamanager.SynonymPack
import dev.fanfly.wingslog.feature.search.datamanager.TokenMatch
import dev.fanfly.wingslog.feature.search.datamanager.TokenMatcher
import dev.fanfly.wingslog.feature.search.datamanager.Tokenizer
import dev.fanfly.wingslog.feature.search.datamanager.impl.text.Stemmer
import dev.fanfly.wingslog.feature.search.datamanager.impl.text.editDistance
import dev.fanfly.wingslog.feature.search.model.MatchExplanation

/** Exact, stem, prefix (any length, for typing), synonym, then fuzzy — the first that lands wins. */
class TolerantTokenMatcher(private val synonyms: SynonymPack) : TokenMatcher {

  override fun match(token: String, field: FieldText): TokenMatch? {
    if (token.isEmpty()) return null
    val tokens = field.tokens
    if (token in tokens) return TokenMatch(EXACT)

    val numeric = Tokenizer.isNumeric(token)
    if (!numeric) {
      val stem = Stemmer.stem(token)
      if (tokens.any { Stemmer.stem(it) == stem }) return TokenMatch(STEM)
    }

    tokens.firstOrNull { it.startsWith(token) }?.let { return TokenMatch(PREFIX, MatchExplanation.Prefix(token, it)) }
    if (numeric) return null

    for (expansion in synonyms.expansions(token)) {
      val matched = if (' ' in expansion) {
        expansion.takeIf { field.normalized.contains(it) }
      } else {
        val stem = Stemmer.stem(expansion)
        tokens.firstOrNull { it == expansion || Stemmer.stem(it) == stem }
      }
      if (matched != null) return TokenMatch(SYNONYM, MatchExplanation.Synonym(token, matched))
    }

    if (token.length >= MIN_FUZZY && Tokenizer.isAlphabetic(token)) {
      val cap = if (token.length >= LONG_TOKEN) 2 else 1
      tokens.firstOrNull { it.length >= MIN_FUZZY && Tokenizer.isAlphabetic(it) && editDistance(token, it, cap) <= cap }
        ?.let { return TokenMatch(FUZZY, MatchExplanation.Fuzzy(token, it)) }
    }
    return null
  }

  private companion object {
    const val EXACT = 1.0
    const val STEM = 0.95
    const val PREFIX = 0.8
    const val SYNONYM = 0.7
    const val FUZZY = 0.5
    const val MIN_FUZZY = 4
    const val LONG_TOKEN = 8
  }
}
