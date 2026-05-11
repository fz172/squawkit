package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.edit_squawk
import wingslog.feature.squawk.sharedassets.generated.resources.no_work_recorded
import wingslog.feature.squawk.sharedassets.generated.resources.reported
import wingslog.feature.squawk.sharedassets.generated.resources.work_history

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquawkDetailSheet(
  item: SquawkWithStatus,
  addressingLog: MaintenanceLog?,
  onDismiss: () -> Unit,
  onEditClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val squawk = item.squawk

  DetailSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    actionSlot = {
      TextButton(onClick = onEditClick) {
        Text(stringResource(Res.string.edit_squawk))
      }
    },
    headerSlot = {
      Text(
        text = squawk.title,
        style = MaterialTheme.typography.displaySmall,
      )
    },
  ) {
    // Priority chip
    PriorityBadge(item)

    // Reported date
    if ((squawk.created_at?.getEpochSecond() ?: 0L) > 0L) {
      val dateStr = squawk.created_at!!.toLocalDate().toDisplayFormat()
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
        Text(
          text = stringResource(Res.string.reported),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = dateStr,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    // Description
    if (squawk.description.isNotBlank()) {
      Text(
        text = squawk.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(0.9f),
      )
    }

    Spacer(Modifier.height(Spacing.medium))

    // Work History section
    Text(
      text = stringResource(Res.string.work_history),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )

    Spacer(Modifier.height(Spacing.small))

    if (addressingLog == null) {
      Text(
        text = stringResource(Res.string.no_work_recorded),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      LogHistoryRow(addressingLog)
    }
  }
}

@Composable
private fun LogHistoryRow(log: MaintenanceLog) {
  val dateStr = if ((log.timestamp?.getEpochSecond() ?: 0L) > 0L)
    log.timestamp!!.toLocalDate().toDisplayFormat()
  else ""

  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.extraSmall),
    verticalArrangement = Arrangement.spacedBy(Spacing.tiny),
  ) {
    if (dateStr.isNotEmpty()) {
      Text(
        text = dateStr,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
    }
    Text(
      text = log.work_description,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
  HorizontalDivider(modifier = Modifier.padding(top = Spacing.extraSmall))
}
