package dev.fanfly.wingslog.feature.search.model

/** A record that passed the filters; [score] is 0 for a blank query. */
data class SearchHit<T>(
  val item: T,
  val score: Double = 0.0,
  /** Why non-obvious tokens matched. Empty for exact hits. */
  val explanations: List<MatchExplanation> = emptyList(),
  /** The words that matched, per field, for highlighting. */
  val matches: List<FieldMatch> = emptyList(),
)

/** The words of one field that the query landed on, as they appear in the record (lowercased). */
data class FieldMatch(val field: String, val words: Set<String>)

/** Record id → its matches, for the cards; hits with no query match are left out. */
fun <T> List<SearchHit<T>>.matchesById(id: (T) -> String): Map<String, List<FieldMatch>> =
  filter { it.matches.isNotEmpty() }.associate { id(it.item) to it.matches }

sealed interface MatchExplanation {
  val query: String
  val matched: String

  /** [query] is an acronym or synonym of [matched] (`xpdr` → `transponder`). */
  data class Synonym(override val query: String, override val matched: String) : MatchExplanation

  /** [matched] is within an edit or two of [query] (`trasnponder`). */
  data class Fuzzy(override val query: String, override val matched: String) : MatchExplanation

  /** [matched] starts with [query]. */
  data class Prefix(override val query: String, override val matched: String) : MatchExplanation
}
