package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.AviationBlue90
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.core.ui.theme.StatusOkDark
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.viewing.AttachmentSection
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.tasks.sharedassets.generated.resources.compliance_type_ad_short
import wingslog.feature.tasks.sharedassets.generated.resources.compliance_type_sb_short
import wingslog.feature.tasks.sharedassets.generated.resources.edit_task
import wingslog.feature.tasks.sharedassets.generated.resources.maintenance_due_title
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import wingslog.feature.tasks.viewing.generated.resources.Res as ViewingRes
import wingslog.feature.tasks.viewing.generated.resources.badge_overdue
import wingslog.feature.tasks.viewing.generated.resources.completed
import wingslog.feature.tasks.viewing.generated.resources.days_overdue_count
import wingslog.feature.tasks.viewing.generated.resources.days_remaining
import wingslog.feature.tasks.viewing.generated.resources.due_today
import wingslog.feature.tasks.viewing.generated.resources.maintenance_history
import wingslog.feature.tasks.viewing.generated.resources.next_due_date
import wingslog.feature.tasks.viewing.generated.resources.next_due_engine_hrs
import wingslog.feature.tasks.viewing.generated.resources.no_maintenance_logs_for_task
import wingslog.feature.tasks.viewing.generated.resources.on_condition

private val HeroDueDateFormat = LocalDate.Format {
  monthName(MonthNames.ENGLISH_ABBREVIATED)
  char(' ')
  day()
  char(',')
  char(' ')
  year()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
  cardWithStatus: MaintenanceTaskWithStatus,
  logs: List<MaintenanceLog>,
  onDismiss: () -> Unit,
  onEditClick: () -> Unit,
  onAttachmentTap: (Attachment) -> Unit = {},
  syncStates: Map<String, BlobSyncState> = emptyMap(),
  openError: String? = null,
  attachmentEnabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val card = cardWithStatus.card
  val dueStatus = cardWithStatus.dueStatus

  DetailSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    actionSlot = {
      TextButton(onClick = onEditClick) {
        Text(stringResource(SharedRes.string.edit_task))
      }
    },
    headerSlot = {
      // When a badge is visible it occupies this slot and centers with the Edit button.
      // When there is no badge the title takes this slot so it aligns with the button instead.
      val badgeVisible =
        dueStatus.status == DueStatus.OVERDUE || dueStatus.status == DueStatus.DUE_SOON
      if (badgeVisible) {
        StatusBadge(dueStatus)
      } else {
        Text(
          text = card.title,
          style = MaterialTheme.typography.displaySmall,
          color = AviationBlue90,
        )
      }
    },
  ) {
    // Title shown in content only when a badge occupied the header slot
    val badgeVisible =
      dueStatus.status == DueStatus.OVERDUE || dueStatus.status == DueStatus.DUE_SOON
    if (badgeVisible) {
      Text(
        text = card.title,
        style = MaterialTheme.typography.displaySmall,
        color = AviationBlue90,
      )
    }

    // Line 1: compliance type badge (SB / AD), if present
    val typeLabel = when (card.type) {
      ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN ->
        stringResource(SharedRes.string.compliance_type_sb_short)

      ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE ->
        stringResource(SharedRes.string.compliance_type_ad_short)

      else -> null
    }
    if (typeLabel != null) {
      Text(
        text = typeLabel,
        style = MaterialTheme.typography.labelSmall,
        color = if (card.type == ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE)
          MaterialTheme.colorScheme.onErrorContainer
        else
          MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
          .background(
            if (card.type == ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE)
              MaterialTheme.colorScheme.errorContainer
            else
              MaterialTheme.colorScheme.primaryContainer,
            RoundedCornerShape(Spacing.badgeCornerRadius),
          )
          .padding(
            horizontal = 6.dp,
            vertical = Spacing.tiny
          ),
      )
    }

    // Line 2: compliance authority · reference number (dot omitted if only one is present)
    val authority = card.compliance_authority.takeIf { it.isNotBlank() }
    val refNumber = card.reference_number.takeIf { it.isNotBlank() }
    val metaLine = when {
      authority != null && refNumber != null -> "$authority · $refNumber"
      authority != null -> authority
      refNumber != null -> refNumber
      else -> null
    }
    if (metaLine != null) {
      Text(
        text = metaLine,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Description text (notes and compliance details shown as plain body text)
    if (card.notes.isNotBlank()) {
      Text(
        text = card.notes,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(0.85f),
      )
    }
    if (card.compliance_details.isNotBlank()) {
      Text(
        text = card.compliance_details,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(0.85f),
      )
    }

    Spacer(Modifier.height(Spacing.medium))

    // Due date hero
    DueDateHero(dueStatus)

    Spacer(Modifier.height(Spacing.large))

    if (attachmentEnabled) {
      AttachmentSection(
        attachments = card.attachments,
        onAttachmentTap = onAttachmentTap,
        syncStates = syncStates,
        openError = openError,
      )

      if (card.attachments.isNotEmpty()) {
        Spacer(modifier = Modifier.height(Spacing.large))
      }
    }

    Text(
      text = stringResource(ViewingRes.string.maintenance_history),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )

    Spacer(modifier = Modifier.height(Spacing.small))

    if (logs.isEmpty()) {
      Text(
        text = stringResource(ViewingRes.string.no_maintenance_logs_for_task),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      logs.forEach { log ->
        LogHistoryItem(log)
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.extraSmall))
      }
    }
  }
}

@Composable
private fun dueStatusColor(status: DueStatus): Color = when (status) {
  DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
  DueStatus.DUE_SOON -> StatusWarning
  DueStatus.COMPLIED -> StatusOk
  DueStatus.NORMAL -> StatusOk
}

@Composable
private fun StatusBadge(dueStatus: DueMetadata) {
  val (label, bgColor, fgColor) = when {
    dueStatus.status == DueStatus.OVERDUE -> Triple(
      stringResource(ViewingRes.string.badge_overdue),
      MaterialTheme.colorScheme.error,
      MaterialTheme.colorScheme.onError,
    )

    dueStatus.status == DueStatus.DUE_SOON -> Triple(
      stringResource(SharedRes.string.maintenance_due_title),
      StatusWarning,
      Color.Black,
    )

    else -> return
  }
  Text(
    text = label,
    style = MaterialTheme.typography.labelSmall.copy(
      fontWeight = FontWeight.ExtraBold,
      letterSpacing = 0.5.sp,
    ),
    color = fgColor,
    modifier = Modifier
      .background(
        bgColor,
        RoundedCornerShape(20.dp)
      )
      .padding(
        horizontal = Spacing.medium,
        vertical = Spacing.extraSmall
      ),
  )
}

@Composable
private fun DueDateHero(dueStatus: DueMetadata) {
  val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
  val accentColor = dueStatusColor(dueStatus.status)

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
    when {
      dueStatus.status == DueStatus.COMPLIED -> {
        Text(
          text = stringResource(ViewingRes.string.completed),
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
          ),
          color = Color.Black,
          modifier = Modifier
            .background(
              StatusOkDark,
              RoundedCornerShape(20.dp)
            )
            .padding(
              horizontal = Spacing.medium,
              vertical = Spacing.extraSmall
            ),
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
            ViewingRes.string.days_remaining,
            daysUntil
          )

          daysUntil < 0 -> stringResource(
            ViewingRes.string.days_overdue_count,
            -daysUntil
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
          text = HeroDueDateFormat.format(nextDueDate).uppercase(),
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
          text = stringResource(ViewingRes.string.next_due_engine_hrs),
          style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = dueStatus.nextDueEngine!!.toDouble().formatToOneDecimalPlace(),
          style = WingslogTypography.heroDisplay,
          color = accentColor,
        )
      }
    }
  }
}

@Composable
private fun LogHistoryItem(log: MaintenanceLog) {
  val dateStr = if ((log.timestamp?.getEpochSecond() ?: 0L) > 0L) {
    log.timestamp!!.toLocalDate().toDisplayFormat()
  } else {
    stringResource(SharedRes.string.unknown_date)
  }

  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.extraSmall),
    verticalArrangement = Arrangement.spacedBy(Spacing.tiny),
  ) {
    Text(
      text = dateStr,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium
    )
    Text(
      text = log.work_description,
      style = MaterialTheme.typography.bodyMedium
    )
  }
}
