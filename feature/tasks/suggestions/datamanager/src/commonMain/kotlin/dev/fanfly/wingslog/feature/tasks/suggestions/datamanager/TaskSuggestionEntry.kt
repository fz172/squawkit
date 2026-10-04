package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What a *Suggest tasks* entry point shows for one Thing (docs/ai/task_population_design.md §9.1,
 * §10; PRD R5, R47, R48, R51), checked in this order:
 *
 * 1. **Hidden** off developer builds until v1 (R48).
 * 2. **SignInRequired** for a guest: the action is visible and opens the account upgrade (R47).
 * 3. **MissingIdentity** when a spec field the template requires is empty: it names them and links
 *    to the Thing's edit screen (R5). The custom preset requires none, so it is never this.
 * 4. **Available**.
 *
 * Connectivity is not checked here (R51, revised 2026-10-02): the action stays enabled offline, and
 * a suggestion call that fails for want of a connection shows a "No internet connection" snackbar
 * in the workflow. The server checks the rest again (§5.3); this is what spares the user a refusal.
 */
class TaskSuggestionEntry(
  private val capability: AppCapability,
  private val auth: FirebaseAuth,
  private val fleetManager: FleetManager,
  private val templateRegistry: TemplateRegistry,
) {

  fun observe(thingId: String): Flow<SuggestEntry> {
    if (!capability.isTaskSuggestionsSupported) return flowOf(SuggestEntry.Hidden)
    return combine(
      auth.authStateChanged.map { user -> user?.isAnonymous ?: true },
      fleetManager.loadThing(thingId),
    ) { isGuest, thing ->
      val missing = thing?.let { t ->
        val template = t.template ?: templateRegistry.forThingWithFallback(t)
        val values = t.spec.associate { it.key to it.value_ }
        template.spec_fields
          .filter { it.required && values[it.key].isNullOrBlank() }
          .map { it.label.ifBlank { it.key } }
      }
        .orEmpty()
      when {
        isGuest -> SuggestEntry.SignInRequired
        missing.isNotEmpty() -> SuggestEntry.MissingIdentity(missing)
        else -> SuggestEntry.Available
      }
    }
  }
}

sealed interface SuggestEntry {
  data object Hidden : SuggestEntry

  data object SignInRequired : SuggestEntry

  /** [fieldLabels] are the template's labels for the required fields left empty. */
  data class MissingIdentity(val fieldLabels: List<String>) : SuggestEntry

  data object Available : SuggestEntry
}
