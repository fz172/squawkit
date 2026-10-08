package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.TaskOriginKind

/**
 * Drafted by the model, from a document or the Thing, rather than taken from the curated list:
 * what the AI disclosure (PRD R31) and the analytics split (R50) count.
 */
fun TaskSuggestion.isFromModel(): Boolean =
  origin_kind == TaskOriginKind.TASK_ORIGIN_KIND_AI_THING ||
    origin_kind == TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT

/**
 * A model run working, or one whose answer has the model's cards in it: what the user would lose
 * by closing it or by starting another. A curated-only run, an empty or failed model run, and no
 * run at all hold nothing of the kind.
 */
fun SuggestionRun.holdsModelAnswer(): Boolean =
  this is SuggestionRun.Working ||
    result?.suggestions.orEmpty()
      .any { it.isFromModel() }
