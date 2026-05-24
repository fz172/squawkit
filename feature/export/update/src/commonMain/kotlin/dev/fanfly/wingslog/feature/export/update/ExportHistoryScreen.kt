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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.export.ExportRecordDateRange
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportHistoryUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.cancel
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_all_time
import wingslog.feature.export.sharedassets.generated.resources.export_email_body
import wingslog.feature.export.sharedassets.generated.resources.export_email_subject
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_body
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_body_cloud_only
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_body_device_and_cloud
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_title
import wingslog.feature.export.sharedassets.generated.resources.export_history_empty_body
import wingslog.feature.export.sharedassets.generated.resources.export_history_empty_title
import wingslog.feature.export.sharedassets.generated.resources.export_history_item_meta
import wingslog.feature.export.sharedassets.generated.resources.export_history_menu_delete
import wingslog.feature.export.sharedassets.generated.resources.export_history_menu_resend
import wingslog.feature.export.sharedassets.generated.resources.export_history_menu_save
import wingslog.feature.export.sharedassets.generated.resources.export_history_menu_share
import wingslog.feature.export.sharedassets.generated.resources.export_history_more_actions
import wingslog.feature.export.sharedassets.generated.resources.export_history_new
import wingslog.feature.export.sharedassets.generated.resources.export_history_status_cloud
import wingslog.feature.export.sharedassets.generated.resources.export_history_status_device
import wingslog.feature.export.sharedassets.generated.resources.export_history_title
import wingslog.feature.export.sharedassets.generated.resources.export_last_12_months
import wingslog.feature.export.sharedassets.generated.resources.export_last_n_months
import wingslog.feature.export.sharedassets.generated.resources.export_share_title
import wingslog.feature.export.sharedassets.generated.resources.export_size_kb
import wingslog.feature.export.sharedassets.generated.resources.export_size_mb
import wingslog.feature.export.sharedassets.generated.resources.export_size_zero_kb
import kotlin.time.Instant
import wingslog.core.ui.generated.resources.Res as CoreRes

@Composable
fun ExportHistoryScreen(
  state: ExportHistoryUiState,
  onNavigateBack: () -> Unit,
  onNew: () -> Unit,
  onShareExport: (filePath: String, chooserTitle: String, subject: String, body: String) -> Unit,
  onResendDelivery: (ExportRecord) -> Unit,
  onRetryDelivery: (ExportRecord) -> Unit,
  onSaveToDevice: (ExportRecord) -> Unit,
  onDelete: (ExportRecord) -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  Scaffold(
    topBar = {
      WingsLogTopAppBar(
        title = stringResource(Res.string.export_history_title),
        onBackClick = onNavigateBack,
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    val contentModifier = Modifier.padding(innerPadding)
      .fillMaxSize()
    when (state) {
      is ExportHistoryUiState.Loading -> LoadingContent(contentModifier)
      is ExportHistoryUiState.Loaded ->
        if (state.exports.isEmpty()) {
          EmptyContent(contentModifier, onNew)
        } else {
          ExportList(
            exports = state.exports,
            canEmailDelivery = state.canEmailDelivery,
            modifier = contentModifier,
            onShareExport = onShareExport,
            onResendDelivery = onResendDelivery,
            onRetryDelivery = onRetryDelivery,
            onSaveToDevice = onSaveToDevice,
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
  canEmailDelivery: Boolean,
  modifier: Modifier,
  onShareExport: (filePath: String, chooserTitle: String, subject: String, body: String) -> Unit,
  onResendDelivery: (ExportRecord) -> Unit,
  onRetryDelivery: (ExportRecord) -> Unit,
  onSaveToDevice: (ExportRecord) -> Unit,
  onDelete: (ExportRecord) -> Unit,
) {
  LazyColumn(
    modifier = modifier.padding(horizontal = Spacing.screenPadding),
    contentPadding = PaddingValues(vertical = Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    items(exports, key = { it.export_id }) { record ->
      ExportHistoryCard(
        record = record,
        canEmailDelivery = canEmailDelivery,
        onShareExport = onShareExport,
        onResendDelivery = { onResendDelivery(record) },
        onRetryDelivery = { onRetryDelivery(record) },
        onSaveToDevice = { onSaveToDevice(record) },
        onDelete = { onDelete(record) },
      )
    }
  }
}

@Composable
private fun ExportHistoryCard(
  record: ExportRecord,
  canEmailDelivery: Boolean,
  onShareExport: (filePath: String, chooserTitle: String, subject: String, body: String) -> Unit,
  onResendDelivery: () -> Unit,
  onRetryDelivery: () -> Unit,
  onSaveToDevice: () -> Unit,
  onDelete: () -> Unit,
) {
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var menuExpanded by remember { mutableStateOf(false) }
  val shareTitle = stringResource(Res.string.export_share_title)
  val emailSubject =
    stringResource(Res.string.export_email_subject, record.file_name)
  val emailBody = stringResource(Res.string.export_email_body)
  val aircraftTitle = aircraftSummary(record)
  val scope = scopeLine(record)
  val onDevice = record.file_path.isNotBlank()
  val canRetry =
    record.persisted_delivery_state == "FAILED" &&
      record.remote_archive_ref.isNotBlank() &&
      record.destination_email.isNotBlank()
  // Email-account users re-send the export by email from the remote archive (no local file needed).
  // The retry affordance already covers the failed case, so don't double up.
  val canResend = canEmailDelivery &&
    record.remote_archive_ref.isNotBlank() &&
    record.destination_email.isNotBlank() &&
    !canRetry
  // Any on-device file can go through the native share sheet, including one an email user pulled
  // down via "Save to device".
  val canShareDevice = onDevice
  // Remote-only archives can be pulled down to the device for offline sharing.
  val canSaveToDevice = !onDevice && record.remote_archive_ref.isNotBlank()
  val hasUpperMenuItems =
    canResend || canRetry || canShareDevice || canSaveToDevice

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
    verticalAlignment = Alignment.CenterVertically,
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
      val storageStatus = exportStorageStatus(
        canEmailDelivery = canEmailDelivery,
        onDevice = onDevice,
        onRemote = record.remote_archive_ref.isNotBlank(),
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Text(
          text = stringResource(
            Res.string.export_history_item_meta,
            formatDate(record.created_at_epoch_millis),
            readableBytes(record.size_bytes),
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (storageStatus != null) {
          Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Icon(
            imageVector = storageStatus.icon,
            contentDescription = null,
            tint = storageStatus.color,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = stringResource(storageStatus.label),
            style = MaterialTheme.typography.bodySmall,
            color = storageStatus.color,
          )
        }
      }
    }

    Box {
      IconButton(
        onClick = { menuExpanded = true },
        modifier = Modifier.size(40.dp),
      ) {
        Icon(
          imageVector = Icons.Default.MoreVert,
          contentDescription = stringResource(Res.string.export_history_more_actions),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
      ) {
        if (canResend || canRetry) {
          DropdownMenuItem(
            text = { Text(stringResource(Res.string.export_history_menu_resend)) },
            leadingIcon = {
              Icon(
                Icons.Default.Email,
                contentDescription = null
              )
            },
            onClick = {
              menuExpanded = false
              if (canRetry) onRetryDelivery() else onResendDelivery()
            },
          )
        }
        if (canShareDevice) {
          DropdownMenuItem(
            text = { Text(stringResource(Res.string.export_history_menu_share)) },
            leadingIcon = {
              Icon(
                Icons.Default.IosShare,
                contentDescription = null
              )
            },
            onClick = {
              menuExpanded = false
              onShareExport(
                record.file_path,
                shareTitle,
                emailSubject,
                emailBody
              )
            },
          )
        }
        if (canSaveToDevice) {
          DropdownMenuItem(
            text = { Text(stringResource(Res.string.export_history_menu_save)) },
            leadingIcon = {
              Icon(
                Icons.Default.Download,
                contentDescription = null
              )
            },
            onClick = {
              menuExpanded = false
              onSaveToDevice()
            },
          )
        }
        if (hasUpperMenuItems) {
          HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.extraSmall),
            color = MaterialTheme.colorScheme.outlineVariant,
          )
        }
        DropdownMenuItem(
          text = {
            Text(
              text = stringResource(Res.string.export_history_menu_delete),
              color = MaterialTheme.colorScheme.error,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
            )
          },
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
        Text(deleteConfirmBody(record))
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
private fun deleteConfirmBody(record: ExportRecord): String = when {
  record.file_path.isNotBlank() && record.remote_archive_ref.isNotBlank() ->
    stringResource(
      Res.string.export_history_delete_confirm_body_device_and_cloud,
      record.file_name
    )

  record.remote_archive_ref.isNotBlank() ->
    stringResource(
      Res.string.export_history_delete_confirm_body_cloud_only,
      record.file_name
    )

  else ->
    stringResource(
      Res.string.export_history_delete_confirm_body,
      record.file_name
    )
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
  return listOf(formats, range).filter { it.isNotBlank() }
    .joinToString(" · ")
}

private fun joinFormats(formats: List<String>): String = when (formats.size) {
  0 -> ""
  1 -> formats[0]
  2 -> "${formats[0]} + ${formats[1]}"
  else -> "${
    formats.dropLast(1)
      .joinToString(", ")
  } + ${formats.last()}"
}

@Composable
private fun rangeLabel(range: ExportRecordDateRange?): String =
  when (range?.kind) {
    null, "" -> ""
    "ALL_TIME" -> stringResource(Res.string.export_all_time)
    "LAST_N_MONTHS" ->
      if (range.months == 12) stringResource(Res.string.export_last_12_months)
      else stringResource(Res.string.export_last_n_months, range.months)

    "CUSTOM" -> {
      val start = runCatching {
        LocalDate.parse(range.custom_start)
          .toDisplayFormat()
      }.getOrDefault(range.custom_start)
      val end = runCatching {
        LocalDate.parse(range.custom_end)
          .toDisplayFormat()
      }.getOrDefault(range.custom_end)
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
  bytes < 1_000_000L -> stringResource(
    Res.string.export_size_kb,
    ((bytes + 999L) / 1_000L).toString()
  )

  else -> stringResource(
    Res.string.export_size_mb,
    ((bytes / 100_000L) / 10.0).toString()
  )
}

private data class StorageStatus(
  val icon: ImageVector,
  val color: Color,
  val label: StringResource,
)

/**
 * Where the archive currently lives, for cloud-sync users. Returns null when no badge is warranted:
 * a no-email user (everything is local) or a fully synced file (local and remote).
 */
@Composable
private fun exportStorageStatus(
  canEmailDelivery: Boolean,
  onDevice: Boolean,
  onRemote: Boolean,
): StorageStatus? = when {
  !canEmailDelivery -> null
  onDevice && onRemote -> null
  onRemote -> StorageStatus(
    icon = Icons.Outlined.Cloud,
    color = MaterialTheme.colorScheme.primary,
    label = Res.string.export_history_status_cloud,
  )

  onDevice -> StorageStatus(
    icon = Icons.Outlined.Smartphone,
    color = StatusWarning,
    label = Res.string.export_history_status_device,
  )

  else -> null
}
