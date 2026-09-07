package dev.fanfly.wingslog.feature.search.model

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Debounces typing only: chips, windows and a cleared query apply at once. */
@OptIn(FlowPreview::class)
fun Flow<RecordFilter>.debouncedQuery(millis: Long): Flow<RecordFilter> {
  val structure = map { it.copy(query = "") }.distinctUntilChanged()
  val query = map { it.query }.distinctUntilChanged()
    .debounce { if (it.isBlank()) 0L else millis }
  return combine(structure, query) { filter, q -> filter.copy(query = q) }
}
