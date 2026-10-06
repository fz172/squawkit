package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.TaskOriginKind

/**
 * Drafted by the model, from a document or the Thing, rather than taken from the curated list:
 * what the AI disclosure (PRD R31) and the analytics split (R50) count.
 */
internal fun TaskSuggestion.isFromModel(): Boolean =
  origin_kind == TaskOriginKind.TASK_ORIGIN_KIND_AI_THING ||
    origin_kind == TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT
