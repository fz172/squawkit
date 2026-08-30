package dev.fanfly.wingslog.core.template

import androidx.compose.runtime.staticCompositionLocalOf
import dev.fanfly.wingslog.thing.ComplianceTerm
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.Noun

/**
 * The lexicon in scope for the composable being rendered (PRD §4.5).
 *
 * Provided at the **Thing scope** from that Thing's DNA. Screens outside any Thing — settings, the
 * switcher, export history — get [GenericLexicon] instead, because on a mixed account there is no
 * single template whose word would be right (§8.5, design §9).
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: a lexicon changes only when the
 * selected Thing changes, which already recomposes everything below it. The dynamic variant would
 * track reads individually to avoid recomposing on a change that never happens.
 */
val LocalThingLexicon = staticCompositionLocalOf { GenericLexicon.LEXICON }

/**
 * Which features exist at all for the thing being rendered (PRD §4.8).
 *
 * **A capability removes UI; it never disables it.** The guard belongs where a composable decides
 * whether to emit anything, not where it would pass `enabled = false` — a homeowner should never
 * see a greyed-out "Engine hours" field. Retrofitting removal onto a show/hide implementation means
 * auditing every call site twice, and the greyed-out version is the one that tends to ship.
 *
 * Defaults to everything enabled so a missing provider shows a control rather than hiding one. That
 * failure is visible and gets reported; the opposite is silent.
 */
val LocalThingCapabilities =
  staticCompositionLocalOf { CurrentThingTemplate.ALL_ENABLED }

/**
 * The domain-neutral lexicon, and the default when nothing more specific is in scope.
 *
 * **A real authored lexicon, not the airplane one with values blanked.** That distinction matters
 * in Phase 2 specifically: on a single-airplane account this is still what settings screens render,
 * so if these words read wrong they read wrong *now* — visible during the phase that changes
 * nothing else — rather than lying dormant until someone adds a boat.
 */
object GenericLexicon {

  val LEXICON: Lexicon = Lexicon(
    thing = Noun(singular = "thing", plural = "things", article = "a"),
    squawk = Noun(singular = "issue", plural = "issues", article = "an"),
    task = Noun(singular = "task", plural = "tasks", article = "a"),
    log = Noun(singular = "log", plural = "logs", article = "a"),
    component = Noun(singular = "part", plural = "parts", article = "a"),
    // "person", not "technician". The generic lexicon has to hold for a *home*, where the entry
    // is "buy milk" and whoever did it is nobody's technician. A generic word that only reads
    // right for machinery is the airplane lexicon wearing a disguise.
    technician = Noun(singular = "person", plural = "people", article = "a"),
    ready_status = "Ready",
    // The OS notification channel name on a mixed account resolves here rather than picking a
    // template's word arbitrarily (PRD §8.5). The channel *id* stays "GROUNDED" regardless —
    // renaming an id drops every user's per-channel settings.
    down_status = "Down",
    down_status_long = "Out of service",
    collection_label = "Stuff",
    // No abbreviation: a house has mandatory work with no two-letter name for it. This is the
    // case that keeps the UI honest — it must drop the parenthetical, not render "Safety recalls ()".
    compliance_mandatory = ComplianceTerm(
      singular = "Safety recall",
      plural = "Safety recalls",
    ),
    compliance_advisory = ComplianceTerm(
      singular = "Service bulletin",
      plural = "Service bulletins",
    ),
    authority_label = "Manufacturer",
  )
}
