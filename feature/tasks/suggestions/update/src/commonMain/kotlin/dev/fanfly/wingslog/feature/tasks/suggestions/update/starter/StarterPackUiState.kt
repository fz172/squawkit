package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.feature.tasks.suggestions.model.StarterPackItem
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.ThingTemplate

data class StarterPackUiState(
  /**
   * How the screen was opened (`Screen.StarterPack.MODE_*`): `starter` from the empty
   * list, `suggest` from the task list's *Suggest tasks* (design §9.1). T17 renders the difference.
   */
  val mode: String = Screen.StarterPack.MODE_STARTER,
  val isLoading: Boolean = true,
  val template: ThingTemplate? = null,
  val lexicon: Lexicon = GenericLexicon.LEXICON,
  val items: List<StarterPackItem> = emptyList(),
  /** The server's curated list is shown and the model can be asked to add to it (PRD R1). */
  val canSuggest: Boolean = false,
  /** A model run is working; its answer will replace the cards. */
  val isSuggesting: Boolean = false,
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
  /** The sources sheet, open while non-null (design §9.3). */
  val sources: SourcesState? = null,
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
