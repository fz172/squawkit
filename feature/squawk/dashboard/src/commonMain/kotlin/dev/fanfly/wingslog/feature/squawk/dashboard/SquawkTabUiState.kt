package dev.fanfly.wingslog.feature.squawk.dashboard

import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus

data class SquawkTabUiState(
  val filter: RecordFilter = RecordFilter(),
  /** Every status, narrowed by [filter]; the tab splits open from closed. */
  val squawks: List<SquawkWithStatus> = emptyList(),
  /** Squawk id → the words the query matched, for highlighting. */
  val matches: Map<String, List<FieldMatch>> = emptyMap(),
)
