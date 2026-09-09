package dev.fanfly.wingslog.feature.search.datamanager.impl

import dev.fanfly.wingslog.feature.search.datamanager.AviationSynonyms
import dev.fanfly.wingslog.feature.search.datamanager.FieldText
import dev.fanfly.wingslog.feature.search.datamanager.GenericSynonyms
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.datamanager.TokenMatcher
import dev.fanfly.wingslog.feature.search.datamanager.Tokenizer
import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.model.MatchExplanation
import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchField
import dev.fanfly.wingslog.feature.search.model.SearchHit
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.contains
import kotlinx.datetime.LocalDate

/** Filters first, then the query. Every token must land in some field. */
class SearchEngineImpl(
  private val matcher: TokenMatcher = TolerantTokenMatcher(GenericSynonyms + AviationSynonyms),
) : SearchEngine {

  override fun <T> search(
    items: List<T>,
    adapter: RecordAdapter<T>,
    filter: RecordFilter,
    today: LocalDate,
  ): List<SearchHit<T>> {
    val survivors = items.filter { adapter.passesFilters(it, filter, today) }
    // Tokenized, not just split on whitespace: punctuation has to come off the same way it does
    // on the field side ([FieldText]), or a token like `"conditioning` — let alone a lone `"` —
    // could never land in any field and every result would vanish mid-word. Punctuation-only input
    // tokenizes to nothing, which reads as a blank query.
    val tokens = Tokenizer.queryTokens(Tokenizer.normalize(filter.query)).distinct()
    if (tokens.isEmpty()) return survivors.map { SearchHit(it) }
    return survivors
      .mapNotNull { item ->
        score(
          adapter.fields(item),
          tokens
        )?.let { SearchHit(item, it.score, it.explanations, it.matches) }
      }
      .sortedWith(compareByDescending<SearchHit<T>> { it.score }.thenByDescending {
        adapter.date(
          it.item
        )
      })
  }

  private class Scored(
    val score: Double,
    val explanations: List<MatchExplanation>,
    val matches: List<FieldMatch>
  )

  private fun score(fields: List<SearchField>, tokens: List<String>): Scored? {
    val texts = fields.map { FieldText(it.text) }
    var total = 0.0
    val explanations = ArrayList<MatchExplanation>(0)
    val wordsByField = LinkedHashMap<String, MutableSet<String>>()
    for (token in tokens) {
      var best = 0.0
      var bestExplanation: MatchExplanation? = null
      fields.forEachIndexed { i, field ->
        val m = matcher.match(token, texts[i]) ?: return@forEachIndexed
        wordsByField.getOrPut(field.name) { LinkedHashSet() }
          .add(m.matched)
        val weighted = m.grade * field.weight
        if (weighted > best) {
          best = weighted
          bestExplanation = m.explanation
        }
      }
      if (best == 0.0) return null
      total += best
      bestExplanation?.let(explanations::add)
    }
    return Scored(
      total,
      explanations,
      wordsByField.map { (field, words) ->
        FieldMatch(
          field,
          words
        )
      })
  }
}

private fun <T> RecordAdapter<T>.passesFilters(
  item: T,
  filter: RecordFilter,
  today: LocalDate
): Boolean {
  if (filter.components.isNotEmpty() && component(item) !in filter.components) return false
  if (filter.time != TimeWindow.All) {
    val date = date(item)
    val inWindow = if (date == null) nullDateMatches else filter.time.contains(
      date,
      today,
      direction(item)
    )
    if (!inWindow) return false
  }
  return filter.facets.isEmpty() || filter.facets.any { facetMatches(item, it) }
}
