package dev.fanfly.wingslog.feature.search.datamanager.impl

import dev.fanfly.wingslog.feature.search.datamanager.AviationSynonyms
import dev.fanfly.wingslog.feature.search.datamanager.FieldText
import dev.fanfly.wingslog.feature.search.datamanager.GenericSynonyms
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.datamanager.TokenMatcher
import dev.fanfly.wingslog.feature.search.datamanager.Tokenizer
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
    val tokens = Tokenizer.normalize(filter.query).split(WHITESPACE).filter { it.isNotEmpty() }.distinct()
    if (tokens.isEmpty()) return survivors.map { SearchHit(it) }
    return survivors
      .mapNotNull { item -> score(adapter.fields(item), tokens)?.let { (score, why) -> SearchHit(item, score, why) } }
      .sortedWith(compareByDescending<SearchHit<T>> { it.score }.thenByDescending { adapter.date(it.item) })
  }

  private fun score(fields: List<SearchField>, tokens: List<String>): Pair<Double, List<MatchExplanation>>? {
    val texts = fields.map { FieldText(it.text) }
    var total = 0.0
    val explanations = ArrayList<MatchExplanation>(0)
    for (token in tokens) {
      var best = 0.0
      var bestExplanation: MatchExplanation? = null
      fields.forEachIndexed { i, field ->
        val m = matcher.match(token, texts[i]) ?: return@forEachIndexed
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
    return total to explanations
  }

  private companion object {
    val WHITESPACE = Regex("\\s+")
  }
}

private fun <T> RecordAdapter<T>.passesFilters(item: T, filter: RecordFilter, today: LocalDate): Boolean {
  if (filter.components.isNotEmpty() && component(item) !in filter.components) return false
  if (filter.time != TimeWindow.All) {
    val date = date(item)
    val inWindow = if (date == null) nullDateMatches else filter.time.contains(date, today, direction(item))
    if (!inWindow) return false
  }
  val facet = filter.facet
  return facet == null || facetMatches(item, facet)
}
