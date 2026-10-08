package dev.fanfly.wingslog.feature.tasks.suggestions.model

import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.MaintenanceTask

/**
 * One suggestion the user kept. [edited] is the task as they changed it before accepting (PRD R28,
 * T18); null writes the suggestion as mapped.
 */
data class AcceptedSuggestion(
  val suggestion: TaskSuggestion,
  val edited: MaintenanceTask? = null,
)

/** One accepted suggestion that was written, and the id of the task it became. */
data class WrittenSuggestion(
  val accepted: AcceptedSuggestion,
  val taskId: String,
)
