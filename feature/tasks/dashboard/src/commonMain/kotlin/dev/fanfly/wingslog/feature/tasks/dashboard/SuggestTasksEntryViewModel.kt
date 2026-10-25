package dev.fanfly.wingslog.feature.tasks.dashboard

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.id.ThingId

/** What the empty task list's *Browse suggested tasks* shows for one Thing (PRD R2, §9.1). */
class SuggestTasksEntryViewModel(
  entry: TaskSuggestionEntry,
  thingId: ThingId,
) : ViewModel() {

  val entry: StateFlow<SuggestEntry> = entry.observe(thingId)
    .stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5_000),
      SuggestEntry.Hidden
    )
}
