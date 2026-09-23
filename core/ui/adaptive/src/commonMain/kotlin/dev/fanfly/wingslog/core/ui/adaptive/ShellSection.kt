package dev.fanfly.wingslog.core.ui.adaptive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.Section
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.settings
import wingslog.core.sharedassets.generated.resources.shell_nav_tasks_narrow
import wingslog.core.sharedassets.generated.resources.shell_tab_dashboard
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/**
 * Top-level sections of the adaptive shell. All but [SETTINGS] are per-thing; [SETTINGS] is global.
 */
enum class ShellSection(val icon: ImageVector) {
  DASHBOARD(Icons.Filled.Dashboard),
  SQUAWKS(Icons.Filled.Warning),
  TASKS(Icons.Filled.Checklist),
  LOGS(Icons.Filled.Description),

  /** Flight data on the airplane preset; declared by the template and gated by the build. */
  DATA_LOGS(Icons.Filled.ShowChart),
  SETTINGS(Icons.Filled.Settings),
}

/*
 * The three label slots are composable functions rather than StringResource fields on the enum.
 *
 * Some of these labels are lexicon words and some cannot be: "Squawks", "Maintenance Tasks" and
 * "Work Logs" come straight from the lexicon, while "Maint.", "Logs" and "Maint. Tasks" are
 * abbreviations chosen to fit a narrow rail and are not derivable from any noun. An enum field can
 * only hold one kind, and holding a StringResource is what let ProUpsellSheet render a placeholder
 * to users (#692): a resource stored as a value hides whether it needs arguments.
 *
 * That "Logs" is shorter than the log noun ("Work Logs") is the same wording split tracked in #683.
 */

/** Short label for the space-constrained bottom bar tier. */
@Composable
fun ShellSection.label(): String = when (this) {
  ShellSection.DASHBOARD -> stringResource(UiRes.string.shell_tab_dashboard)
  // All three read the template. Tasks and Logs used fixed "Maint." / "Logs" strings, so the two
  // right-hand tabs kept aviation wording on every preset — the one place in the shell that never
  // changed with the thing.
  ShellSection.SQUAWKS -> LexiconFormatter.shortPlural(LocalThingLexicon.current.squawkNoun)
  ShellSection.TASKS -> LexiconFormatter.shortPlural(LocalThingLexicon.current.taskNoun)
  ShellSection.LOGS -> LexiconFormatter.shortPlural(LocalThingLexicon.current.logNoun)
  ShellSection.DATA_LOGS -> LexiconFormatter.shortPlural(LocalThingLexicon.current.dataLogNoun)
  ShellSection.SETTINGS -> stringResource(UiRes.string.settings)
}

/** Full section name: the content top-bar title, also the wide (EXPANDED/LARGE) sidebar label. */
@Composable
fun ShellSection.title(): String = when (this) {
  ShellSection.TASKS -> LexiconFormatter.titleCasePlural(LocalThingLexicon.current.taskNoun)
  ShellSection.LOGS -> LexiconFormatter.titleCasePlural(LocalThingLexicon.current.logNoun)
  ShellSection.DATA_LOGS -> LexiconFormatter.titleCasePlural(LocalThingLexicon.current.dataLogNoun)
  else -> label()
}

/** Label for the narrower MEDIUM sidebar; may abbreviate where the full title doesn't fit. */
@Composable
fun ShellSection.narrowSidebarLabel(): String = when (this) {
  ShellSection.TASKS -> stringResource(UiRes.string.shell_nav_tasks_narrow)
  else -> title()
}

private val DEFAULT_PER_THING_SECTIONS =
  listOf(
    ShellSection.DASHBOARD,
    ShellSection.SQUAWKS,
    ShellSection.TASKS,
    ShellSection.LOGS,
  )

/**
 * The per-thing sections this thing's template declares, in the order it wants them (PRD §4.8).
 *
 * An ordered list rather than a bool per section, so the shell reads one list instead of every
 * screen checking a flag — and so a template can *reorder*, which a set of bools cannot express.
 *
 * Falls back to the original four when the template names none. That is the fail-open rule
 * [LocalThingCapabilities] documents: a missing declaration should show a section, not silently
 * remove navigation. SETTINGS is absent from both — it is account-level and never template-owned.
 */
@Composable
internal fun perThingSections(): List<ShellSection> =
  perThingSectionsFor(LocalThingCapabilities.current)

/**
 * The decision, separated from the composition so it can be tested with a narrower capability set
 * than the shipped templates declare.
 *
 * That separation is the point. With the airplane set every section is declared, so a rule that
 * ignored its input would produce exactly the same navigation as one that read it — the two are
 * indistinguishable on screen and in any test that only exercises the shipped template.
 *
 * The data log rollout switch used to sit here as a second filter. It is gone: the template's own
 * section list is the only thing that decides now (T46).
 */
internal fun perThingSectionsFor(capabilities: Capabilities): List<ShellSection> =
  capabilities.sections.mapNotNull { it.toShellSection() }
    .ifEmpty { DEFAULT_PER_THING_SECTIONS }

private fun Section.toShellSection(): ShellSection? = when (this) {
  Section.SECTION_DASHBOARD -> ShellSection.DASHBOARD
  Section.SECTION_SQUAWKS -> ShellSection.SQUAWKS
  Section.SECTION_TASKS -> ShellSection.TASKS
  Section.SECTION_LOGS -> ShellSection.LOGS
  Section.SECTION_DATA_LOGS -> ShellSection.DATA_LOGS
  // A template built by a newer client naming a section this build has no screen for. Dropping it
  // is the only safe reading: the alternative is a tab that navigates nowhere.
  Section.SECTION_UNKNOWN -> null
}
