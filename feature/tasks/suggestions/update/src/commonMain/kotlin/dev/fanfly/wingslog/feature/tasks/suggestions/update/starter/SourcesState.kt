package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.thing.Attachment
import kotlin.time.Instant

/**
 * The sources sheet (design §9.3, PRD R6): the documents picked for the next model run, and what
 * the Thing's owner and the day allow. Open while non-null on [StarterPackUiState.sources].
 */
data class SourcesState(
  /** Picked and stored on this device, uploading; the run waits for each (§8.1). */
  val documents: List<Attachment> = emptyList(),
  /** Eligibility is being asked; *Suggest* waits for it. */
  val isChecking: Boolean = true,
  /** The Thing's owner has Pro, so documents can be added (PRD R46). */
  val documentsAllowed: Boolean = false,
  /** The caller owns the Thing; a member's paywall names the owner's plan instead (R45). */
  val isOwner: Boolean = true,
  /** Why no model run can start now (the daily limit, another member's run, …); null when one can. */
  val blocked: AiErrorCode? = null,
  /** When [blocked] lifts, for the daily limit and a spending ceiling. */
  val availableAt: Instant? = null,
  /** A picked file is being stored. */
  val isAdding: Boolean = false,
  /** Why the last pick was not added, shown once. */
  val problem: DocumentProblem? = null,
) {
  val atLimit: Boolean get() = documents.size >= MAX_DOCUMENTS_PER_RUN

  val canSuggest: Boolean get() = !isChecking && blocked == null && !isAdding

  companion object {
    /** `ai_config.maxDocumentsPerRun`; the app caps at pick time (design §5.5). */
    const val MAX_DOCUMENTS_PER_RUN = 3
  }
}

enum class DocumentProblem {
  /** Over the AI document cap (25 MB). */
  TOO_LARGE,

  /** Not a PDF or an image (PRD R7). */
  UNSUPPORTED,

  /** More than [SourcesState.MAX_DOCUMENTS_PER_RUN]. */
  TOO_MANY,

  /** The file could not be read or stored. */
  NOT_ADDED,
}
