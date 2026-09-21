package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.Preview
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.formatMeterValue
import dev.fanfly.wingslog.core.ui.common.compose.ListRow
import dev.fanfly.wingslog.core.ui.common.compose.ListRowDivider
import dev.fanfly.wingslog.core.ui.common.compose.SectionHeader
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.MaintenanceTask
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.all
import wingslog.feature.tasks.viewing.generated.resources.due_date
import wingslog.feature.tasks.viewing.generated.resources.label_due_engine_value
import wingslog.feature.tasks.viewing.generated.resources.label_expired
import wingslog.feature.tasks.viewing.generated.resources.needs_attention
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.tasks.viewing.generated.resources.Res as ViewingRes

/** How many tasks the dashboard lists before sending the reader to the full list. */
private const val PREVIEW_ROWS = 3

/**
 * The overdue and due-soon tasks, as the first rows of the dashboard. The header counts all of
 * them; the list shows the first [PREVIEW_ROWS] and [onViewAllClick] leads to the rest.
 */
@Composable
fun NeedsAttentionSection(
  tasks: List<MaintenanceTaskWithStatus>,
  onTaskClick: (MaintenanceTaskWithStatus) -> Unit,
  onViewAllClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    SectionHeader(
      title = stringResource(ViewingRes.string.needs_attention),
      count = tasks.size,
      trailing = {
        Text(
          text = stringResource(CoreRes.string.all),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.clickable(onClick = onViewAllClick)
            .padding(Spacing.extraSmall),
        )
      },
    )
    tasks.take(PREVIEW_ROWS)
      .forEachIndexed { index, task ->
        if (index > 0) ListRowDivider()
        ListRow(
          title = AnnotatedString(task.card.title),
          metadata = task.dueLine(),
          onClick = { onTaskClick(task) },
          trailing = {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
        )
      }
  }
}

/** When the task fell or falls due, in the tone of its status; null when it records neither. */
@Composable
private fun MaintenanceTaskWithStatus.dueLine(): AnnotatedString? {
  val isOverdue = dueStatus.status == DueStatus.OVERDUE
  val colors = MaterialTheme.statusColors
  val color = if (isOverdue) colors.critical.accent else colors.caution.accent

  val dueDate = dueStatus.nextDueDate?.toDisplayFormat()
  val dueMeter = dueStatus.nextDueEngine?.let {
    LocalThingTemplate.current.formatMeterValue(dueStatus.nextDueMeterKey, it.toDouble())
  }
  val due = dueDate ?: dueMeter ?: return null
  val text = when {
    isOverdue -> stringResource(ViewingRes.string.label_expired, due)
    dueDate != null -> stringResource(ViewingRes.string.due_date, due)
    else -> stringResource(ViewingRes.string.label_due_engine_value, due)
  }
  return AnnotatedString(text, SpanStyle(color = color))
}

private fun previewTask(
  title: String,
  dueDate: LocalDate? = null,
  dueEngine: Float? = null,
  status: DueStatus = DueStatus.OVERDUE,
) = MaintenanceTaskWithStatus(
  card = MaintenanceTask(title = title),
  dueStatus = DueMetadata(
    nextDueDate = dueDate,
    nextDueEngine = dueEngine,
    nextDueMeterKey = dueEngine?.let { MeterKeys.ENGINE_HOURS },
    status = status,
  ),
)

/** One of each due line: expired by date, expired by meter, due soon — and a fourth that is cut. */
@Preview
@Composable
private fun PreviewNeedsAttentionSection() = NeedsAttentionSection(
  tasks = listOf(
    previewTask("Annual inspection", dueDate = LocalDate(2026, 5, 13)),
    previewTask("100 hour inspection", dueEngine = 1250f),
    previewTask(
      "Transponder certification",
      dueDate = LocalDate(2026, 10, 2),
      status = DueStatus.DUE_SOON,
    ),
    previewTask("ELT battery", dueDate = LocalDate(2026, 10, 9), status = DueStatus.DUE_SOON),
  ),
  onTaskClick = {},
  onViewAllClick = {},
  modifier = Modifier.padding(Spacing.large),
)
