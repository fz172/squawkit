@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fanfly.wingslog.feature.export.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.export.ExportRecordDateRange
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportHistoryUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.cancel
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_all_time
import wingslog.feature.export.sharedassets.generated.resources.export_email_body
import wingslog.feature.export.sharedassets.generated.resources.export_email_subject
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_body
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_title
import wingslog.feature.export.sharedassets.generated.resources.export_history_empty_body
import wingslog.feature.export.sharedassets.generated.resources.export_history_empty_title
import wingslog.feature.export.sharedassets.generated.resources.export_history_item_meta
import wingslog.feature.export.sharedassets.generated.resources.export_history_new
import wingslog.feature.export.sharedassets.generated.resources.export_history_title
import wingslog.feature.export.sharedassets.generated.resources.export_last_12_months
import wingslog.feature.export.sharedassets.generated.resources.export_last_n_months
import wingslog.feature.export.sharedassets.generated.resources.export_share
import wingslog.feature.export.sharedassets.generated.resources.export_share_title
import wingslog.feature.export.sharedassets.generated.resources.export_size_kb
import wingslog.feature.export.sharedassets.generated.resources.export_size_mb
import wingslog.feature.export.sharedassets.generated.resources.export_size_zero_kb

@Composable
fun ExportHistoryScreen(
  state: ExportHistoryUiState,
  onNavigateBack: () -> Unit,
  onNew: () -> Unit,
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
          EmptyContent(contentModifier, onNew)
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
private fun EmptyContent(modifier: Modifier, onNew: () -> Unit) {
  Column(
    modifier = modifier.padding(horizontal = Spacing.huge),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(
      space = Spacing.large,
      alignment = Alignment.CenterVertically,
    ),
  ) {
    Icon(
      imageVector = Icons.Default.Inbox,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(56.dp),
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
    Button(
      onClick = onNew,
      shape = RoundedCornerShape(Spacing.chipCornerRadius),
    ) {
      Text(stringResource(Res.string.export_history_new))
    }
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
    contentPadding = PaddingValues(vertical = Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    items(exports, key = { it.file_path }) { record ->
      ExportHistoryCard(
        record = record,
        onShareExport = onShareExport,
        onDelete = { onDelete(record) },
      )
    }
  }
}

@Composable
private fun ExportHistoryCard(
  record: ExportRecord,
  onShareExport: (filePath: String, chooserTitle: String, subject: String, body: String) -> Unit,
  onDelete: () -> Unit,
) {
  var showDeleteConfirm by remember { mutableStateOf(false) }
  val shareTitle = stringResource(Res.string.export_share_title)
  val emailSubject = stringResource(Res.string.export_email_subject, record.file_name)
  val emailBody = stringResource(Res.string.export_email_body)
  val aircraftTitle = aircraftSummary(record)
  val scope = scopeLine(record)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        width = Spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .padding(Spacing.medium),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(Spacing.cardCornerRadius))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.FolderZip,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = aircraftTitle,
        style = if (record.aircraft.isNotEmpty()) WingslogTypography.dataMedium else MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (scope.isNotBlank()) {
        Text(
          text = scope,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Icon(
          imageVector = Icons.Default.Schedule,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = stringResource(
            Res.string.export_history_item_meta,
            formatDate(record.created_at_epoch_millis),
            readableBytes(record.size_bytes),
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
      FilledTonalIconButton(
        onClick = { onShareExport(record.file_path, shareTitle, emailSubject, emailBody) },
        modifier = Modifier.size(36.dp),
      ) {
        Icon(
          imageVector = Icons.Default.IosShare,
          contentDescription = stringResource(Res.string.export_share),
          modifier = Modifier.size(18.dp),
        )
      }
      IconButton(
        onClick = { showDeleteConfirm = true },
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = stringResource(Res.string.export_history_delete),
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text(stringResource(Res.string.export_history_delete_confirm_title)) },
      text = {
        Text(stringResource(Res.string.export_history_delete_confirm_body, record.file_name))
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

/** Aircraft tail summary ("N532SL" / "N532SL +2"), falling back to the file name for legacy records. */
private fun aircraftSummary(record: ExportRecord): String {
  val tails = record.aircraft.map { it.tail_number.ifBlank { "—" } }
  return when (tails.size) {
    0 -> record.file_name
    1 -> tails[0]
    else -> "${tails[0]} +${tails.size - 1}"
  }
}

/** "{formats} · {range}" scope line, blank for legacy records that carry no metadata. */
@Composable
private fun scopeLine(record: ExportRecord): String {
  val formats = joinFormats(record.formats)
  val range = rangeLabel(record.date_range)
  return listOf(formats, range).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun joinFormats(formats: List<String>): String = when (formats.size) {
  0 -> ""
  1 -> formats[0]
  2 -> "${formats[0]} + ${formats[1]}"
  else -> "${formats.dropLast(1).joinToString(", ")} + ${formats.last()}"
}

@Composable
private fun rangeLabel(range: ExportRecordDateRange?): String = when (range?.kind) {
  null, "" -> ""
  "ALL_TIME" -> stringResource(Res.string.export_all_time)
  "LAST_N_MONTHS" ->
    if (range.months == 12) stringResource(Res.string.export_last_12_months)
    else stringResource(Res.string.export_last_n_months, range.months)
  "CUSTOM" -> {
    val start = runCatching { LocalDate.parse(range.custom_start).toDisplayFormat() }.getOrDefault(range.custom_start)
    val end = runCatching { LocalDate.parse(range.custom_end).toDisplayFormat() }.getOrDefault(range.custom_end)
    "$start – $end"
  }
  else -> ""
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
  bytes < 1_000_000L -> stringResource(Res.string.export_size_kb, ((bytes + 999L) / 1_000L).toString())
  else -> stringResource(Res.string.export_size_mb, ((bytes / 100_000L) / 10.0).toString())
}
