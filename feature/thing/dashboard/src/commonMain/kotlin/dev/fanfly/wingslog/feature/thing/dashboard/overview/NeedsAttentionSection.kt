package dev.fanfly.wingslog.feature.thing.dashboard.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.formatMeterValue
import dev.fanfly.wingslog.core.ui.common.compose.ListRow
import dev.fanfly.wingslog.core.ui.common.compose.ListRowDivider
import dev.fanfly.wingslog.core.ui.common.compose.SectionHeader
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.Squawk
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.viewing.generated.resources.due_date
import wingslog.feature.tasks.viewing.generated.resources.label_due_engine_value
import wingslog.feature.tasks.viewing.generated.resources.label_expired
import wingslog.feature.thing.dashboard.generated.resources.overview_needs_attention
import wingslog.feature.tasks.viewing.generated.resources.Res as TasksViewingRes
import wingslog.feature.thing.dashboard.generated.resources.Res as DashboardRes

/**
 * Everything that wants the owner now, as the first rows of the dashboard: the squawks that put
 * the thing in its down state, then the overdue and due-soon tasks.
 *
 * Every item is listed and there is no "all" link — the rows come from two lists, so no one list
 * is the rest of them. Each row leads to its own.
 */
@Composable
fun NeedsAttentionSection(
  downSquawks: List<Squawk>,
  tasks: List<MaintenanceTaskWithStatus>,
  onSquawkClick: (Squawk) -> Unit,
  onTaskClick: (MaintenanceTaskWithStatus) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (downSquawks.isEmpty() && tasks.isEmpty()) return
  val blocking = MaterialTheme.statusColors.blocking.accent
  val downStatus =
    LexiconFormatter.titleCase(LocalThingLexicon.current.down_status)
  Column(modifier = modifier) {
    SectionHeader(
      title = stringResource(DashboardRes.string.overview_needs_attention),
      count = downSquawks.size + tasks.size,
    )
    // One list of rows: the down-state squawks lead, told apart by tone rather than by a box.
    downSquawks.forEachIndexed { index, squawk ->
      if (index > 0) ListRowDivider()
      ListRow(
        title = AnnotatedString(squawk.title, SpanStyle(color = blocking)),
        metadata = AnnotatedString(downStatus, SpanStyle(color = blocking)),
        onClick = { onSquawkClick(squawk) },
        trailing = { RowChevron() },
      )
    }
    tasks.forEachIndexed { index, task ->
      if (index > 0 || downSquawks.isNotEmpty()) ListRowDivider()
      ListRow(
        title = AnnotatedString(task.card.title),
        metadata = task.dueLine(),
        onClick = { onTaskClick(task) },
        trailing = { RowChevron() },
      )
    }
  }
}

@Composable
private fun RowChevron() {
  Icon(
    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** When the task fell or falls due, in the tone of its status; null when it records neither. */
@Composable
private fun MaintenanceTaskWithStatus.dueLine(): AnnotatedString? {
  val isOverdue = dueStatus.status == DueStatus.OVERDUE
  val colors = MaterialTheme.statusColors
  val color = if (isOverdue) colors.critical.accent else colors.caution.accent

  val dueDate = dueStatus.nextDueDate?.toDisplayFormat()
  val dueMeter = dueStatus.nextDueEngine?.let {
    LocalThingTemplate.current.formatMeterValue(
      dueStatus.nextDueMeterKey,
      it.toDouble()
    )
  }
  val due = dueDate ?: dueMeter ?: return null
  val text = when {
    isOverdue -> stringResource(TasksViewingRes.string.label_expired, due)
    dueDate != null -> stringResource(TasksViewingRes.string.due_date, due)
    else -> stringResource(TasksViewingRes.string.label_due_engine_value, due)
  }
  return AnnotatedString(text, SpanStyle(color = color))
}
