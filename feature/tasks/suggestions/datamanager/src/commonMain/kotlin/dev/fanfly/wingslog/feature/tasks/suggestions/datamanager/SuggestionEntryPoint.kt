package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

/**
 * Where a run was asked from (PRD R50). [wire] goes on the request and in the analytics, so the
 * words stay as they were before the enum existed.
 */
enum class SuggestionEntryPoint(val wire: String) {
  /** The empty task list, or a finished run's push: the curated list. */
  CURATED("curated"),

  /** *Suggest* on the Add Tasks sheet. */
  ADD("add"),

  /** The AI button, or *Try again*, on the curated list. */
  SUGGEST_MORE("suggest_more"),
}
