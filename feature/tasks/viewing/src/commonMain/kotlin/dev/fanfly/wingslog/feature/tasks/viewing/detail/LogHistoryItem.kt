package dev.fanfly.wingslog.feature.tasks.viewing.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes

@Composable
internal fun LogHistoryItem(log: MaintenanceLog) {
  val dateStr = if ((log.timestamp?.getEpochSecond() ?: 0L) > 0L) {
    log.timestamp!!.toLocalDate()
      .toDisplayFormat()
  } else {
    stringResource(SharedRes.string.unknown_date)
  }

  Column(
    modifier = Modifier.fillMaxWidth()
      .padding(vertical = Spacing.extraSmall),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Text(
      text = dateStr,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium
    )
    Text(
      text = log.work_description, style = MaterialTheme.typography.bodyMedium
    )
  }
}
