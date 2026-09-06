package dev.fanfly.wingslog.feature.search.datamanager.impl

import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.datamanager.TokenMatcher
import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchField
import dev.fanfly.wingslog.feature.search.model.SearchHit
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.contains
import kotlinx.datetime.LocalDate

/** Filters first, then the query. Every token must land in some field. */
class SearchEngineImpl(
  private val matcher: TokenMatcher = SubstringTokenMatcher,
) : SearchEngine {

  override fun <T> search(
    items: List<T>,
    adapter: RecordAdapter<T>,
    filter: RecordFilter,
    today: LocalDate,
  ): List<SearchHit<T>> {
    val survivors = items.filter { adapter.passesFilters(it, filter, today) }
    val tokens = filter.query.lowercase().split(WHITESPACE).filter { it.isNotEmpty() }.distinct()
    if (tokens.isEmpty()) return survivors.map { SearchHit(it) }
    return survivors
      .mapNotNull { item -> score(adapter.fields(item), tokens)?.let { SearchHit(item, it) } }
      .sortedWith(compareByDescending<SearchHit<T>> { it.score }.thenByDescending { adapter.date(it.item) })
  }

  private fun score(fields: List<SearchField>, tokens: List<String>): Double? {
    var total = 0.0
    for (token in tokens) {
      val best = fields.maxOf { field -> matcher.grade(token, field.text) * field.weight }
      if (best == 0.0) return null
      total += best
    }
    return total
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

/** Case-insensitive substring; the tolerant matcher (P2) replaces it. */
object SubstringTokenMatcher : TokenMatcher {
  override fun grade(token: String, text: String): Double =
    if (text.contains(token, ignoreCase = true)) 1.0 else 0.0
}
