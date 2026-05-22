@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fanfly.wingslog.feature.export.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.core.ui.theme.StatusOkContainer
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryEmailSource
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.done
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_aircraft_details_incomplete
import wingslog.feature.export.sharedassets.generated.resources.export_aircraft_section
import wingslog.feature.export.sharedassets.generated.resources.export_aircraft_summary_more
import wingslog.feature.export.sharedassets.generated.resources.export_all_time
import wingslog.feature.export.sharedassets.generated.resources.export_back_to_setup
import wingslog.feature.export.sharedassets.generated.resources.export_clear_all
import wingslog.feature.export.sharedassets.generated.resources.export_custom
import wingslog.feature.export.sharedassets.generated.resources.export_custom_end_date
import wingslog.feature.export.sharedassets.generated.resources.export_custom_start_date
import wingslog.feature.export.sharedassets.generated.resources.export_date_range_section
import wingslog.feature.export.sharedassets.generated.resources.export_email_body
import wingslog.feature.export.sharedassets.generated.resources.export_email_subject
import wingslog.feature.export.sharedassets.generated.resources.export_delivery_description
import wingslog.feature.export.sharedassets.generated.resources.export_delivery_disabled_body
import wingslog.feature.export.sharedassets.generated.resources.export_delivery_field_label
import wingslog.feature.export.sharedassets.generated.resources.export_delivery_helper
import wingslog.feature.export.sharedassets.generated.resources.export_delivery_resolved_auth
import wingslog.feature.export.sharedassets.generated.resources.export_delivery_resolved_explicit
import wingslog.feature.export.sharedassets.generated.resources.export_delivery_title
import wingslog.feature.export.sharedassets.generated.resources.export_error_details
import wingslog.feature.export.sharedassets.generated.resources.export_error_subtitle
import wingslog.feature.export.sharedassets.generated.resources.export_error_title
import wingslog.feature.export.sharedassets.generated.resources.export_estimated_size
import wingslog.feature.export.sharedassets.generated.resources.export_footer_aircraft_count
import wingslog.feature.export.sharedassets.generated.resources.export_format_csv_sub
import wingslog.feature.export.sharedassets.generated.resources.export_format_pdf_sub
import wingslog.feature.export.sharedassets.generated.resources.export_format_xlsx_sub
import wingslog.feature.export.sharedassets.generated.resources.export_formats_helper
import wingslog.feature.export.sharedassets.generated.resources.export_formats_helper_empty
import wingslog.feature.export.sharedassets.generated.resources.export_formats_section
import wingslog.feature.export.sharedassets.generated.resources.export_history_action
import wingslog.feature.export.sharedassets.generated.resources.export_last_12_months
import wingslog.feature.export.sharedassets.generated.resources.export_location_downloads_hopply
import wingslog.feature.export.sharedassets.generated.resources.export_location_files_hopply
import wingslog.feature.export.sharedassets.generated.resources.export_no_aircraft_body
import wingslog.feature.export.sharedassets.generated.resources.export_no_aircraft_title
import wingslog.feature.export.sharedassets.generated.resources.export_primary_action
import wingslog.feature.export.sharedassets.generated.resources.export_progress_building_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_building_archive_detail
import wingslog.feature.export.sharedassets.generated.resources.export_progress_collecting_data
import wingslog.feature.export.sharedassets.generated.resources.export_progress_collecting_data_detail
import wingslog.feature.export.sharedassets.generated.resources.export_progress_compressing_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_compressing_archive_detail
import wingslog.feature.export.sharedassets.generated.resources.export_progress_requesting_delivery
import wingslog.feature.export.sharedassets.generated.resources.export_progress_requesting_delivery_detail
import wingslog.feature.export.sharedassets.generated.resources.export_progress_saving_file
import wingslog.feature.export.sharedassets.generated.resources.export_progress_saving_file_detail
import wingslog.feature.export.sharedassets.generated.resources.export_progress_uploading_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_uploading_archive_detail
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_aircraft
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_attachments
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_attachments_included
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_file_subtitle
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_range
import wingslog.feature.export.sharedassets.generated.resources.export_running_cancel_notice
import wingslog.feature.export.sharedassets.generated.resources.export_running_progress_percent
import wingslog.feature.export.sharedassets.generated.resources.export_running_steps_title
import wingslog.feature.export.sharedassets.generated.resources.export_running_title
import wingslog.feature.export.sharedassets.generated.resources.export_select_all
import wingslog.feature.export.sharedassets.generated.resources.export_share
import wingslog.feature.export.sharedassets.generated.resources.export_share_title
import wingslog.feature.export.sharedassets.generated.resources.export_size_kb
import wingslog.feature.export.sharedassets.generated.resources.export_size_mb
import wingslog.feature.export.sharedassets.generated.resources.export_size_zero_kb
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_file_name
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_location
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_auth
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_explicit
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_failed
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_failed_title
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_manual
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_manual_title
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_pending
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_pending_title
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_ready_title
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_sent
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_sent_title
import wingslog.feature.export.sharedassets.generated.resources.export_success_title
import wingslog.feature.export.sharedassets.generated.resources.export_try_again
import wingslog.feature.export.sharedassets.generated.resources.export_untitled_aircraft
import wingslog.feature.export.sharedassets.generated.resources.export_view_exports
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs

@Composable
fun ExportSelectionScreen(
  state: ExportUiState,
  onNavigateBack: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onToggleAircraft: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onToggleFormat: (ExportFormat) -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomStartChange: (LocalDate) -> Unit,
  onCustomEndChange: (LocalDate) -> Unit,
  onExportDestinationEmailChanged: (String) -> Unit,
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
        actions = {
          if (state is ExportUiState.Configuring && state.aircraft.isNotEmpty()) {
            IconButton(onClick = onNavigateToHistory) {
              Icon(
                imageVector = Icons.Default.History,
                contentDescription = stringResource(Res.string.export_history_action),
              )
            }
          }
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
        onToggleFormat = onToggleFormat,
        onDateRangeChange = onDateRangeChange,
        onCustomStartChange = onCustomStartChange,
        onCustomEndChange = onCustomEndChange,
        onExportDestinationEmailChanged = onExportDestinationEmailChanged,
        onNavigateToHistory = onNavigateToHistory,
      )
      is ExportUiState.Running -> RunningContent(
        state = state,
        modifier = Modifier.padding(innerPadding),
        onCancel = onCancel,
      )
      is ExportUiState.Success -> SuccessResult(
        state = state,
        modifier = Modifier.padding(innerPadding),
        onShare = onShareExport,
        onHistory = onNavigateToHistory,
        onDone = onDone,
      )
      is ExportUiState.Error -> ErrorResult(
        modifier = Modifier.padding(innerPadding),
        onRetry = onRetry,
        onBack = onNavigateBack,
      )
    }
  }
}

// ─── Setup ────────────────────────────────────────────────────────────────

@Composable
private fun ConfiguringContent(
  state: ExportUiState.Configuring,
  modifier: Modifier,
  onToggleAircraft: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onToggleFormat: (ExportFormat) -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomStartChange: (LocalDate) -> Unit,
  onCustomEndChange: (LocalDate) -> Unit,
  onExportDestinationEmailChanged: (String) -> Unit,
  onNavigateToHistory: () -> Unit,
) {
  if (!state.isLoadingAircraft && state.aircraft.isEmpty()) {
    EmptyAircraftContent(modifier, onNavigateToHistory)
    return
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = Spacing.screenPadding),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    item {
      Spacer(Modifier.height(Spacing.small))
      FormatSection(formats = state.formats, onToggleFormat = onToggleFormat)
    }

    item {
      val allSelected = state.selectedAircraftIds.size == state.aircraft.size
      SectionHeader(
        title = stringResource(Res.string.export_aircraft_section),
        action = if (state.aircraft.size > 1) {
          {
            TextButton(onClick = if (allSelected) onClearAll else onSelectAll) {
              Text(
                stringResource(
                  if (allSelected) Res.string.export_clear_all else Res.string.export_select_all
                )
              )
            }
          }
        } else {
          null
        },
      )
    }

    items(state.aircraft, key = { it.aircraftId }) { aircraft ->
      AircraftCard(
        aircraft = aircraft,
        selected = aircraft.aircraftId in state.selectedAircraftIds,
        onClick = { onToggleAircraft(aircraft.aircraftId) },
      )
    }

    item {
      DateRangeSection(
        state = state,
        onDateRangeChange = onDateRangeChange,
        onCustomStartChange = onCustomStartChange,
        onCustomEndChange = onCustomEndChange,
      )
    }

    item {
      DeliveryConfigSection(
        state = state,
        onEmailChanged = onExportDestinationEmailChanged,
      )
      Spacer(Modifier.height(Spacing.medium))
    }
  }
}

@Composable
private fun DeliveryConfigSection(
  state: ExportUiState.Configuring,
  onEmailChanged: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    SectionHeader(title = stringResource(Res.string.export_delivery_title))
    Text(
      text = if (state.resolvedDeliveryInfo == null && state.exportDestinationEmail.isBlank()) {
        stringResource(Res.string.export_delivery_disabled_body)
      } else {
        stringResource(Res.string.export_delivery_description)
      },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
      value = state.exportDestinationEmail,
      onValueChange = onEmailChanged,
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      label = { Text(stringResource(Res.string.export_delivery_field_label)) },
      supportingText = {
        Text(
          text = when (val resolved = state.resolvedDeliveryInfo) {
            null -> stringResource(Res.string.export_delivery_helper)
            else -> when (resolved.source) {
              ExportDeliveryEmailSource.EXPLICIT ->
                stringResource(Res.string.export_delivery_resolved_explicit, resolved.destinationEmail)
              ExportDeliveryEmailSource.AUTH_FALLBACK ->
                stringResource(Res.string.export_delivery_resolved_auth, resolved.destinationEmail)
            }
          }
        )
      },
    )
  }
}

// ─── Setup · Report formats ─────────────────────────────────────────────────

private data class FormatChoice(
  val format: ExportFormat,
  val icon: ImageVector,
  val sub: StringResource,
)

private val FORMAT_CHOICES = listOf(
  FormatChoice(ExportFormat.PDF, Icons.Default.PictureAsPdf, Res.string.export_format_pdf_sub),
  FormatChoice(ExportFormat.CSV, Icons.Default.Description, Res.string.export_format_csv_sub),
  FormatChoice(ExportFormat.XLSX, Icons.Default.TableView, Res.string.export_format_xlsx_sub),
)

@Composable
private fun FormatSection(
  formats: Set<ExportFormat>,
  onToggleFormat: (ExportFormat) -> Unit,
) {
  Column {
    SectionHeader(title = stringResource(Res.string.export_formats_section))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      FORMAT_CHOICES.forEach { choice ->
        FormatChip(
          modifier = Modifier.weight(1f),
          choice = choice,
          selected = choice.format in formats,
          isLastSelected = choice.format in formats && formats.size == 1,
          onClick = { onToggleFormat(choice.format) },
        )
      }
    }
    Spacer(Modifier.height(Spacing.small))
    Row(
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(
        imageVector = Icons.Default.FolderZip,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp).padding(top = 1.dp),
      )
      Text(
        text = if (formats.isEmpty()) {
          stringResource(Res.string.export_formats_helper_empty)
        } else {
          stringResource(Res.string.export_formats_helper, joinFormats(formats))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun FormatChip(
  modifier: Modifier,
  choice: FormatChoice,
  selected: Boolean,
  isLastSelected: Boolean,
  onClick: () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  Box(
    modifier = modifier
      .heightIn(min = 96.dp)
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
      .border(
        width = 1.5.dp,
        color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .clickable(enabled = !isLastSelected, onClick = onClick)
      .padding(Spacing.medium),
  ) {
    SelectionIndicator(
      selected = selected,
      modifier = Modifier.align(Alignment.TopEnd),
      size = 18.dp,
    )
    Column(
      modifier = Modifier.align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Icon(
        imageVector = choice.icon,
        contentDescription = null,
        tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
      )
      Text(
        text = choice.format.name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = stringResource(choice.sub),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

// ─── Setup · Aircraft ───────────────────────────────────────────────────────

@Composable
private fun AircraftCard(
  aircraft: AircraftSelectionRow,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(
        if (selected) accent.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceContainer
      )
      .border(
        width = Spacing.hairline,
        color = if (selected) accent.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    SelectionIndicator(selected = selected, size = 22.dp, rounded = true)
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = aircraft.tailNumber.ifBlank { stringResource(Res.string.export_untitled_aircraft) },
        style = WingslogTypography.dataLarge,
        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = aircraft.makeModel.ifBlank {
          stringResource(Res.string.export_aircraft_details_incomplete)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Square (or rounded-square) check box used by format chips and aircraft cards. */
@Composable
private fun SelectionIndicator(
  selected: Boolean,
  modifier: Modifier = Modifier,
  size: Dp = 22.dp,
  rounded: Boolean = false,
) {
  val accent = MaterialTheme.colorScheme.primary
  val shape = RoundedCornerShape(if (rounded) 6.dp else 5.dp)
  Box(
    modifier = modifier
      .size(size)
      .clip(shape)
      .background(if (selected) accent else Color.Transparent)
      .then(
        if (selected) Modifier
        else Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape)
      ),
    contentAlignment = Alignment.Center,
  ) {
    if (selected) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(size * 0.7f),
      )
    }
  }
}

// ─── Setup · Date range ─────────────────────────────────────────────────────

@Composable
private fun DateRangeSection(
  state: ExportUiState.Configuring,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomStartChange: (LocalDate) -> Unit,
  onCustomEndChange: (LocalDate) -> Unit,
) {
  Column {
    SectionHeader(title = stringResource(Res.string.export_date_range_section))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      RangePill(
        label = stringResource(Res.string.export_all_time),
        selected = state.dateRange == DateRangeOption.AllTime,
        onClick = { onDateRangeChange(DateRangeOption.AllTime) },
      )
      RangePill(
        label = stringResource(Res.string.export_last_12_months),
        selected = state.dateRange == DateRangeOption.Last12Months,
        onClick = { onDateRangeChange(DateRangeOption.Last12Months) },
      )
      RangePill(
        label = stringResource(Res.string.export_custom),
        selected = state.dateRange == DateRangeOption.Custom,
        onClick = { onDateRangeChange(DateRangeOption.Custom) },
      )
    }
    if (state.dateRange == DateRangeOption.Custom) {
      Spacer(Modifier.height(Spacing.medium))
      CustomRangeCard(
        start = state.customStart,
        end = state.customEnd,
        onStartChange = onCustomStartChange,
        onEndChange = onCustomEndChange,
      )
    }
  }
}

@Composable
private fun RangePill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(Spacing.chipCornerRadius))
      .background(if (selected) accent else Color.Transparent)
      .border(
        width = 1.5.dp,
        color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.chipCornerRadius),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = Spacing.medium, vertical = Spacing.small),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun CustomRangeCard(
  start: LocalDate,
  end: LocalDate,
  onStartChange: (LocalDate) -> Unit,
  onEndChange: (LocalDate) -> Unit,
) {
  // Two clearly tappable fields so neither bound can be left at its default by accident.
  Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
    DateField(
      modifier = Modifier.weight(1f),
      label = stringResource(Res.string.export_custom_start_date),
      date = start,
      onChange = onStartChange,
    )
    DateField(
      modifier = Modifier.weight(1f),
      label = stringResource(Res.string.export_custom_end_date),
      date = end,
      onChange = onEndChange,
    )
  }
}

@Composable
private fun DateField(
  label: String,
  date: LocalDate,
  onChange: (LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showPicker by remember { mutableStateOf(false) }
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        width = Spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .clickable { showPicker = true }
      .padding(horizontal = Spacing.medium, vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Icon(
      imageVector = Icons.Default.Event,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = date.toDisplayFormat(),
        style = WingslogTypography.dataMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
  if (showPicker) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toDatePickerMillis())
    DatePickerDialog(
      onDismissRequest = { showPicker = false },
      confirmButton = {
        TextButton(onClick = {
          pickerState.selectedDateMillis?.toDatePickerLocalDate()?.let(onChange)
          showPicker = false
        }) { Text(stringResource(CoreRes.string.done).uppercase()) }
      },
      dismissButton = {
        TextButton(onClick = { showPicker = false }) {
          Text(stringResource(CoreRes.string.cancel).uppercase())
        }
      },
    ) {
      DatePicker(state = pickerState)
    }
  }
}

// ─── Setup · Footer ─────────────────────────────────────────────────────────

@Composable
private fun ExportBottomBar(
  state: ExportUiState.Configuring,
  onExport: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.background)
      .navigationBarsPadding()
      .padding(horizontal = Spacing.screenPadding)
      .padding(top = Spacing.medium, bottom = Spacing.large),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Icon(
          imageVector = Icons.Default.Flight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = stringResource(Res.string.export_footer_aircraft_count, state.selectedAircraftIds.size),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "·",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = rangeSummary(state),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = stringResource(Res.string.export_estimated_size, readableBytes(state.estimatedSizeBytes)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Button(
      onClick = onExport,
      enabled = state.selectedAircraftIds.isNotEmpty() && state.formats.isNotEmpty(),
      modifier = Modifier
        .fillMaxWidth()
        .height(Spacing.buttonHeight),
      shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    ) {
      Icon(
        imageVector = Icons.Default.FolderZip,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(Spacing.small))
      Text(
        text = stringResource(Res.string.export_primary_action),
        style = MaterialTheme.typography.titleMedium,
      )
    }
  }
}

// ─── Running ──────────────────────────────────────────────────────────────

@Composable
private fun RunningContent(
  state: ExportUiState.Running,
  modifier: Modifier,
  onCancel: () -> Unit,
) {
  val phases = exportRunningPhases()
  val currentIndex = phases.indexOf(state.step).coerceAtLeast(0)
  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.FolderZip,
    heroColor = MaterialTheme.colorScheme.primary,
    heroContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
    title = stringResource(Res.string.export_running_title),
    subtitle = state.step.label(),
    body = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        LinearProgressIndicator(
          progress = { state.percent / 100f },
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = stringResource(Res.string.export_running_progress_percent, state.percent),
          style = WingslogTypography.dataMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = state.step.detail(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = stringResource(Res.string.export_running_cancel_notice),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.cardCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Spacing.medium),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
          Text(
            text = stringResource(Res.string.export_running_steps_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          phases.forEachIndexed { index, step ->
            ProgressStepRow(
              label = step.label(),
              active = index == currentIndex,
              complete = index < currentIndex,
            )
          }
        }
      }
    },
    actions = {
      ResultSecondaryButton(
        label = stringResource(CoreRes.string.cancel),
        icon = null,
        onClick = onCancel,
      )
    },
  )
}

@Composable
private fun ProgressStepRow(
  label: String,
  active: Boolean,
  complete: Boolean,
) {
  val icon = when {
    complete -> Icons.Default.CheckCircle
    active -> Icons.Default.Schedule
    else -> Icons.Default.RadioButtonUnchecked
  }
  val tint = when {
    complete -> StatusOk
    active -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = label,
      style = if (active) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
      color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
    )
  }
}

private fun exportRunningPhases(): List<ExportProgressStep> = listOf(
  ExportProgressStep.COLLECTING_DATA,
  ExportProgressStep.BUILDING_ARCHIVE,
  ExportProgressStep.COMPRESSING_ARCHIVE,
  ExportProgressStep.SAVING_FILE,
  ExportProgressStep.UPLOADING_ARCHIVE,
  ExportProgressStep.REQUESTING_DELIVERY,
)

@Composable
private fun ExportProgressStep.label(): String = when (this) {
  ExportProgressStep.COLLECTING_DATA -> stringResource(Res.string.export_progress_collecting_data)
  ExportProgressStep.BUILDING_ARCHIVE -> stringResource(Res.string.export_progress_building_archive)
  ExportProgressStep.COMPRESSING_ARCHIVE -> stringResource(Res.string.export_progress_compressing_archive)
  ExportProgressStep.SAVING_FILE -> stringResource(Res.string.export_progress_saving_file)
  ExportProgressStep.UPLOADING_ARCHIVE -> stringResource(Res.string.export_progress_uploading_archive)
  ExportProgressStep.REQUESTING_DELIVERY -> stringResource(Res.string.export_progress_requesting_delivery)
}

@Composable
private fun ExportProgressStep.detail(): String = when (this) {
  ExportProgressStep.COLLECTING_DATA -> stringResource(Res.string.export_progress_collecting_data_detail)
  ExportProgressStep.BUILDING_ARCHIVE -> stringResource(Res.string.export_progress_building_archive_detail)
  ExportProgressStep.COMPRESSING_ARCHIVE -> stringResource(Res.string.export_progress_compressing_archive_detail)
  ExportProgressStep.SAVING_FILE -> stringResource(Res.string.export_progress_saving_file_detail)
  ExportProgressStep.UPLOADING_ARCHIVE -> stringResource(Res.string.export_progress_uploading_archive_detail)
  ExportProgressStep.REQUESTING_DELIVERY -> stringResource(Res.string.export_progress_requesting_delivery_detail)
}

// ─── Result · Success ───────────────────────────────────────────────────────

@Composable
private fun SuccessResult(
  state: ExportUiState.Success,
  modifier: Modifier,
  onShare: (String, String, String, String) -> Unit,
  onHistory: () -> Unit,
  onDone: () -> Unit,
) {
  val fileName = state.fileName.ifBlank { stringResource(Res.string.export_stub_preview_file_name) }
  val location = state.displayLocation.ifBlank {
    when (state.displayLocationKind) {
      ExportDisplayLocation.DOWNLOADS_HOPPLY -> stringResource(Res.string.export_location_downloads_hopply)
      ExportDisplayLocation.FILES_HOPPLY -> stringResource(Res.string.export_location_files_hopply)
      ExportDisplayLocation.UNKNOWN -> stringResource(Res.string.export_stub_preview_location)
    }
  }
  val shareTitle = stringResource(Res.string.export_share_title)
  val emailSubject = stringResource(Res.string.export_email_subject, fileName)
  val emailBody = stringResource(Res.string.export_email_body)

  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.CheckCircle,
    heroColor = StatusOk,
    heroContainer = StatusOkContainer,
    title = stringResource(Res.string.export_success_title),
    subtitle = location,
    body = {
      DeliveryStatusCard(state)
      ReceiptCard(
        fileName = fileName,
        sizeText = readableBytes(state.sizeBytes),
        formats = state.formats,
        aircraftSummary = aircraftSummary(state.selectedTailNumbers),
        rangeText = rangeSummary(state.dateRange, state.customStart, state.customEnd),
      )
    },
    actions = {
      ResultPrimaryButton(
        label = stringResource(Res.string.export_share),
        icon = Icons.Default.IosShare,
        onClick = { onShare(state.filePath, shareTitle, emailSubject, emailBody) },
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        ResultSecondaryButton(
          modifier = Modifier.weight(1f),
          label = stringResource(Res.string.export_view_exports),
          icon = Icons.Default.History,
          onClick = onHistory,
        )
        ResultSecondaryButton(
          modifier = Modifier.weight(1f),
          label = stringResource(CoreRes.string.done),
          icon = null,
          plain = true,
          onClick = onDone,
        )
      }
    },
  )
}

@Composable
private fun DeliveryStatusCard(state: ExportUiState.Success) {
  val title = when {
    state.deliveryInfo == null -> stringResource(Res.string.export_success_delivery_manual_title)
    state.deliveryState == "SENT" -> stringResource(Res.string.export_success_delivery_sent_title)
    state.deliveryState == "FAILED" -> stringResource(Res.string.export_success_delivery_failed_title)
    state.deliveryState == "QUEUED" || state.deliveryState == "SENDING" ->
      stringResource(Res.string.export_success_delivery_pending_title)
    else -> stringResource(Res.string.export_success_delivery_ready_title)
  }
  val stateBody = when {
    state.deliveryInfo == null -> stringResource(Res.string.export_success_delivery_manual)
    state.deliveryState == "SENT" -> stringResource(Res.string.export_success_delivery_sent)
    state.deliveryState == "FAILED" ->
      state.deliveryFailureMessage.ifBlank {
        stringResource(Res.string.export_success_delivery_failed)
      }
    state.deliveryState == "QUEUED" || state.deliveryState == "SENDING" ->
      stringResource(Res.string.export_success_delivery_pending)
    else -> stringResource(Res.string.export_success_delivery_pending)
  }
  val destinationBody = when (val delivery = state.deliveryInfo) {
    null -> ""
    else -> when (delivery.source) {
      ExportDeliveryEmailSource.EXPLICIT ->
        stringResource(Res.string.export_success_delivery_explicit, delivery.destinationEmail)
      ExportDeliveryEmailSource.AUTH_FALLBACK ->
        stringResource(Res.string.export_success_delivery_auth, delivery.destinationEmail)
    }
  }
  val body = listOf(stateBody, destinationBody).filter { it.isNotBlank() }.joinToString("\n")

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.secondaryContainer)
      .padding(Spacing.large),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

@Composable
private fun ReceiptCard(
  fileName: String,
  sizeText: String,
  formats: Set<ExportFormat>,
  aircraftSummary: String,
  rangeText: String,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        width = Spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .padding(Spacing.large),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.large),
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
          modifier = Modifier.size(24.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = fileName,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(Res.string.export_receipt_file_subtitle, sizeText, joinFormats(formats)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    ReceiptRow(Icons.Default.Flight, stringResource(Res.string.export_receipt_aircraft), aircraftSummary, mono = true)
    ReceiptRow(Icons.Default.DateRange, stringResource(Res.string.export_receipt_range), rangeText)
    ReceiptRow(
      Icons.Default.Attachment,
      stringResource(Res.string.export_receipt_attachments),
      stringResource(Res.string.export_receipt_attachments_included),
    )
  }
}

@Composable
private fun ReceiptRow(icon: ImageVector, label: String, value: String, mono: Boolean = false) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.weight(1f))
    Text(
      text = value,
      style = if (mono) WingslogTypography.dataMedium else MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.End,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

// ─── Result · Error ─────────────────────────────────────────────────────────

@Composable
private fun ErrorResult(
  modifier: Modifier,
  onRetry: () -> Unit,
  onBack: () -> Unit,
) {
  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.ErrorOutline,
    heroColor = MaterialTheme.colorScheme.error,
    heroContainer = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
    title = stringResource(Res.string.export_error_title),
    subtitle = stringResource(Res.string.export_error_subtitle),
    body = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Spacing.cardCornerRadius))
          .background(MaterialTheme.colorScheme.surfaceContainer)
          .border(
            width = Spacing.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(Spacing.cardCornerRadius),
          )
          .padding(Spacing.large),
      ) {
        Text(
          text = stringResource(Res.string.export_error_details),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    actions = {
      ResultPrimaryButton(
        label = stringResource(Res.string.export_try_again),
        icon = null,
        onClick = onRetry,
      )
      ResultSecondaryButton(
        label = stringResource(Res.string.export_back_to_setup),
        icon = Icons.Default.Tune,
        onClick = onBack,
      )
    },
  )
}

// ─── Result shell + buttons ─────────────────────────────────────────────────

@Composable
private fun ResultShell(
  modifier: Modifier,
  heroIcon: ImageVector,
  heroColor: Color,
  heroContainer: Color,
  title: String,
  subtitle: String,
  body: @Composable () -> Unit,
  actions: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = Spacing.screenPadding)
      .padding(top = Spacing.large, bottom = Spacing.extraLarge),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(top = Spacing.large, bottom = Spacing.extraLarge),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(RoundedCornerShape(percent = 50))
          .background(heroContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = heroIcon,
          contentDescription = null,
          tint = heroColor,
          modifier = Modifier.size(36.dp),
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
    body()
    Spacer(Modifier.weight(1f))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
      actions()
    }
  }
}

@Composable
private fun ResultPrimaryButton(label: String, icon: ImageVector?, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(Spacing.buttonHeight),
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
  ) {
    if (icon != null) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(Spacing.small))
    }
    Text(text = label, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
private fun ResultSecondaryButton(
  label: String,
  icon: ImageVector?,
  modifier: Modifier = Modifier,
  plain: Boolean = false,
  onClick: () -> Unit,
) {
  if (plain) {
    TextButton(
      onClick = onClick,
      modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
      Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  } else {
    OutlinedButton(
      onClick = onClick,
      modifier = modifier.fillMaxWidth().height(48.dp),
      shape = RoundedCornerShape(Spacing.chipCornerRadius),
    ) {
      if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.small))
      }
      Text(text = label)
    }
  }
}

// ─── Empty aircraft ─────────────────────────────────────────────────────────

@Composable
private fun EmptyAircraftContent(
  modifier: Modifier,
  onNavigateToHistory: () -> Unit,
) {
  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.FileDownload,
    heroColor = MaterialTheme.colorScheme.primary,
    heroContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
    title = stringResource(Res.string.export_no_aircraft_title),
    subtitle = stringResource(Res.string.export_no_aircraft_body),
    body = {},
    actions = {
      ResultSecondaryButton(
        label = stringResource(Res.string.export_history_action),
        icon = Icons.Default.History,
        onClick = onNavigateToHistory,
      )
    },
  )
}

// ─── Shared helpers ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    action?.invoke()
  }
}

/** Joins formats in canonical order: "PDF", "PDF + CSV", "PDF, CSV + XLSX". */
private fun joinFormats(formats: Set<ExportFormat>): String {
  val ordered = ExportFormat.entries.filter { it in formats }.map { it.name }
  return when (ordered.size) {
    0 -> "—"
    1 -> ordered[0]
    2 -> "${ordered[0]} + ${ordered[1]}"
    else -> "${ordered.dropLast(1).joinToString(", ")} + ${ordered.last()}"
  }
}

@Composable
private fun aircraftSummary(tailNumbers: List<String>): String = when (tailNumbers.size) {
  0 -> "—"
  1 -> tailNumbers[0]
  2 -> tailNumbers.joinToString(", ")
  else -> stringResource(Res.string.export_aircraft_summary_more, tailNumbers[0], tailNumbers.size - 1)
}

@Composable
private fun rangeSummary(state: ExportUiState.Configuring): String =
  rangeSummary(state.dateRange, state.customStart, state.customEnd)

@Composable
private fun rangeSummary(range: DateRangeOption, start: LocalDate, end: LocalDate): String =
  when (range) {
    DateRangeOption.AllTime -> stringResource(Res.string.export_all_time)
    DateRangeOption.Last12Months -> stringResource(Res.string.export_last_12_months)
    DateRangeOption.Custom -> "${start.toDisplayFormat()} – ${end.toDisplayFormat()}"
  }

@Composable
private fun readableBytes(bytes: Long): String = when {
  bytes <= 0L -> stringResource(Res.string.export_size_zero_kb)
  bytes < 1_000_000L -> stringResource(Res.string.export_size_kb, ((bytes + 999L) / 1_000L).toString())
  else -> stringResource(Res.string.export_size_mb, ((bytes / 100_000L) / 10.0).toString())
}

private fun LocalDate.toDatePickerMillis(): Long =
  LocalDateTime(year, month, day, 12, 0, 0)
    .toInstant(TimeZone.UTC)
    .toEpochMilliseconds()

private fun Long.toDatePickerLocalDate(): LocalDate =
  Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
