package dev.fanfly.wingslog.feature.search.model

import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.datetime.LocalDate

/** How one record kind exposes its text, component and date to the search engine. */
interface RecordAdapter<T> {
  /** Heaviest field first. */
  fun fields(item: T): List<SearchField>

  fun component(item: T): ComponentType

  /** The date the time window tests; null when the record has none. */
  fun date(item: T): LocalDate?

  fun direction(item: T): TimeDirection = TimeDirection.PAST

  /** Whether a dateless record survives a window other than [TimeWindow.All]. */
  val nullDateMatches: Boolean get() = false

  fun facetMatches(item: T, facet: Facet): Boolean = false
}

/** [weight] multiplies a match’s grade, so a serial hit outranks a body hit. */
data class SearchField(val name: String, val text: String, val weight: Int)
