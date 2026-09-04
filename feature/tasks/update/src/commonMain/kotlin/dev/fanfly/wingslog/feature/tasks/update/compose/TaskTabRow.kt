package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabSpec
import dev.fanfly.wingslog.thing.Capabilities
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adjustments
import wingslog.feature.tasks.update.generated.resources.basics
import wingslog.feature.tasks.update.generated.resources.compliance
import wingslog.feature.tasks.update.generated.resources.schedule
import wingslog.feature.comments.sharedassets.generated.resources.Res as CommentsRes
import wingslog.feature.comments.sharedassets.generated.resources.comments_tab

data class TaskTabSpec(
  val icon: ImageVector,
  val label: StringResource,
)

var BASIC_TAB = TaskTabSpec(Icons.Default.Edit, Res.string.basics)
var COMPLIANCE_TAB = TaskTabSpec(Icons.Default.Info, Res.string.compliance)
var SCHEDULE_TAB = TaskTabSpec(Icons.Default.DateRange, Res.string.schedule)
var ADJUSTMENT_TAB = TaskTabSpec(Icons.Default.Tune, Res.string.adjustments)
var COMMENTS_TAB =
  TaskTabSpec(Icons.Default.Forum, CommentsRes.string.comments_tab)

@Composable
fun TaskTabRow(
  tabs: List<TaskTabSpec>,
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
 * The task form's tabs, as identities rather than positions.
 *
 * The screens used to dispatch on the page *number* — `0 ->`, `1 ->`, `2 ->` — which is exactly what
 * makes a tab impossible to remove: dropping one silently renumbers every tab after it, so the
 * schedule tab would render under the compliance tab's heading. Switching on the identity means a
 * template can omit a tab without any other tab moving.
 */
enum class TaskFormTab {
  IDENTITY,
  COMPLIANCE,
  SCHEDULE,

  /** Edit only — there is nothing to adjust on a task that does not exist yet. */
  ADJUSTMENTS,

  /** Edit only, same reason: a task that does not exist yet has no id to hang a thread on. */
  COMMENTS,
  ;

  /** Stable, locale-independent analytics key. Tied to the identity, not to a tab's position. */
  val analyticsKey: String
    get() = when (this) {
      IDENTITY -> "basics"
      COMPLIANCE -> "compliance"
      SCHEDULE -> "schedule"
      ADJUSTMENTS -> "adjustments"
      COMMENTS -> "comments"
    }
}

internal val TaskFormTab.spec: TaskTabSpec
  get() = when (this) {
    TaskFormTab.IDENTITY -> BASIC_TAB
    TaskFormTab.COMPLIANCE -> COMPLIANCE_TAB
    TaskFormTab.SCHEDULE -> SCHEDULE_TAB
    TaskFormTab.ADJUSTMENTS -> ADJUSTMENT_TAB
    TaskFormTab.COMMENTS -> COMMENTS_TAB
  }

/**
 * Which tabs this template's tasks have (PRD §4.8, `compliance`).
 *
 * **Removal, not disabling.** A homeowner gets no compliance tab at all rather than an empty one:
 * airworthiness directives and service bulletins are an aviation regulator's idea, and a greyed-out
 * tab invites the question "what am I missing?" where an absent one raises nothing.
 *
 * A pure function so it can be tested with `compliance = false`. The airplane template sets it true,
 * so against the shipped set this is indistinguishable from no gate at all.
 */
internal fun taskFormTabsFor(
  capabilities: Capabilities,
  includeAdjustments: Boolean,
  includeComments: Boolean,
): List<TaskFormTab> = TaskFormTab.entries.filter {
  when (it) {
    TaskFormTab.COMPLIANCE -> capabilities.compliance
    TaskFormTab.ADJUSTMENTS -> includeAdjustments
    TaskFormTab.COMMENTS -> includeComments
    else -> true
  }
}
