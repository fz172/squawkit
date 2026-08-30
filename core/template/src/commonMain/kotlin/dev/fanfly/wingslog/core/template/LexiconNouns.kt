package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.Noun

/**
 * Non-null noun accessors for the ~160 Phase 2C call sites (#656).
 *
 * Wire generates every message field as nullable, so `lexicon.squawk` is a `Noun?` everywhere a
 * string is filled. Written out, each call site would carry a `!!` — putting a crash in the render
 * path for a case that cannot arise through the shell, since every lexicon that reaches the
 * composition is a template's or [GenericLexicon]'s and both populate all six nouns.
 *
 * It *can* arise from a hand-built `Lexicon` in a test, or from a fetched template that omits a
 * field once Phase 3 adds the RPC — and there the right answer is a readable generic word, not a
 * blank screen. A missing noun is a bad template, not a bad app state.
 */

/** The generic fallbacks, resolved once. Non-null by construction — [GenericLexicon] sets all six. */
private val GENERIC = GenericLexicon.LEXICON

val Lexicon.thingNoun: Noun get() = thing ?: GENERIC.thing!!

val Lexicon.squawkNoun: Noun get() = squawk ?: GENERIC.squawk!!

val Lexicon.taskNoun: Noun get() = task ?: GENERIC.task!!

val Lexicon.logNoun: Noun get() = log ?: GENERIC.log!!

val Lexicon.componentNoun: Noun get() = component ?: GENERIC.component!!

val Lexicon.technicianNoun: Noun get() = technician ?: GENERIC.technician!!
