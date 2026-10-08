package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

/**
 * What a model run says it is doing (PRD R19), by the key the pipeline writes to the job (backend
 * `tasks/pipeline.ts`, `PipelineStage`). [readsDocument] stages work on one document, which the
 * run names beside the stage.
 */
enum class SuggestionStage(val wire: String?, val readsDocument: Boolean = false) {
  READING_DOCUMENT("reading_document", readsDocument = true),
  FINDING_SCHEDULE("finding_schedule", readsDocument = true),
  EXTRACTING_SCHEDULE("extracting_schedule", readsDocument = true),
  RECALLING_SCHEDULE("recalling_schedule"),
  TAILORING("tailoring"),
  VALIDATING("validating"),

  /** A stage this build does not know: progress, without saying of what. */
  OTHER(null),
  ;

  companion object {
    /** The stage [wire] names; null for a run that has reported none yet. */
    fun fromWire(wire: String?): SuggestionStage? =
      if (wire == null) null else entries.firstOrNull { it.wire == wire } ?: OTHER
  }
}
