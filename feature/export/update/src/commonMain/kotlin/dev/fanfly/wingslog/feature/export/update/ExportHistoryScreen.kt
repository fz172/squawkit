@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fanfly.wingslog.feature.export.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.export.datamanager.ExportRecord
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportHistoryUiState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.cancel
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_email_body
import wingslog.feature.export.sharedassets.generated.resources.export_email_subject
import wingslog.feature.export.sharedassets.generated.resources.export_history_actions
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_body
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_title
import wingslog.feature.export.sharedassets.generated.resources.export_history_empty_body
import wingslog.feature.export.sharedassets.generated.resources.export_history_empty_title
import wingslog.feature.export.sharedassets.generated.resources.export_history_item_meta
import wingslog.feature.export.sharedassets.generated.resources.export_history_title
import wingslog.feature.export.sharedassets.generated.resources.export_share
import wingslog.feature.export.sharedassets.generated.resources.export_share_title
import wingslog.feature.export.sharedassets.generated.resources.export_size_kb
import wingslog.feature.export.sharedassets.generated.resources.export_size_mb
import wingslog.feature.export.sharedassets.generated.resources.export_size_zero_kb

@Composable
fun ExportHistoryScreen(
  state: ExportHistoryUiState,
  onNavigateBack: () -> Unit,
  onShareExport: (filePath: String, chooserTitle: String, subject: String, body: String) -> Unit,
  onDelete: (ExportRecord) -> Unit,
) {
  Scaffold(
    topBar = {
      WingsLogTopAppBar(
        title = stringResource(Res.string.export_history_title),
        onBackClick = onNavigateBack,
      )
    },
  ) { innerPadding ->
    val contentModifier = Modifier.padding(innerPadding).fillMaxSize()
    when (state) {
      is ExportHistoryUiState.Loading -> LoadingContent(contentModifier)
      is ExportHistoryUiState.Loaded ->
        if (state.exports.isEmpty()) {
          EmptyContent(contentModifier)
        } else {
          ExportList(
            exports = state.exports,
            modifier = contentModifier,
            onShareExport = onShareExport,
            onDelete = onDelete,
          )
        }
    }
  }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator()
  }
}

@Composable
private fun EmptyContent(modifier: Modifier) {
  Column(
    modifier = modifier.padding(Spacing.screenPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(
      space = Spacing.large,
      alignment = Alignment.CenterVertically,
    ),
  ) {
    Icon(
      imageVector = Icons.Default.FileDownload,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = stringResource(Res.string.export_history_empty_title),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Text(
      text = stringResource(Res.string.export_history_empty_body),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun ExportList(
  exports: List<ExportRecord>,
  modifier: Modifier,
  onShareExport: (filePath: String, chooserTitle: String, subject: String, body: String) -> Unit,
  onDelete: (ExportRecord) -> Unit,
) {
  LazyColumn(
    modifier = modifier.padding(horizontal = Spacing.screenPadding),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    items(exports, key = { it.filePath }) { record ->
      ExportHistoryRow(
        record = record,
        onShareExport = onShareExport,
        onDelete = { onDelete(record) },
      )
    }
  }
}

@Composable
private fun ExportHistoryRow(
  record: ExportRecord,
  onShareExport: (filePath: String, chooserTitle: String, subject: String, body: String) -> Unit,
  onDelete: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  val shareTitle = stringResource(Res.string.export_share_title)
  val emailSubject = stringResource(Res.string.export_email_subject, record.fileName)
  val emailBody = stringResource(Res.string.export_email_body)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Default.Description,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(end = Spacing.medium),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = record.fileName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = stringResource(
          Res.string.export_history_item_meta,
          formatDate(record.createdAtEpochMillis),
          readableBytes(record.sizeBytes),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Box {
      IconButton(onClick = { menuExpanded = true }) {
        Icon(
          imageVector = Icons.Default.MoreVert,
          contentDescription = stringResource(Res.string.export_history_actions),
        )
      }
      DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
      ) {
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.export_share)) },
          leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
          onClick = {
            menuExpanded = false
            onShareExport(record.filePath, shareTitle, emailSubject, emailBody)
          },
        )
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.export_history_delete)) },
          leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
          onClick = {
            menuExpanded = false
            showDeleteConfirm = true
          },
        )
      }
    }
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text(stringResource(Res.string.export_history_delete_confirm_title)) },
      text = {
        Text(stringResource(Res.string.export_history_delete_confirm_body, record.fileName))
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteConfirm = false
            onDelete()
          },
        ) {
          Text(stringResource(Res.string.export_history_delete).uppercase())
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) {
          Text(stringResource(CoreRes.string.cancel).uppercase())
        }
      },
    )
  }
}

@Composable
private fun formatDate(epochMillis: Long): String =
  Instant.fromEpochMilliseconds(epochMillis)
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date
    .toDisplayFormat()

@Composable
private fun readableBytes(bytes: Long): String = when {
  bytes <= 0L -> stringResource(Res.string.export_size_zero_kb)
  bytes < 1_000_000L -> stringResource(
    Res.string.export_size_kb,
    ((bytes + 999L) / 1_000L).toString(),
  )
  else -> stringResource(
    Res.string.export_size_mb,
    ((bytes / 100_000L) / 10.0).toString(),
  )
}
