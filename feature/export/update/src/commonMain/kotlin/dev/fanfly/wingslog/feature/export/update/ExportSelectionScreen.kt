@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fanfly.wingslog.feature.export.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.update.viewmodel.AircraftSelectionRow
import dev.fanfly.wingslog.feature.export.update.viewmodel.DateRangeOption
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.done
import wingslog.core.ui.generated.resources.retry
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_aircraft_section
import wingslog.feature.export.sharedassets.generated.resources.export_all_time
import wingslog.feature.export.sharedassets.generated.resources.export_clear_all
import wingslog.feature.export.sharedassets.generated.resources.export_custom
import wingslog.feature.export.sharedassets.generated.resources.export_custom_end_date
import wingslog.feature.export.sharedassets.generated.resources.export_custom_start_date
import wingslog.feature.export.sharedassets.generated.resources.export_date_range_section
import wingslog.feature.export.sharedassets.generated.resources.export_error_body
import wingslog.feature.export.sharedassets.generated.resources.export_error_title
import wingslog.feature.export.sharedassets.generated.resources.export_aircraft_details_incomplete
import wingslog.feature.export.sharedassets.generated.resources.export_email_body
import wingslog.feature.export.sharedassets.generated.resources.export_email_subject
import wingslog.feature.export.sharedassets.generated.resources.export_last_12_months
import wingslog.feature.export.sharedassets.generated.resources.export_log_count
import wingslog.feature.export.sharedassets.generated.resources.export_no_aircraft_body
import wingslog.feature.export.sharedassets.generated.resources.export_no_aircraft_title
import wingslog.feature.export.sharedassets.generated.resources.export_primary_action
import wingslog.feature.export.sharedassets.generated.resources.export_progress_building_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_collecting_data
import wingslog.feature.export.sharedassets.generated.resources.export_progress_compressing_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_saving_file
import wingslog.feature.export.sharedassets.generated.resources.export_running_title
import wingslog.feature.export.sharedassets.generated.resources.export_size_kb
import wingslog.feature.export.sharedassets.generated.resources.export_size_mb
import wingslog.feature.export.sharedassets.generated.resources.export_size_zero_kb
import wingslog.feature.export.sharedassets.generated.resources.export_select_aircraft_helper
import wingslog.feature.export.sharedassets.generated.resources.export_select_all
import wingslog.feature.export.sharedassets.generated.resources.export_selection_summary
import wingslog.feature.export.sharedassets.generated.resources.export_share
import wingslog.feature.export.sharedassets.generated.resources.export_share_title
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_file_name
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_location
import wingslog.feature.export.sharedassets.generated.resources.export_success_instructions
import wingslog.feature.export.sharedassets.generated.resources.export_location_downloads_hopply
import wingslog.feature.export.sharedassets.generated.resources.export_location_files_hopply
import wingslog.feature.export.sharedassets.generated.resources.export_success_body
import wingslog.feature.export.sharedassets.generated.resources.export_success_title
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs
import wingslog.feature.export.sharedassets.generated.resources.export_untitled_aircraft

@Composable
fun ExportSelectionScreen(
  state: ExportUiState,
  onNavigateBack: () -> Unit,
  onToggleAircraft: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomStartChange: (LocalDate) -> Unit,
  onCustomEndChange: (LocalDate) -> Unit,
  onExport: () -> Unit,
  onCancel: () -> Unit,
  onShareExport: (String, String, String, String) -> Unit,
  onDone: () -> Unit,
  onRetry: () -> Unit,
) {
  Scaffold(
    topBar = {
      WingsLogTopAppBar(
        title = stringResource(Res.string.feature_name_export_logs),
        onBackClick = when (state) {
          is ExportUiState.Running -> onCancel
          else -> onNavigateBack
        },
      )
    },
    bottomBar = {
      if (state is ExportUiState.Configuring && state.aircraft.isNotEmpty()) {
        ExportBottomBar(state, onExport)
      }
    },
  ) { innerPadding ->
    when (state) {
      is ExportUiState.Configuring -> ConfiguringContent(
        state = state,
        modifier = Modifier.padding(innerPadding),
        onToggleAircraft = onToggleAircraft,
        onSelectAll = onSelectAll,
        onClearAll = onClearAll,
        onDateRangeChange = onDateRangeChange,
        onCustomStartChange = onCustomStartChange,
        onCustomEndChange = onCustomEndChange,
      )
      is ExportUiState.Running -> RunningContent(
        state = state,
        modifier = Modifier.padding(innerPadding),
        onCancel = onCancel,
      )
      is ExportUiState.Success -> SuccessContent(
        state = state,
        modifier = Modifier.padding(innerPadding),
        onShare = onShareExport,
        onDone = onDone,
      )
      is ExportUiState.Error -> ErrorContent(
        state = state,
        modifier = Modifier.padding(innerPadding),
        onRetry = onRetry,
      )
    }
  }
}

@Composable
private fun ConfiguringContent(
  state: ExportUiState.Configuring,
  modifier: Modifier,
  onToggleAircraft: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomStartChange: (LocalDate) -> Unit,
  onCustomEndChange: (LocalDate) -> Unit,
) {
  if (!state.isLoadingAircraft && state.aircraft.isEmpty()) {
    EmptyAircraftContent(modifier)
    return
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = Spacing.screenPadding),
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    item {
      Spacer(Modifier.height(Spacing.small))
      SectionTitle(stringResource(Res.string.export_aircraft_section))
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        FilterChip(
          selected = state.selectedAircraftIds.size == state.aircraft.size,
          onClick = onSelectAll,
          label = { Text(stringResource(Res.string.export_select_all)) },
        )
        FilterChip(
          selected = state.selectedAircraftIds.isEmpty(),
          onClick = onClearAll,
          label = { Text(stringResource(Res.string.export_clear_all)) },
        )
      }
      Text(
        text = stringResource(
          Res.string.export_selection_summary,
          state.selectedAircraftIds.size,
          state.aircraft.size,
          state.estimatedLogCount,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    items(state.aircraft, key = { it.aircraftId }) { aircraft ->
      AircraftRow(
        aircraft = aircraft,
        selected = aircraft.aircraftId in state.selectedAircraftIds,
        onClick = { onToggleAircraft(aircraft.aircraftId) },
      )
    }

    item {
      SectionTitle(stringResource(Res.string.export_date_range_section))
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        DateRangeChip(
          label = stringResource(Res.string.export_all_time),
          selected = state.dateRange == DateRangeOption.AllTime,
          onClick = { onDateRangeChange(DateRangeOption.AllTime) },
        )
        DateRangeChip(
          label = stringResource(Res.string.export_last_12_months),
          selected = state.dateRange == DateRangeOption.Last12Months,
          onClick = { onDateRangeChange(DateRangeOption.Last12Months) },
        )
        DateRangeChip(
          label = stringResource(Res.string.export_custom),
          selected = state.dateRange == DateRangeOption.Custom,
          onClick = { onDateRangeChange(DateRangeOption.Custom) },
        )
      }
      if (state.dateRange == DateRangeOption.Custom) {
        Spacer(Modifier.height(Spacing.small))
        CustomDateRangeFields(
          start = state.customStart,
          end = state.customEnd,
          onStartChange = onCustomStartChange,
          onEndChange = onCustomEndChange,
        )
      }
      Spacer(Modifier.height(Spacing.massive))
    }
  }
}

@Composable
private fun AircraftRow(
  aircraft: AircraftSelectionRow,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = selected,
      onCheckedChange = { onClick() },
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = aircraft.tailNumber.ifBlank {
          stringResource(Res.string.export_untitled_aircraft)
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = aircraft.makeModel.ifBlank {
          stringResource(Res.string.export_aircraft_details_incomplete)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(
      text = stringResource(Res.string.export_log_count, aircraft.logCount),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun CustomDateRangeFields(
  start: LocalDate,
  end: LocalDate,
  onStartChange: (LocalDate) -> Unit,
  onEndChange: (LocalDate) -> Unit,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    DatePickerField(
      label = stringResource(Res.string.export_custom_start_date),
      date = start,
      onDateChange = onStartChange,
    )
    DatePickerField(
      label = stringResource(Res.string.export_custom_end_date),
      date = end,
      onDateChange = onEndChange,
    )
  }
}

@Composable
private fun DatePickerField(
  label: String,
  date: LocalDate,
  onDateChange: (LocalDate) -> Unit,
) {
  var showPicker by remember { mutableStateOf(false) }

  OutlinedButton(
    onClick = { showPicker = true },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.Start,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
      )
      Text(
        text = date.toDisplayFormat(),
        style = MaterialTheme.typography.bodyLarge,
      )
    }
  }

  if (showPicker) {
    val datePickerState = rememberDatePickerState(
      initialSelectedDateMillis = date.toDatePickerMillis(),
    )
    DatePickerDialog(
      onDismissRequest = { showPicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis
              ?.toDatePickerLocalDate()
              ?.let(onDateChange)
            showPicker = false
          },
        ) {
          Text(stringResource(CoreRes.string.done).uppercase())
        }
      },
      dismissButton = {
        TextButton(onClick = { showPicker = false }) {
          Text(stringResource(CoreRes.string.cancel).uppercase())
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}

@Composable
private fun DateRangeChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
  )
}

@Composable
private fun ExportBottomBar(
  state: ExportUiState.Configuring,
  onExport: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(Spacing.screenPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Button(
      onClick = onExport,
      enabled = state.selectedAircraftIds.isNotEmpty(),
      modifier = Modifier
        .fillMaxWidth()
        .height(Spacing.buttonHeight),
      shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    ) {
      Text(
        text = stringResource(
          Res.string.export_primary_action,
          readableBytes(state.estimatedSizeBytes),
        ).uppercase(),
      )
    }
    if (state.selectedAircraftIds.isEmpty()) {
      Text(
        text = stringResource(Res.string.export_select_aircraft_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.small),
      )
    }
  }
}

@Composable
private fun RunningContent(
  state: ExportUiState.Running,
  modifier: Modifier,
  onCancel: () -> Unit,
) {
  StatusContent(
    modifier = modifier,
    icon = Icons.Default.FileDownload,
    title = stringResource(Res.string.export_running_title),
    body = state.step.label(),
  ) {
    LinearProgressIndicator(
      progress = { state.percent / 100f },
      modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = onCancel) {
      Text(stringResource(CoreRes.string.cancel).uppercase())
    }
  }
}

@Composable
private fun ExportProgressStep.label(): String = when (this) {
  ExportProgressStep.COLLECTING_DATA ->
    stringResource(Res.string.export_progress_collecting_data)
  ExportProgressStep.BUILDING_ARCHIVE ->
    stringResource(Res.string.export_progress_building_archive)
  ExportProgressStep.COMPRESSING_ARCHIVE ->
    stringResource(Res.string.export_progress_compressing_archive)
  ExportProgressStep.SAVING_FILE ->
    stringResource(Res.string.export_progress_saving_file)
}

@Composable
private fun SuccessContent(
  state: ExportUiState.Success,
  modifier: Modifier,
  onShare: (String, String, String, String) -> Unit,
  onDone: () -> Unit,
) {
  val fileName = state.fileName.ifBlank {
    stringResource(Res.string.export_stub_preview_file_name)
  }
  val displayLocation = state.displayLocation.ifBlank {
    when (state.displayLocationKind) {
      ExportDisplayLocation.DOWNLOADS_HOPPLY ->
        stringResource(Res.string.export_location_downloads_hopply)
      ExportDisplayLocation.FILES_HOPPLY ->
        stringResource(Res.string.export_location_files_hopply)
      ExportDisplayLocation.UNKNOWN ->
        stringResource(Res.string.export_stub_preview_location)
    }
  }
  StatusContent(
    modifier = modifier,
    icon = Icons.Default.CheckCircle,
    title = stringResource(Res.string.export_success_title),
    body = stringResource(
      Res.string.export_success_body,
      fileName,
      displayLocation,
      stringResource(Res.string.export_success_instructions),
    ),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      val shareTitle = stringResource(Res.string.export_share_title)
      val emailSubject = stringResource(Res.string.export_email_subject, fileName)
      val emailBody = stringResource(Res.string.export_email_body)
      TextButton(onClick = { onShare(state.filePath, shareTitle, emailSubject, emailBody) }) {
        Text(stringResource(Res.string.export_share).uppercase())
      }
      Button(onClick = onDone) {
        Text(stringResource(CoreRes.string.done).uppercase())
      }
    }
  }
}

@Composable
private fun ErrorContent(
  state: ExportUiState.Error,
  modifier: Modifier,
  onRetry: () -> Unit,
) {
  StatusContent(
    modifier = modifier,
    icon = Icons.Default.ErrorOutline,
    title = stringResource(Res.string.export_error_title),
    body = state.message.ifBlank { stringResource(Res.string.export_error_body) },
  ) {
    Button(onClick = onRetry) {
      Text(stringResource(CoreRes.string.retry).uppercase())
    }
  }
}

@Composable
private fun EmptyAircraftContent(modifier: Modifier) {
  StatusContent(
    modifier = modifier,
    icon = Icons.Default.FileDownload,
    title = stringResource(Res.string.export_no_aircraft_title),
    body = stringResource(Res.string.export_no_aircraft_body),
  )
}

@Composable
private fun StatusContent(
  modifier: Modifier,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  body: String,
  actions: @Composable ColumnScope.() -> Unit = {},
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(Spacing.screenPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(
      space = Spacing.large,
      alignment = Alignment.CenterVertically,
    ),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    actions()
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text.uppercase(),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.Bold,
  )
}

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

private fun LocalDate.toDatePickerMillis(): Long =
  LocalDateTime(year, month, day, 12, 0, 0)
    .toInstant(TimeZone.UTC)
    .toEpochMilliseconds()

private fun Long.toDatePickerLocalDate(): LocalDate =
  Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
