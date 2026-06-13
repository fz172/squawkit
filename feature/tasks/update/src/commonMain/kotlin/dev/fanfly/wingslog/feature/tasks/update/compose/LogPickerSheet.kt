package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectableRow
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectionMode
import dev.fanfly.wingslog.core.ui.common.compose.PickerSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.sharedassets.generated.resources.maintenance_history
import wingslog.feature.logs.sharedassets.generated.resources.Res as LogsRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogPickerSheet(
  logs: List<MaintenanceLog>,
  onSelect: (MaintenanceLog) -> Unit,
  onDismiss: () -> Unit,
) = PickerSheet(
  onDismiss = onDismiss,
  headerSlot = {
    Text(
      text = stringResource(LogsRes.string.maintenance_history),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
    )
  },
) {
  val sorted = logs.sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
  LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.none)) {
    items(sorted, key = { it.id }) { log ->
      val dateStr = if ((log.timestamp?.getEpochSecond() ?: 0L) > 0L) {
        log.timestamp!!.toLocalDate().toDisplayFormat()
      } else ""
      PickerSelectableRow(
        title = log.work_description.ifBlank { log.id },
        subtitle = dateStr,
        selected = false,
        selectionMode = PickerSelectionMode.RADIO,
        titleStyle = MaterialTheme.typography.bodyLarge,
        titleWeight = FontWeight.Medium,
        titleMaxLines = 2,
        titleOverflow = TextOverflow.Ellipsis,
        onClick = { onSelect(log) },
      )
    }
  }
}
