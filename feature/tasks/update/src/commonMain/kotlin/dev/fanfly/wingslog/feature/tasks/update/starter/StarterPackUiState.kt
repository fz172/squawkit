package dev.fanfly.wingslog.feature.tasks.update.starter

import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.ThingTemplate

data class StarterPackUiState(
  val isLoading: Boolean = true,
  val template: ThingTemplate? = null,
  val lexicon: Lexicon = GenericLexicon.LEXICON,
  val items: List<StarterPackItem> = emptyList(),
  val isSaving: Boolean = false,
  /** Set once the step is over, either way; how many were written says which way. */
  val isDone: Boolean = false,
  val acceptedCount: Int = 0,
) {
  val selectedCount: Int get() = items.count { it.selected }
}
