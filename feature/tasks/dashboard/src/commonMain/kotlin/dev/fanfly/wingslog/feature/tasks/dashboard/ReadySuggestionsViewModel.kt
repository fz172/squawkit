package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.task.TaskOriginKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * A model run that finished while the user was elsewhere, its answer held for the day and not yet
 * acted on (PRD R19; 1f): the one place AI shows on the task list, and only when there is
 * something to review.
 */
class ReadySuggestionsViewModel(
  suggestionManager: TaskSuggestionManager,
  thingId: String,
) : ViewModel() {

  val ready: StateFlow<ReadySuggestions?> = suggestionManager.observeRun(thingId)
    .map { run ->
      val result = (run as? SuggestionRun.Ready)?.result ?: return@map null
      val fromModel = result.suggestions.count {
        it.matches_existing_task_id?.value_.isNullOrEmpty() &&
          (it.origin_kind == TaskOriginKind.TASK_ORIGIN_KIND_AI_THING ||
            it.origin_kind == TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT)
      }
      if (fromModel == 0) return@map null
      ReadySuggestions(
        count = fromModel,
        document = result.documents.firstOrNull()?.let { it.title.ifBlank { it.name } },
      )
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** [count] suggestions the model drafted, from [document] when it read one. */
data class ReadySuggestions(val count: Int, val document: String?)
