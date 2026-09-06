package dev.fanfly.wingslog.feature.search.datamanager

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

/** Grades one lowercase query token against a field’s text: 0 for no match, 1 for exact. */
fun interface TokenMatcher {
  fun grade(token: String, text: String): Double
}
