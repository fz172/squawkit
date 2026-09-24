package dev.fanfly.wingslog.feature.export.update.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.export.ExportRecord

@Composable
internal fun ExportList(
  exports: List<ExportRecord>,
  canEmailDelivery: Boolean,
  modifier: Modifier,
  onDownloadExport: (exportId: String, filePath: String, fileName: String) -> Unit,
  onResendDelivery: (ExportRecord) -> Unit,
  onRetryDelivery: (ExportRecord) -> Unit,
  onSaveToDevice: (ExportRecord) -> Unit,
  onDelete: (ExportRecord) -> Unit,
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxHeight()
        .constrainedContentWidth(ContentWidth.Reading)
        .padding(horizontal = Spacing.screenPadding),
      contentPadding = PaddingValues(vertical = Spacing.small),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      items(exports, key = { it.export_id }) { record ->
        ExportHistoryCard(
          record = record,
          canEmailDelivery = canEmailDelivery,
          onDownloadExport = onDownloadExport,
          onResendDelivery = { onResendDelivery(record) },
          onRetryDelivery = { onRetryDelivery(record) },
          onSaveToDevice = { onSaveToDevice(record) },
          onDelete = { onDelete(record) },
        )
      }
    }
  }
}
