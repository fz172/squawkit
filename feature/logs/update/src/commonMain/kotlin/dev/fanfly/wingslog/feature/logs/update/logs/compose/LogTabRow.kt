package dev.fanfly.wingslog.feature.logs.update.logs.compose

import dev.fanfly.wingslog.thing.Capabilities
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabSpec
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.log_tab_hours
import wingslog.feature.logs.update.generated.resources.log_tab_records
import wingslog.feature.logs.update.generated.resources.log_tab_work

data class LogTabSpec(
  val icon: ImageVector,
  val label: StringResource,
)

val LOG_WORK_TAB = LogTabSpec(Icons.Default.Build, Res.string.log_tab_work)
val LOG_HOURS_TAB = LogTabSpec(Icons.Default.Schedule, Res.string.log_tab_hours)
val LOG_RECORDS_TAB = LogTabSpec(Icons.Default.Link, Res.string.log_tab_records)

/** Stable, locale-independent analytics keys for the log-form tabs, in tab order. */
val LOG_FORM_TAB_KEYS = listOf("work", "hours", "records")

@Composable
fun LogTabRow(
  tabs: List<LogTabSpec>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  IconLabelTabRow(
    tabs = tabs.map { IconLabelTabSpec(it.icon, stringResource(it.label)) },
    selectedIndex = selectedIndex,
    onSelect = onSelect,
    modifier = modifier,
  )
}

/**
 * The log form's tabs, as identities rather than positions.
 *
 * Same reason as `TaskFormTab`: dispatching on the page number makes a tab impossible to remove,
 * because dropping one renumbers every tab after it and the next form renders under the wrong
 * heading with no error anywhere.
 */
enum class LogFormTab {
  WORK,

  /** Meter readings — airframe, engine and prop time on an airplane. */
  HOURS,
  RECORDS,
}

internal val LogFormTab.spec: LogTabSpec
  get() = when (this) {
    LogFormTab.WORK -> LOG_WORK_TAB
    LogFormTab.HOURS -> LOG_HOURS_TAB
    LogFormTab.RECORDS -> LOG_RECORDS_TAB
  }

/**
 * Which tabs this template's logs have (PRD §4.8, `meters`).
 *
 * **Removal, not disabling.** A template with no meters gets no hours tab at all: a house has no
 * running total to record against, and an empty "Hours" tab is a question the user cannot answer.
 *
 * A pure function so it can be tested with `meters = false`. The airplane template sets it true, so
 * against the shipped set this is indistinguishable from no gate.
 */
internal fun logFormTabsFor(capabilities: Capabilities): List<LogFormTab> =
  LogFormTab.entries.filter { it != LogFormTab.HOURS || capabilities.meters }
