package dev.fanfly.wingslog.feature.tasks.viewing.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.formatMeterValue
import dev.fanfly.wingslog.core.template.meter
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.viewing.generated.resources.completed
import wingslog.feature.tasks.viewing.generated.resources.days_overdue_count
import wingslog.feature.tasks.viewing.generated.resources.days_remaining
import wingslog.feature.tasks.viewing.generated.resources.due_today
import wingslog.feature.tasks.viewing.generated.resources.engine_hours_label
import wingslog.feature.tasks.viewing.generated.resources.next_due_date
import wingslog.feature.tasks.viewing.generated.resources.next_due_meter
import wingslog.feature.tasks.viewing.generated.resources.on_condition
import wingslog.feature.tasks.viewing.generated.resources.Res as ViewingRes

@Composable
internal fun DueDateHero(dueStatus: DueMetadata) {
  val today = Clock.System.now()
    .toLocalDateTime(TimeZone.currentSystemDefault()).date
  val accentColor = dueStatusColor(dueStatus.status)

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
    when {
      dueStatus.status == DueStatus.COMPLIED -> {
        StatusChip(
          label = stringResource(ViewingRes.string.completed),
          tier = StatusTier.POSITIVE,
        )
      }

      dueStatus.isOnCondition -> {
        Text(
          text = stringResource(ViewingRes.string.on_condition),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      dueStatus.nextDueDate != null -> {
        val nextDueDate = dueStatus.nextDueDate!!
        val daysUntil = today.daysUntil(nextDueDate)
        val countdownText = when {
          daysUntil > 0 -> stringResource(
            ViewingRes.string.days_remaining, daysUntil
          )

          daysUntil < 0 -> stringResource(
            ViewingRes.string.days_overdue_count, -daysUntil
          )

          else -> stringResource(ViewingRes.string.due_today)
        }

        Text(
          text = stringResource(ViewingRes.string.next_due_date),
          style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = nextDueDate.toDisplayFormat(numberOnly = false),
          style = WingslogTypography.heroDisplay,
          color = accentColor,
        )
        Text(
          text = countdownText,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      dueStatus.nextDueEngine != null -> {
        Text(
          // "NEXT DUE ODOMETER" on a car. The heading named the meter as well as the value, so
          // both had to stop assuming hours (#759).
          text = stringResource(
            ViewingRes.string.next_due_meter,
            (
              LocalThingTemplate.current.meter(dueStatus.nextDueMeterKey.orEmpty())?.label
                ?: stringResource(ViewingRes.string.engine_hours_label)
              ),
          ),
          style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = LocalThingTemplate.current.formatMeterValue(
            dueStatus.nextDueMeterKey,
            dueStatus.nextDueEngine!!.toDouble(),
          ),
          style = WingslogTypography.heroDisplay,
          color = accentColor,
        )
      }
    }
  }
}
