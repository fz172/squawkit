package dev.fanfly.wingslog.feature.tasks.suggestions.update.add

import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.update.starter.SourcesState
import dev.fanfly.wingslog.thing.Lexicon

/**
 * The Add Tasks sheet: *Suggest* with optional manuals, or *Create manually*. The middle slot is
 * the manuals area where the owner has Pro, a one-line promo where they do not, so the layout does
 * not jump between plans.
 */
data class AddTasksUiState(
  val lexicon: Lexicon = GenericLexicon.LEXICON,
  /**
   * What the suggestions are based on, as the Thing's list row names it ("Sling 4 TSi"); empty
   * when it has nothing to show.
   */
  val details: String = "",
  /** Whether *Suggest* can be offered: a guest signs in, a Thing missing details gets them. */
  val entry: SuggestEntry = SuggestEntry.Hidden,
  /** The manuals picked, and what the owner's plan and the day allow. */
  val sources: SourcesState = SourcesState(),
) {
  /** The manuals area, rather than the promo or nothing. */
  val showsManuals: Boolean
    get() = entry == SuggestEntry.Available && !sources.isChecking &&
      sources.blocked == null && sources.documentsAllowed

  /** The Pro promo: an owner without Pro. A member cannot change the owner’s plan (R45). */
  val showsPromo: Boolean
    get() = entry == SuggestEntry.Available && !sources.isChecking &&
      sources.blocked == null && !sources.documentsAllowed && sources.isOwner

  /** *Suggest* does something now; a guest and a Thing missing details have their own actions. */
  val canSuggest: Boolean
    get() = when (entry) {
      SuggestEntry.Available -> !sources.isChecking && !sources.isAdding
      SuggestEntry.SignInRequired, is SuggestEntry.MissingIdentity -> true
      SuggestEntry.Hidden -> false
    }
}
