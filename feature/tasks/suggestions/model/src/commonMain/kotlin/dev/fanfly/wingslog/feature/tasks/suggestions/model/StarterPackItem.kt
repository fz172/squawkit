package dev.fanfly.wingslog.feature.tasks.suggestions.model

import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.MaintenanceTask

/** One card on the suggestions screen. */
data class StarterPackItem(
  /** What the card shows, and what accepting writes (design §6.8). */
  val suggestion: TaskSuggestion,
  val selected: Boolean,
  /**
   * The task as the user changed it before adding it (PRD R28), from the task form's draft mode;
   * null when it is to be written as suggested.
   */
  val edited: MaintenanceTask? = null,
) {
  /** The Thing already has this task (PRD R24): shown, never checkable. */
  val isAlreadyTracked: Boolean get() = !suggestion.matches_existing_task_id?.value_.isNullOrEmpty()
}
