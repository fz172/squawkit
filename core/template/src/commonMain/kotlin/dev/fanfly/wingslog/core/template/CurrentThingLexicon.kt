package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Lexicon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The lexicon of the thing the user currently has selected, app-scoped.
 *
 * **Why this exists rather than only a CompositionLocal installed by the shell.** The per-thing
 * form dialogs — add/edit squawk, task, log — are registered on the *root* nav graph, so Compose
 * Navigation composes them in `DialogHost`, a sibling of the NavHost's content. They are therefore
 * outside anything the shell provides. A dialog reading only the shell's provider would fall back
 * to [GenericLexicon] and render "New issue" where the app has always said "New squawk".
 *
 * That failure is invisible to the byte-identical snapshot test (#658), which checks that a
 * resource's recipe reconstructs its original wording — not *which* lexicon is in scope where the
 * recipe is read. So it has to be got right by construction here.
 *
 * Written by the shell's ViewModel, which is the authority on what is selected, and read above both
 * NavHosts (`AppEntry` on Android/iOS, `WebApp` on web) so content and dialogs see the same words.
 */
class CurrentThingLexicon(registry: TemplateRegistry) {

  /**
   * What to say when nothing is selected — an empty fleet, or before the first load.
   *
   * **While exactly one preset exists it is the only correct answer**, so it is used rather than
   * [GenericLexicon]. This is not a Phase 2 shortcut: the surfaces that render with no selection
   * include the redeem and invite-code flows, which a technician with no thing of their own reaches
   * as their *first* screen. Falling back to generic there would show "Join a shared thing" to a
   * user the app has always said "Join a shared aircraft" to — a visible regression in the phase
   * whose entire promise is that nothing changes.
   *
   * It retires itself. The moment a second canonical preset ships there is no single right word,
   * and this returns the generic lexicon without anyone editing it (design §9).
   */
  private val default: Lexicon =
    registry.canonical().singleOrNull()?.lexicon ?: GenericLexicon.LEXICON

  private val _lexicon = MutableStateFlow(default)

  val lexicon: StateFlow<Lexicon> = _lexicon.asStateFlow()

  /** Restores the no-selection default — an empty fleet is not the same as "keep the last word". */
  fun clear() {
    _lexicon.value = default
  }

  fun set(value: Lexicon) {
    _lexicon.value = value
  }
}
