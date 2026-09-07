package dev.fanfly.wingslog.feature.search.model

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** How the list ViewModels run a search; bound once in Koin, overridden in tests. */
data class SearchTuning(
  val queryDebounceMillis: Long = 150,
  val dispatcher: CoroutineDispatcher = Dispatchers.Default,
)
