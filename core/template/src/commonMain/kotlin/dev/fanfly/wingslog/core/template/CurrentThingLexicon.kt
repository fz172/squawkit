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
class CurrentThingLexicon {

  private val _lexicon = MutableStateFlow(GenericLexicon.LEXICON)

  /** Generic until a thing is selected — which is also the right answer for an empty fleet. */
  val lexicon: StateFlow<Lexicon> = _lexicon.asStateFlow()

  fun set(value: Lexicon) {
    _lexicon.value = value
  }
}
