package dev.fanfly.wingslog.feature.squawk.update.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.sharedassets.generated.resources.Res as LogsRes
import wingslog.feature.logs.sharedassets.generated.resources.maintenance_history

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogPickerSheet(
  logs: List<MaintenanceLog>,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        text = stringResource(LogsRes.string.maintenance_history),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      val sorted = logs.sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }

      LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.none)) {
        items(sorted, key = { it.id }) { log ->
          val dateStr = if ((log.timestamp?.getEpochSecond() ?: 0L) > 0L)
            log.timestamp!!.toLocalDate().toDisplayFormat()
          else ""

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelect(log.id) }
              .padding(vertical = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.tiny),
          ) {
            Text(
              text = log.work_description.ifBlank { log.id },
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            if (dateStr.isNotEmpty()) {
              Text(
                text = dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          HorizontalDivider()
        }
      }

      Spacer(Modifier.height(Spacing.huge))
    }
  }
}
