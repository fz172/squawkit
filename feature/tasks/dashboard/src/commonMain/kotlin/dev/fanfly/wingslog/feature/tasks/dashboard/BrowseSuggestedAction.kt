package dev.fanfly.wingslog.feature.tasks.dashboard

import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry

/**
 * What the empty task list's *Browse suggested* does. A guest gets no suggestions, curated ones
 * included (PRD R47), so where suggestions come from the server the button opens the sign-in /
 * link-account prompt instead, as *Suggest tasks* does. Everywhere else it opens the list: the
 * app's own pack in production, where the entry is [SuggestEntry.Hidden], and a curated-only run,
 * which needs no make or model (R5), for a Thing missing them.
 */
internal fun browseSuggestedAction(
  entry: SuggestEntry,
  browse: () -> Unit,
  signIn: () -> Unit,
): () -> Unit = if (entry == SuggestEntry.SignInRequired) signIn else browse
