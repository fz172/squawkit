package dev.fanfly.wingslog.feature.search.model

import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.datetime.LocalDate

/**
 * How one kind of record exposes itself to [SearchEngine]: the text to search, the component it is
 * filed against, and the date its time window tests. One adapter per list — logs here, squawks and
 * tasks beside the ViewModel that lists them.
 */
interface RecordAdapter<T> {
  /** The searchable text, heaviest field first (design §3.2). */
  fun fields(item: T): List<SearchField>

  fun component(item: T): ComponentType

  /** The date the time window tests, or null when the record has none. */
  fun date(item: T): LocalDate?

  /** Whether a preset counts back or forward from today for this record. */
  fun direction(item: T): TimeDirection = TimeDirection.PAST

  /**
   * Whether a record with no date survives a window other than [TimeWindow.All]. False for anything
   * that always has a date; true for an active meter-only task, which time cannot exclude (PRD FR.19).
   */
  val nullDateMatches: Boolean get() = false

  fun facetMatches(item: T, facet: Facet): Boolean = false
}

/** One searchable field. [weight] multiplies a match’s grade, so a serial hit outranks a body hit. */
data class SearchField(val name: String, val text: String, val weight: Int)
