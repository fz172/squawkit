package dev.fanfly.wingslog.feature.tasks.dashboard

import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry

/**
 * What the empty task list's *Browse suggested* does. A guest gets no suggestions, curated ones
 * included (PRD R47), so the button opens the sign-in / link-account prompt instead, as *Suggest
 * tasks* does. Everyone else gets the list: a curated-only run, which needs no make or model (R5),
 * so a Thing missing them is offered it too.
 */
internal fun browseSuggestedAction(
  entry: SuggestEntry,
  browse: () -> Unit,
  signIn: () -> Unit,
): () -> Unit = if (entry == SuggestEntry.SignInRequired) signIn else browse

/**
 * Whether the server keeps a curated list for [templateId] (design §6.8), so an empty task list
 * offers *Browse suggested*: every template but the custom one. Unknown (still loading) is no.
 */
internal fun hasCuratedList(templateId: String?): Boolean =
  templateId != null && templateId != CanonicalTemplates.CUSTOM.id
