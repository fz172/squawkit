package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.core.template.CurrentThingTemplate.Companion.ALL_ENABLED
import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The template of the thing the user currently has selected, app-scoped.
 *
 * **Why this exists rather than only a CompositionLocal installed by the shell.** The per-thing form
 * dialogs — add/edit squawk, task, log — are registered on the *root* nav graph, so Compose
 * Navigation composes them in `DialogHost`, a sibling of the NavHost's content. They are therefore
 * outside anything the shell provides. A dialog reading only the shell's provider would fall back
 * to [GenericLexicon] and render "New issue" where the app has always said "New squawk".
 *
 * **Why the whole template and not just the lexicon.** This started as a lexicon holder. Capabilities
 * need exactly the same plumbing, and so will the meter, component-slot and spec-field sets when
 * Phase 3 renders them — carrying the template means that plumbing is built once rather than four
 * times, and there is never a moment where two holders disagree about which thing is selected.
 *
 * Written by the shell's ViewModel, which is the authority on what is selected, and read above both
 * NavHosts (`AppEntry` on Android/iOS, `WebApp` on web) so content and dialogs see the same words
 * and the same set of features.
 */
class CurrentThingTemplate(registry: TemplateRegistry) {

  /**
   * What applies when nothing is selected — an empty fleet, or before the first load.
   *
   * **While exactly one preset exists it is the only correct answer.** This is not a Phase 2
   * shortcut: the surfaces that render with no selection include the redeem and invite-code flows,
   * which a technician with no thing of their own reaches as their *first* screen. Falling back to
   * something generic there would show "Join a shared thing" to a user the app has always said
   * "Join a shared aircraft" to.
   *
   * It retires itself. The moment a second canonical preset ships there is no single right answer,
   * and this becomes null — at which point the lexicon falls back to [GenericLexicon] and
   * capabilities to [ALL_ENABLED], both below (design §9).
   */
  private val default: ThingTemplate? = registry.canonical()
    .singleOrNull()

  private val _template = MutableStateFlow(default)
  private val _lexicon = MutableStateFlow(default.lexiconOrGeneric())
  private val _capabilities =
    MutableStateFlow(default.capabilitiesOrAllEnabled())

  val template: StateFlow<ThingTemplate?> = _template.asStateFlow()

  /** Generic when no single template applies — see [default]. */
  val lexicon: StateFlow<Lexicon> = _lexicon.asStateFlow()

  /**
   * Everything enabled when no single template applies.
   *
   * **Fails open on purpose.** A capability *removes* UI, and the surfaces it removes are per-thing
   * ones that only render once a thing is selected — so this fallback should be unreachable. If it
   * is ever reached, showing a control that should have been hidden is a far cheaper mistake than
   * hiding one the user needs, and it is the one a person notices and reports.
   */
  val capabilities: StateFlow<Capabilities> = _capabilities.asStateFlow()

  fun set(value: ThingTemplate) = publish(value)

  /** Restores the no-selection default — an empty fleet is not the same as "keep the last thing". */
  fun clear() = publish(default)

  /**
   * All three move together.
   *
   * Deliberately three fields rather than one `_template` with `map`-derived views: a mapped Flow is
   * not a StateFlow, and turning it into one needs a `stateIn` scope that an app-scoped holder has
   * no business owning. Assigning together here means a reader can never see a lexicon from one
   * thing beside capabilities from another.
   */
  private fun publish(value: ThingTemplate?) {
    _template.value = value
    _lexicon.value = value.lexiconOrGeneric()
    _capabilities.value = value.capabilitiesOrAllEnabled()
  }

  private fun ThingTemplate?.lexiconOrGeneric(): Lexicon =
    this?.lexicon ?: GenericLexicon.LEXICON

  private fun ThingTemplate?.capabilitiesOrAllEnabled(): Capabilities =
    this?.capabilities ?: ALL_ENABLED

  companion object {
    internal val ALL_ENABLED = Capabilities(
      components = true,
      meters = true,
      compliance = true,
      technicians = true,
      technician_certificates = true,
      component_serial_prompt = true,
    )
  }
}
