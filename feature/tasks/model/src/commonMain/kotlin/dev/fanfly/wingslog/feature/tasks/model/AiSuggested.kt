package dev.fanfly.wingslog.feature.tasks.model

import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TaskOriginKind

/**
 * Whether the AI drafted this task (task population PRD R34): from the Thing's details, a document
 * or its logs. A curated suggestion left as it was is not, nor is a task added by hand.
 */
val MaintenanceTask.isAiSuggested: Boolean
  get() = when (origin?.kind) {
    TaskOriginKind.TASK_ORIGIN_KIND_AI_THING,
    TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT,
    TaskOriginKind.TASK_ORIGIN_KIND_AI_LOG_BACKFILL,
      -> true

    else -> false
  }
