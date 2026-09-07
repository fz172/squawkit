package dev.fanfly.wingslog.feature.search.datamanager

import dev.fanfly.wingslog.feature.search.model.MatchExplanation
import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchHit
import kotlinx.datetime.LocalDate

/** Applies a [RecordFilter] to a list in memory. A blank query keeps the caller’s order. */
interface SearchEngine {
  fun <T> search(
    items: List<T>,
    adapter: RecordAdapter<T>,
    filter: RecordFilter,
    today: LocalDate,
  ): List<SearchHit<T>>
}

/** Grades one normalised query token against one field. */
fun interface TokenMatcher {
  fun match(token: String, field: FieldText): TokenMatch?
}

/** [grade] runs 0–1: exact 1, stem 0.95, prefix 0.8, synonym 0.7, fuzzy 0.5; [matched] is the field’s word or phrase. */
data class TokenMatch(val grade: Double, val matched: String, val explanation: MatchExplanation? = null)

/** A field’s text, normalised and tokenised once per search rather than once per query token. */
class FieldText(text: String) {
  val normalized: String = Tokenizer.normalize(text)
  val tokens: List<String> by lazy { Tokenizer.tokens(normalized) }
}
