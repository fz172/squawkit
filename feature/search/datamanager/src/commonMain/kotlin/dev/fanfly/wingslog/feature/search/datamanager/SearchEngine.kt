package dev.fanfly.wingslog.feature.search.datamanager

import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchField
import dev.fanfly.wingslog.feature.search.model.SearchHit
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.contains
import kotlinx.datetime.LocalDate

/**
 * Applies a [RecordFilter] to a list in memory: component, time window and facet first, then the
 * query. With a blank query the caller’s order is kept — the tabs already sort squawks by priority
 * and tasks by due status, and a search with nothing typed must not fight that (design §4.7).
 *
 * A thing’s logbook is hundreds of records, so a full pass per keystroke is cheap on every target,
 * web included. The query step is a [TokenMatcher] so the tolerant matcher (P2) can replace the
 * substring one without touching the callers.
 */
class SearchEngine(private val matcher: TokenMatcher = SubstringTokenMatcher) {

  fun <T> search(
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

  /** Every token must land in some field; each contributes its best grade × weight (design §4.5). */
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
  if (facet != null && !facetMatches(item, facet)) return false
  return true
}

/** Grades one lowercase query token against one field’s text: 0 for no match, up to 1 for exact. */
fun interface TokenMatcher {
  fun grade(token: String, text: String): Double
}

/** The P1 matcher: a case-insensitive substring is a full-grade hit, anything else is none. */
object SubstringTokenMatcher : TokenMatcher {
  override fun grade(token: String, text: String): Double =
    if (text.contains(token, ignoreCase = true)) 1.0 else 0.0
}
