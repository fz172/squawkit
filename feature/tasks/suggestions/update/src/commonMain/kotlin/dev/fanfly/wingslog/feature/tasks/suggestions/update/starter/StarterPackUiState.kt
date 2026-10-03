package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.feature.tasks.suggestions.model.StarterPackItem
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.ThingTemplate

data class StarterPackUiState(
  /**
   * How the screen was opened (`Screen.StarterPack.MODE_*`): `starter` from creation and the empty
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
  val isSaving: Boolean = false,
  /** Set once the step is over, either way; how many were written says which way. */
  val isDone: Boolean = false,
  val acceptedCount: Int = 0,
) {
  val selectedCount: Int get() = items.count { it.selected }
}
