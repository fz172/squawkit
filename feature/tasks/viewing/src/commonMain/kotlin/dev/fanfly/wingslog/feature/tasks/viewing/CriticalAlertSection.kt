package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.formatMeterValue
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.MaintenanceTask
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.viewing.generated.resources.critical_airworthiness
import wingslog.feature.tasks.viewing.generated.resources.due_date
import wingslog.feature.tasks.viewing.generated.resources.label_due_engine_value
import wingslog.feature.tasks.viewing.generated.resources.label_expired
import wingslog.feature.tasks.viewing.generated.resources.maintenance_due_subtitle
import wingslog.feature.tasks.viewing.generated.resources.Res as ViewingRes


@Composable
fun CriticalAlertsSection(
  overdueTasks: List<MaintenanceTaskWithStatus>,
  onCardClick: (MaintenanceTaskWithStatus) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasOverdue = overdueTasks.any { it.dueStatus.status == DueStatus.OVERDUE }
  val colors = MaterialTheme.statusColors
  val titleColor =
    if (hasOverdue) colors.critical.accent else colors.caution.accent

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column {
      // --- Card header: title + subtitle ---
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            horizontal = Spacing.large,
            vertical = Spacing.extraLarge
          ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Text(
          text = LexiconFormatter.titleCase(LocalThingLexicon.current.due_status),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = titleColor,
        )
        Text(
          text = stringResource(
            ViewingRes.string.maintenance_due_subtitle,
            LexiconFormatter.sentenceCasePlural(LocalThingLexicon.current.taskNoun),
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

      // --- Attention list ---
      Column(
        modifier = Modifier.padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Text(
          text = stringResource(ViewingRes.string.critical_airworthiness),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          letterSpacing = 1.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
          overdueTasks.forEach { inspection ->
            CriticalAlertItem(
              cardWithStatus = inspection,
              onClick = { onCardClick(inspection) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CriticalAlertItem(
  cardWithStatus: MaintenanceTaskWithStatus,
  onClick: () -> Unit,
) {
  val isOverdue = cardWithStatus.dueStatus.status == DueStatus.OVERDUE
  val colors = MaterialTheme.statusColors
  val dotColor =
    if (isOverdue) colors.critical.accent else colors.caution.accent

  val dueDate = cardWithStatus.dueStatus.nextDueDate
  val dueEngine = cardWithStatus.dueStatus.nextDueEngine
  val dueMeterKey = cardWithStatus.dueStatus.nextDueMeterKey
  val statusText = when {
    isOverdue && dueDate != null ->
      stringResource(
        ViewingRes.string.label_expired,
        dueDate.toDisplayFormat()
      )

    isOverdue && dueEngine != null ->
      stringResource(
        ViewingRes.string.label_expired,
        LocalThingTemplate.current.formatMeterValue(
          dueMeterKey,
          dueEngine.toDouble()
        )
      )

    dueDate != null ->
      stringResource(
        ViewingRes.string.due_date,
        dueDate.toDisplayFormat()
      )

    dueEngine != null ->
      stringResource(
        ViewingRes.string.label_due_engine_value,
        LocalThingTemplate.current.formatMeterValue(
          dueMeterKey,
          dueEngine.toDouble()
        )
      )

    else -> ""
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = Spacing.extraSmall),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    verticalAlignment = Alignment.Top,
  ) {
    Box(
      modifier = Modifier
        .padding(top = Spacing.extraSmall)
        .size(Spacing.extraSmall)
        .background(
          dotColor,
          CircleShape
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
      Text(
        text = cardWithStatus.card.title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (statusText.isNotBlank()) {
        Text(
          text = statusText,
          style = MaterialTheme.typography.labelSmall,
          color = dotColor,
        )
      }
    }
  }
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

/** One of each status line the section can draw: expired by date, expired by meter, and due soon. */
@Preview
@Composable
private fun PreviewCriticalAlertsSection() = CriticalAlertsSection(
  overdueTasks = listOf(
    previewTask("Annual inspection", dueDate = LocalDate(2026, 5, 13)),
    previewTask("100 hour inspection", dueEngine = 1250f),
    previewTask(
      "Transponder certification",
      dueDate = LocalDate(2026, 10, 2),
      status = DueStatus.DUE_SOON,
    ),
  ),
  onCardClick = {},
  modifier = Modifier.padding(Spacing.large),
)

/** The caution colouring: nothing is expired yet, so the heading is amber rather than red. */
@Preview
@Composable
private fun PreviewCriticalAlertsSectionDueSoonOnly() = CriticalAlertsSection(
  overdueTasks = listOf(
    previewTask(
      "Pitot-static check",
      dueDate = LocalDate(2026, 10, 2),
      status = DueStatus.DUE_SOON,
    ),
  ),
  onCardClick = {},
  modifier = Modifier.padding(Spacing.large),
)
