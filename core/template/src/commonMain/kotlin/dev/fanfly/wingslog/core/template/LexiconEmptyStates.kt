package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.EmptyStates
import dev.fanfly.wingslog.thing.Lexicon

/**
 * The empty-state copy a template writes for its own screens.
 *
 * These used to be strings in `strings.xml`, and the resources are gone rather than converted: a
 * frame like `"Tap + to report %1${'$'}s"` renders a grammatical sentence about the wrong subject
 * for every preset that is not an aeroplane. A homeowner noting that the fence gate sticks is not
 * reporting a defect, and no noun substituted into the aviation frame makes them one. So the whole
 * sentence is the template's, the same call `down_status` takes (design §10a).
 *
 * Read only on **per-thing** surfaces — a tab or an Overview rail inside a selected Thing. The
 * fleet empty state renders with nothing selected and is deliberately not here.
 */

/** The generic copy, resolved once. Non-null by construction — [GenericLexicon] writes all nine. */
private val GENERIC: EmptyStates = GenericLexicon.LEXICON.empty_states!!

/**
 * Falls back per FIELD, not per block.
 *
 * Wire generates the message as nullable and a proto3 string defaults to empty, so a fetched
 * template that omits one line is indistinguishable from one that sets it blank — and blank is what
 * would render, as an empty line under a heading. A partial block is a bad template, not a bad app
 * state, so the missing line takes the generic wording and the rest of the template's stands.
 */
private inline fun Lexicon.copy(pick: (EmptyStates) -> String): String =
  empty_states?.let(pick)?.ifEmpty { null } ?: pick(GENERIC)

/** Squawks tab, Open — under "No open squawks". */
val Lexicon.squawkEmptyHint: String get() = copy { it.squawk_hint }

/** Tasks tab, Due — under "No maintenance tasks". */
val Lexicon.taskEmptyHint: String get() = copy { it.task_hint }

/** Tasks tab, History. The whole state; there is no title above it. */
val Lexicon.taskHistoryEmptyHint: String get() = copy { it.task_history_hint }

/** Logs tab — under "Work log is empty", above the "Log First Entry" button. */
val Lexicon.logEmptyHint: String get() = copy { it.log_hint }

/** Overview, recent-activity rail. */
val Lexicon.overviewLogEmptyTitle: String get() = copy { it.overview_log_title }

/** Overview, recent-activity rail. */
val Lexicon.overviewLogEmptyHint: String get() = copy { it.overview_log_hint }

/** Overview, next-due rail. */
val Lexicon.overviewTaskEmptyTitle: String get() = copy { it.overview_task_title }

/** Overview, next-due rail. */
val Lexicon.overviewTaskEmptyHint: String get() = copy { it.overview_task_hint }

/** Overview, open-squawks rail. Its title reuses the tab's "No open %1${'$'}s", which substitutes. */
val Lexicon.overviewSquawkEmptyHint: String get() = copy { it.overview_squawk_hint }

/**
 * The Dashboard's log-onboarding card, on a Thing with no logs at all.
 *
 * The one line here that is not only about wording: it names the METERS to record a baseline for,
 * and a template may declare none. The card is not gated on `capabilities.meters`, so the aviation
 * copy asked a homeowner for airframe, engine and prop times — readings the log form does not even
 * offer them. A meterless preset writes a sentence about the record instead.
 */
val Lexicon.logOnboardingHint: String get() = copy { it.log_onboarding_hint }
