package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.thing.ComplianceTerm
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.term_with_abbreviation
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * A compliance category label — "Airworthiness Directives (AD)", or "AD (Airworthiness Directive)".
 *
 * The parenthetical lives in a string resource rather than being concatenated here, because
 * brackets are punctuation and punctuation is localised: CJK uses full-width brackets and RTL
 * reverses the order. One resource serves both orderings, which is why the caller decides what goes
 * first rather than this deciding for it.
 *
 * **Drops the parenthetical entirely when the template declares no abbreviation.** A house has
 * mandatory work with no two-letter name for it, and "Safety recalls ()" is worse than plain
 * "Safety recalls".
 */
@Composable
internal fun complianceLabel(main: String, abbreviation: String): String =
  if (abbreviation.isEmpty()) main
  else stringResource(CoreRes.string.term_with_abbreviation, main, abbreviation)

/** `Airworthiness Directives (AD)` — the plural form the picker groups by. */
@Composable
internal fun ComplianceTerm.pluralLabel(): String =
  complianceLabel(LexiconFormatter.titleCase(plural), abbreviation)

/** `AD (Airworthiness Directive)` — the abbreviation-first form the detail tab uses. */
@Composable
internal fun ComplianceTerm.abbreviationFirstLabel(): String =
  if (abbreviation.isEmpty()) LexiconFormatter.titleCase(singular)
  else complianceLabel(abbreviation, LexiconFormatter.titleCase(singular))
