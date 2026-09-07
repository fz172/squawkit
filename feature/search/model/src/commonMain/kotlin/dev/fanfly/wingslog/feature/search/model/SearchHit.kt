package dev.fanfly.wingslog.feature.search.model

/** A record that passed the filters; [score] is 0 for a blank query. */
data class SearchHit<T>(
  val item: T,
  val score: Double = 0.0,
  /** Why non-obvious tokens matched, so the card can say so. Empty for exact hits. */
  val explanations: List<MatchExplanation> = emptyList(),
)

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
