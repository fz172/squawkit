package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.nav.SuggestionsMode
import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.feature.tasks.suggestions.model.SuggestionItem
import dev.fanfly.wingslog.rpc.suggesttasks.IdentifiedDocument
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.ThingTemplate

data class SuggestionsUiState(
  /**
   * How the screen was opened ([SuggestionsMode]): `curated` from the empty list or a finished
   * run's push, `add` from the Add Tasks sheet's *Suggest*, which starts the model run at once.
   */
  val mode: SuggestionsMode = SuggestionsMode.CURATED,
  val isLoading: Boolean = true,
  val template: ThingTemplate? = null,
  val lexicon: Lexicon = GenericLexicon.LEXICON,
  val items: List<SuggestionItem> = emptyList(),
  /**
   * What the run made of each document it read, for the review header: what it is, and whether it
   * looks like it is for this Thing (design §9.5).
   */
  val documents: List<IdentifiedDocument> = emptyList(),
  /** Asking whether a model run can start, before the button or the sheet is offered. */
  val isCheckingAi: Boolean = false,
  /**
   * No model run can start now, and why (the daily limit, another member's run, …): said in place
   * of the button, with when it is back where the server says.
   */
  val aiUnavailable: AiSkipped? = null,
  /** The server's curated list is shown and the model can be asked to add to it (PRD R1). */
  val canSuggest: Boolean = false,
  /** A model run is working; its answer will replace the cards. */
  val isSuggesting: Boolean = false,
  /**
   * The working run reads documents, so the rows still to come are headed as the manuals'. Known
   * from the files handed over, or for a run opened again, from a stage that names one.
   */
  val readsDocuments: Boolean = false,
  /** The working run's stage key and its argument (a document's name), for the progress line. */
  val stage: String? = null,
  val stageArg: String? = null,
  /** The model run failed; the cards stay, with this and *Try again* above them (PRD R21). */
  val failure: AiErrorCode? = null,
  /**
   * The run returned the curated suggestions alone because the model was refused: why, and for
   * the daily limit when it is back (PRD R9a).
   */
  val aiSkipped: AiSkipped? = null,
  /** The model had nothing confident to say (PRD R21a); the screen offers *Add details*. */
  val notEnough: Boolean = false,
  /**
   * With [notEnough]: another run can start now, so *Use a manual* leads somewhere. Asked once the
   * run comes back empty; false until the answer is in.
   */
  val canUseManual: Boolean = false,
  /** With [notEnough]: why no run can start now and, for the daily limit, when one can. */
  val manualBlocked: AiSkipped? = null,
  /** Why *Suggest more* or *Try again* did not start; shown once, as a snackbar. */
  val notice: AiErrorCode? = null,
  val isSaving: Boolean = false,
  /** Set once the step is over, either way; how many were written says which way. */
  val isDone: Boolean = false,
  val acceptedCount: Int = 0,
  /** Why the screen closed with nothing to show, for the task tab to say (PRD R21, R51). */
  val closingError: AiErrorCode? = null,
) {
  val selectedCount: Int get() = items.count { it.selected }
}
