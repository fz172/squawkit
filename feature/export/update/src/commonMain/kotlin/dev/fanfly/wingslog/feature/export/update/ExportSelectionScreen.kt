@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.fanfly.wingslog.feature.export.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.GroupedCheckboxRow
import dev.fanfly.wingslog.core.ui.common.compose.GroupedLeadingIconChip
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.done
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_aircraft_details_incomplete
import wingslog.feature.export.sharedassets.generated.resources.export_aircraft_section
import wingslog.feature.export.sharedassets.generated.resources.export_aircraft_summary_more
import wingslog.feature.export.sharedassets.generated.resources.export_all_time
import wingslog.feature.export.sharedassets.generated.resources.export_back_to_setup
import wingslog.feature.export.sharedassets.generated.resources.export_clear_all
import wingslog.feature.export.sharedassets.generated.resources.export_custom
import wingslog.feature.export.sharedassets.generated.resources.export_custom_end_date
import wingslog.feature.export.sharedassets.generated.resources.export_custom_range_title
import wingslog.feature.export.sharedassets.generated.resources.export_custom_start_date
import wingslog.feature.export.sharedassets.generated.resources.export_date_range_section
import wingslog.feature.export.sharedassets.generated.resources.export_email_action
import wingslog.feature.export.sharedassets.generated.resources.export_email_body
import wingslog.feature.export.sharedassets.generated.resources.export_email_subject
import wingslog.feature.export.sharedassets.generated.resources.export_error_details
import wingslog.feature.export.sharedassets.generated.resources.export_error_subtitle
import wingslog.feature.export.sharedassets.generated.resources.export_error_title
import wingslog.feature.export.sharedassets.generated.resources.export_estimated_size
import wingslog.feature.export.sharedassets.generated.resources.export_footer_aircraft_count
import wingslog.feature.export.sharedassets.generated.resources.export_format_csv_sub
import wingslog.feature.export.sharedassets.generated.resources.export_format_pdf_sub
import wingslog.feature.export.sharedassets.generated.resources.export_format_xlsx_sub
import wingslog.feature.export.sharedassets.generated.resources.export_formats_helper_empty
import wingslog.feature.export.sharedassets.generated.resources.export_formats_section
import wingslog.feature.export.sharedassets.generated.resources.export_history_action
import wingslog.feature.export.sharedassets.generated.resources.export_last_12_months
import wingslog.feature.export.sharedassets.generated.resources.export_location_downloads_squawkit
import wingslog.feature.export.sharedassets.generated.resources.export_location_files_squawkit
import wingslog.feature.export.sharedassets.generated.resources.export_no_aircraft_body
import wingslog.feature.export.sharedassets.generated.resources.export_no_aircraft_title
import wingslog.feature.export.sharedassets.generated.resources.export_primary_action
import wingslog.feature.export.sharedassets.generated.resources.export_progress_building_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_collecting_data
import wingslog.feature.export.sharedassets.generated.resources.export_progress_compressing_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_saving_file
import wingslog.feature.export.sharedassets.generated.resources.export_progress_uploading_archive
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_aircraft
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_attachments
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_attachments_included
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_file_subtitle
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_range
import wingslog.feature.export.sharedassets.generated.resources.export_running_stage_counter
import wingslog.feature.export.sharedassets.generated.resources.export_running_title
import wingslog.feature.export.sharedassets.generated.resources.export_select_all
import wingslog.feature.export.sharedassets.generated.resources.export_sent_to
import wingslog.feature.export.sharedassets.generated.resources.export_share
import wingslog.feature.export.sharedassets.generated.resources.export_share_title
import wingslog.feature.export.sharedassets.generated.resources.export_size_kb
import wingslog.feature.export.sharedassets.generated.resources.export_size_mb
import wingslog.feature.export.sharedassets.generated.resources.export_size_zero_kb
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_file_name
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_location
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_auth
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_failed
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_failed_title
import wingslog.feature.export.sharedassets.generated.resources.export_success_emailed_subtitle
import wingslog.feature.export.sharedassets.generated.resources.export_success_sent_title
import wingslog.feature.export.sharedassets.generated.resources.export_success_title
import wingslog.feature.export.sharedassets.generated.resources.export_try_again
import wingslog.feature.export.sharedassets.generated.resources.export_untitled_aircraft
import wingslog.feature.export.sharedassets.generated.resources.export_view_exports
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs
import kotlin.time.Instant
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

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
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
  onExport: () -> Unit,
  onCancel: () -> Unit,
  onShareExport: (String, String, String, String) -> Unit,
  onDone: () -> Unit,
  onRetry: () -> Unit,
) {
  Scaffold(
    topBar = {
      ConstrainedTopBar(ContentWidth.Form) {
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
      }
    },
  ) { innerPadding ->
    val layoutDirection = LocalLayoutDirection.current
    when (state) {
      is ExportUiState.Configuring -> ConfiguringContent(
        state = state,
        // The pinned bottom bar runs edge-to-edge and adds its own navigation-bar inset, so the
        // content keeps only the top/horizontal scaffold insets — applying the bottom one here too
        // would double-pad the bar above the nav bar.
        modifier = Modifier.padding(
          top = innerPadding.calculateTopPadding(),
          start = innerPadding.calculateStartPadding(layoutDirection),
          end = innerPadding.calculateEndPadding(layoutDirection),
        ),
        onToggleAircraft = onToggleAircraft,
        onSelectAll = onSelectAll,
        onClearAll = onClearAll,
        onToggleFormat = onToggleFormat,
        onDateRangeChange = onDateRangeChange,
        onCustomRangeChange = onCustomRangeChange,
        onNavigateToHistory = onNavigateToHistory,
        onExport = onExport,
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
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
  onNavigateToHistory: () -> Unit,
  onExport: () -> Unit,
) {
  if (!state.isLoadingAircraft && state.aircraft.isEmpty()) {
    EmptyAircraftContent(modifier, onNavigateToHistory)
    return
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    val bottomBarReservedHeight =
      if (state.resolvedDeliveryInfo?.destinationEmail != null) {
        ExportBottomBarWithEmailReservedHeight
      } else {
        ExportBottomBarReservedHeight
      }
    ExportSetupList(
      state = state,
      onToggleAircraft = onToggleAircraft,
      onSelectAll = onSelectAll,
      onClearAll = onClearAll,
      onToggleFormat = onToggleFormat,
      onDateRangeChange = onDateRangeChange,
      onCustomRangeChange = onCustomRangeChange,
      modifier = Modifier
        .fillMaxHeight()
        .constrainedContentWidth(ContentWidth.Form)
        .padding(horizontal = Spacing.screenPadding),
      bottomPadding = bottomBarReservedHeight,
    )
    if (state.aircraft.isNotEmpty()) {
      Box(
        modifier = Modifier.align(Alignment.BottomCenter),
      ) {
        ExportBottomBar(state, onExport)
      }
    }
  }
}

@Composable
private fun ExportSetupList(
  state: ExportUiState.Configuring,
  onToggleAircraft: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onToggleFormat: (ExportFormat) -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
  modifier: Modifier = Modifier,
  bottomPadding: Dp = Spacing.screenPadding,
) {
  LazyColumn(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    item {
      Spacer(Modifier.height(Spacing.small))
      FormatSection(formats = state.formats, onToggleFormat = onToggleFormat)
    }

    item {
      val allSelected = state.selectedAircraftIds.size == state.aircraft.size
      Section(
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
      ) {
        GroupedRowGroup(
          rows = state.aircraft.map { aircraft ->
            {
              AircraftOptionRow(
                aircraft = aircraft,
                selected = aircraft.aircraftId in state.selectedAircraftIds,
                onClick = { onToggleAircraft(aircraft.aircraftId) },
              )
            }
          }
        )
      }
    }

    item {
      DateRangeSection(
        state = state,
        onDateRangeChange = onDateRangeChange,
        onCustomRangeChange = onCustomRangeChange,
      )
    }

    item {
      Spacer(Modifier.height(bottomPadding))
    }
  }
}

// ─── Setup · Report formats ─────────────────────────────────────────────────

private data class FormatChoice(
  val format: ExportFormat,
  val icon: ImageVector,
  val sub: StringResource,
)

private val FORMAT_CHOICES = listOf(
  FormatChoice(
    ExportFormat.PDF,
    Icons.Default.PictureAsPdf,
    Res.string.export_format_pdf_sub
  ),
  FormatChoice(
    ExportFormat.CSV,
    Icons.Default.Description,
    Res.string.export_format_csv_sub
  ),
  FormatChoice(
    ExportFormat.XLSX,
    Icons.Default.TableView,
    Res.string.export_format_xlsx_sub
  ),
)

private val ExportBottomBarReservedHeight = 176.dp
private val ExportBottomBarWithEmailReservedHeight = 200.dp

@Composable
private fun FormatSection(
  formats: Set<ExportFormat>,
  onToggleFormat: (ExportFormat) -> Unit,
) {
  Section(title = stringResource(Res.string.export_formats_section)) {
    GroupedRowGroup(
      rows = FORMAT_CHOICES.map { choice ->
        {
          val selected = choice.format in formats
          val isLastSelected = selected && formats.size == 1
          GroupedCheckboxRow(
            title = choice.format.name,
            subtitle = stringResource(choice.sub),
            checked = selected,
            onCheckedChange = {
              if (!isLastSelected) {
                onToggleFormat(choice.format)
              }
            },
            leading = {
              GroupedLeadingIconChip(
                icon = choice.icon,
                contentDescription = choice.format.name,
              )
            },
          )
        }
      }
    )
    // The picker enforces at least one format; the advisory only surfaces in the edge case.
    if (formats.isEmpty()) {
      Spacer(Modifier.height(Spacing.small))
      Text(
        text = stringResource(Res.string.export_formats_helper_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.statusColors.caution.accent,
        modifier = Modifier.padding(start = Spacing.extraSmall),
      )
    }
  }
}

// ─── Setup · Aircraft ───────────────────────────────────────────────────────

@Composable
private fun AircraftOptionRow(
  aircraft: AircraftSelectionRow,
  selected: Boolean,
  onClick: () -> Unit,
) {
  GroupedCheckboxRow(
    title = aircraft.tailNumber.ifBlank { stringResource(Res.string.export_untitled_aircraft) },
    subtitle = aircraft.makeModel.ifBlank {
      stringResource(Res.string.export_aircraft_details_incomplete)
    },
    titleStyle = WingslogTypography.dataLarge,
    checked = selected,
    onCheckedChange = { onClick() },
  )
}

// ─── Setup · Date range ─────────────────────────────────────────────────────

@Composable
private fun DateRangeSection(
  state: ExportUiState.Configuring,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
) {
  Section(title = stringResource(Res.string.export_date_range_section)) {
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
      CombinedRangeField(
        start = state.customStart,
        end = state.customEnd,
        onChange = onCustomRangeChange,
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

/**
 * Single combined range readout ("MM/DD/YYYY → MM/DD/YYYY") that opens a range picker, rather than
 * two stranded date fields. Both bounds are always set, so neither can be left at a stale default.
 */
@Composable
private fun CombinedRangeField(
  start: LocalDate,
  end: LocalDate,
  onChange: (LocalDate, LocalDate) -> Unit,
) {
  var showPicker by remember { mutableStateOf(false) }
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
      .clickable { showPicker = true }
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      imageVector = Icons.Default.Event,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(Res.string.export_receipt_range).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "${start.toDisplayFormat()}  →  ${end.toDisplayFormat()}",
        style = WingslogTypography.dataMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
    Icon(
      imageVector = Icons.Default.EditCalendar,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp),
    )
  }
  if (showPicker) {
    val pickerState = rememberDateRangePickerState(
      initialSelectedStartDateMillis = start.toDatePickerMillis(),
      initialSelectedEndDateMillis = end.toDatePickerMillis(),
    )
    DatePickerDialog(
      onDismissRequest = { showPicker = false },
      confirmButton = {
        TextButton(onClick = {
          val newStart =
            pickerState.selectedStartDateMillis?.toDatePickerLocalDate()
          val newEnd =
            pickerState.selectedEndDateMillis?.toDatePickerLocalDate()
          if (newStart != null && newEnd != null) onChange(newStart, newEnd)
          showPicker = false
        }) { Text(stringResource(CoreRes.string.done).uppercase()) }
      },
      dismissButton = {
        TextButton(onClick = { showPicker = false }) {
          Text(stringResource(CoreRes.string.cancel).uppercase())
        }
      },
    ) {
      DateRangePicker(
        state = pickerState,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 620.dp)
          .weight(1f, fill = false),
        title = {
          Text(
            text = stringResource(Res.string.export_custom_range_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
              start = Spacing.extraLarge,
              end = Spacing.extraLarge,
              top = Spacing.large,
            ),
          )
        },
        headline = {
          DateRangePickerHeadline(
            start = pickerState.selectedStartDateMillis?.toDatePickerLocalDate()
              ?: start,
            end = pickerState.selectedEndDateMillis?.toDatePickerLocalDate()
              ?: end,
          )
        },
        showModeToggle = true,
      )
    }
  }
}

@Composable
private fun DateRangePickerHeadline(
  start: LocalDate,
  end: LocalDate,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.extraLarge)
      .padding(top = Spacing.medium, bottom = Spacing.large),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    DateRangePickerHeadlineCell(
      label = stringResource(Res.string.export_custom_start_date),
      value = start.toDisplayFormat(),
      modifier = Modifier.weight(1f),
    )
    DateRangePickerHeadlineCell(
      label = stringResource(Res.string.export_custom_end_date),
      value = end.toDisplayFormat(),
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun DateRangePickerHeadlineCell(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        width = Spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .padding(horizontal = Spacing.medium, vertical = Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = value,
      style = WingslogTypography.dataMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Clip,
    )
  }
}

// ─── Setup · Footer ─────────────────────────────────────────────────────────

@Composable
private fun ExportBottomBar(
  state: ExportUiState.Configuring,
  onExport: () -> Unit,
) {
  val deliveryEmail = state.resolvedDeliveryInfo?.destinationEmail
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.background)
      .navigationBarsPadding(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .constrainedContentWidth(ContentWidth.Form)
        .padding(horizontal = Spacing.screenPadding)
        .padding(top = Spacing.medium, bottom = Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Icon(
          imageVector = Icons.Default.Flight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = stringResource(
            Res.string.export_footer_aircraft_count,
            state.selectedAircraftIds.size
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
        )
        MetaDot()
        Text(
          text = rangeSummary(state),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        MetaDot()
        Text(
          text = stringResource(
            Res.string.export_estimated_size,
            readableBytes(state.estimatedSizeBytes)
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
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
          imageVector = if (deliveryEmail != null) Icons.Default.Mail else Icons.Default.FolderZip,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.small))
        Text(
          text = stringResource(
            if (deliveryEmail != null) Res.string.export_email_action
            else Res.string.export_primary_action
          ),
          style = MaterialTheme.typography.titleMedium,
        )
      }

      if (deliveryEmail != null) {
        Text(
          text = stringResource(Res.string.export_sent_to, deliveryEmail),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun MetaDot() {
  Text(
    text = "·",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

// ─── Running ──────────────────────────────────────────────────────────────

@Composable
private fun RunningContent(
  state: ExportUiState.Running,
  modifier: Modifier,
  onCancel: () -> Unit,
) {
  val phases = exportRunningPhases()
  val currentIndex = phases.indexOf(state.step)
    .coerceAtLeast(0)
  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.FolderZip,
    heroColor = MaterialTheme.colorScheme.primary,
    heroContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
    title = stringResource(Res.string.export_running_title),
    subtitle = state.step.label(),
    body = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
          LinearProgressIndicator(
            progress = { state.percent / 100f },
            modifier = Modifier.fillMaxWidth(),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "${state.percent}%",
              style = WingslogTypography.dataMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(
                Res.string.export_running_stage_counter,
                currentIndex + 1,
                phases.size,
              ),
              style = WingslogTypography.dataMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
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
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.size(18.dp),
      contentAlignment = Alignment.Center,
    ) {
      when {
        complete -> Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.statusColors.positive.accent,
          modifier = Modifier.size(18.dp),
        )

        active -> CircularProgressIndicator(
          modifier = Modifier.size(14.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.primary,
        )

        else -> Icon(
          imageVector = Icons.Default.RadioButtonUnchecked,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.size(14.dp),
        )
      }
    }
    Text(
      text = label,
      style = if (active) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
      color = when {
        active -> MaterialTheme.colorScheme.onSurface
        complete -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
      },
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
)

@Composable
private fun ExportProgressStep.label(): String = when (this) {
  ExportProgressStep.COLLECTING_DATA -> stringResource(Res.string.export_progress_collecting_data)
  ExportProgressStep.BUILDING_ARCHIVE -> stringResource(Res.string.export_progress_building_archive)
  ExportProgressStep.COMPRESSING_ARCHIVE -> stringResource(Res.string.export_progress_compressing_archive)
  ExportProgressStep.SAVING_FILE -> stringResource(Res.string.export_progress_saving_file)
  ExportProgressStep.UPLOADING_ARCHIVE -> stringResource(Res.string.export_progress_uploading_archive)
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
  val fileName =
    state.fileName.ifBlank { stringResource(Res.string.export_stub_preview_file_name) }
  val location = state.displayLocation.ifBlank {
    when (state.displayLocationKind) {
      ExportDisplayLocation.DOWNLOADS_SQUAWKIT -> stringResource(Res.string.export_location_downloads_squawkit)
      ExportDisplayLocation.FILES_SQUAWKIT -> stringResource(Res.string.export_location_files_squawkit)
      ExportDisplayLocation.UNKNOWN -> stringResource(Res.string.export_stub_preview_location)
    }
  }
  val shareTitle = stringResource(Res.string.export_share_title)
  val emailSubject = stringResource(Res.string.export_email_subject, fileName)
  val emailBody = stringResource(Res.string.export_email_body)

  // Email users have their archive delivered by email; the device subtitle and manual-share button
  // apply only to the no-email path.
  val deliveredByEmail = state.deliveryInfo != null
  // Delivery is asynchronous, so right after export the state is usually QUEUED/SENDING rather than
  // SENT. Treat anything that isn't an outright failure as the clean "Export sent" success; only a
  // genuine failure falls back to the status card.
  val deliveryFailed =
    deliveredByEmail && state.persistedDeliveryState == "FAILED"
  val emailSucceeded = deliveredByEmail && !deliveryFailed
  // A failed email delivery folds into the receipt as a labeled status section rather than a
  // separate stacked card, so the success screen stays a single card.
  val deliveryFailure = if (deliveryFailed) {
    val reason = state.deliveryFailureMessage.ifBlank {
      stringResource(Res.string.export_success_delivery_failed)
    }
    val destination = state.deliveryInfo.destinationEmail
    DeliveryFailure(
      title = stringResource(Res.string.export_success_delivery_failed_title),
      message = if (destination.isNotBlank()) {
        reason + "\n" + stringResource(
          Res.string.export_success_delivery_auth,
          destination
        )
      } else {
        reason
      },
    )
  } else {
    null
  }

  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.Check,
    heroColor = MaterialTheme.statusColors.positive.accent,
    heroContainer = MaterialTheme.statusColors.positive.container,
    title = stringResource(
      if (emailSucceeded) Res.string.export_success_sent_title else Res.string.export_success_title
    ),
    subtitle = if (deliveredByEmail) "" else location,
    subtitleContent = if (emailSucceeded) {
      { EmailedSubtitle(state.deliveryInfo.destinationEmail) }
    } else {
      null
    },
    body = {
      ReceiptCard(
        fileName = fileName,
        sizeText = readableBytes(state.sizeBytes),
        formats = state.formats,
        aircraftSummary = aircraftSummary(state.selectedTailNumbers),
        rangeText = rangeSummary(
          state.dateRange,
          state.customStart,
          state.customEnd
        ),
        deliveryFailure = deliveryFailure,
      )
    },
    actions = {
      if (deliveredByEmail) {
        ResultPrimaryButton(
          label = stringResource(CoreRes.string.done),
          icon = null,
          onClick = onDone,
        )
        ResultSecondaryButton(
          label = stringResource(Res.string.export_view_exports),
          icon = Icons.Default.History,
          onClick = onHistory,
        )
      } else {
        ResultPrimaryButton(
          label = stringResource(Res.string.export_share),
          icon = Icons.Default.IosShare,
          onClick = {
            onShare(
              state.filePath,
              shareTitle,
              emailSubject,
              emailBody
            )
          },
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
      }
    },
  )
}

/** Emailed-success subtitle with the destination address rendered in mono, per the design. */
@Composable
private fun EmailedSubtitle(email: String) {
  Text(
    text = buildAnnotatedString {
      append(stringResource(Res.string.export_success_emailed_subtitle))
      if (email.isNotBlank()) {
        append(" ")
        withStyle(
          WingslogTypography.dataMedium.toSpanStyle()
            .copy(color = MaterialTheme.colorScheme.onSurface)
        ) { append(email) }
      }
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
  )
}

/** Failed-delivery summary folded into the receipt card, so the success screen stays one card. */
private data class DeliveryFailure(val title: String, val message: String)

@Composable
private fun DeliveryFailureSection(failure: DeliveryFailure) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      imageVector = Icons.Default.ErrorOutline,
      contentDescription = null,
      tint = MaterialTheme.statusColors.critical.accent,
      modifier = Modifier.size(20.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
      Text(
        text = failure.title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.statusColors.critical.accent,
      )
      Text(
        text = failure.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ReceiptCard(
  fileName: String,
  sizeText: String,
  formats: Set<ExportFormat>,
  aircraftSummary: String,
  rangeText: String,
  deliveryFailure: DeliveryFailure? = null,
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
    if (deliveryFailure != null) {
      DeliveryFailureSection(deliveryFailure)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
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
          text = stringResource(
            Res.string.export_receipt_file_subtitle,
            sizeText,
            joinFormats(formats)
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    ReceiptRow(
      Icons.Default.Flight,
      stringResource(Res.string.export_receipt_aircraft),
      aircraftSummary,
      mono = true
    )
    ReceiptRow(
      Icons.Default.DateRange,
      stringResource(Res.string.export_receipt_range),
      rangeText
    )
    ReceiptRow(
      Icons.Default.Attachment,
      stringResource(Res.string.export_receipt_attachments),
      stringResource(Res.string.export_receipt_attachments_included),
    )
  }
}

@Composable
private fun ReceiptRow(
  icon: ImageVector,
  label: String,
  value: String,
  mono: Boolean = false
) {
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
    heroColor = MaterialTheme.statusColors.critical.accent,
    heroContainer = MaterialTheme.statusColors.critical.container,
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
  subtitleContent: (@Composable () -> Unit)? = null,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .constrainedContentWidth(ContentWidth.Form)
        .padding(horizontal = Spacing.screenPadding)
        .padding(top = Spacing.large, bottom = Spacing.extraLarge),
    ) {
      Column(
        // Tight icon/title/subtitle cluster, then a generous gap before the content below.
        modifier = Modifier.fillMaxWidth()
          .padding(top = Spacing.large, bottom = Spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Box(
          modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(heroContainer)
            .border(
              width = 1.5.dp,
              color = heroColor.copy(alpha = 0.35f),
              shape = RoundedCornerShape(percent = 50),
            ),
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
        if (subtitleContent != null) {
          subtitleContent()
        } else if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
      body()
      Spacer(Modifier.weight(1f))
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        actions()
      }
    }
  }
}

@Composable
private fun ResultPrimaryButton(
  label: String,
  icon: ImageVector?,
  onClick: () -> Unit
) {
  Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
      .height(Spacing.buttonHeight),
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp)
      )
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
      modifier = modifier.fillMaxWidth()
        .height(48.dp),
    ) {
      Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  } else {
    OutlinedButton(
      onClick = onClick,
      modifier = modifier.fillMaxWidth()
        .height(48.dp),
      shape = RoundedCornerShape(Spacing.chipCornerRadius),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
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

/** A section header with a consistent gap above its content, regardless of where it sits. */
@Composable
private fun Section(
  title: String,
  action: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column {
    SectionHeader(title = title, action = action)
    Spacer(Modifier.height(Spacing.medium))
    content()
  }
}

@Composable
private fun SectionHeader(
  title: String,
  action: (@Composable () -> Unit)? = null
) {
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
  val ordered = ExportFormat.entries.filter { it in formats }
    .map { it.name }
  return when (ordered.size) {
    0 -> "—"
    1 -> ordered[0]
    2 -> "${ordered[0]} + ${ordered[1]}"
    else -> "${
      ordered.dropLast(1)
        .joinToString(", ")
    } + ${ordered.last()}"
  }
}

@Composable
private fun aircraftSummary(tailNumbers: List<String>): String =
  when (tailNumbers.size) {
    0 -> "—"
    1 -> tailNumbers[0]
    2 -> tailNumbers.joinToString(", ")
    else -> stringResource(
      Res.string.export_aircraft_summary_more,
      tailNumbers[0],
      tailNumbers.size - 1
    )
  }

@Composable
private fun rangeSummary(state: ExportUiState.Configuring): String =
  rangeSummary(state.dateRange, state.customStart, state.customEnd)

@Composable
private fun rangeSummary(
  range: DateRangeOption,
  start: LocalDate,
  end: LocalDate
): String =
  when (range) {
    DateRangeOption.AllTime -> stringResource(Res.string.export_all_time)
    DateRangeOption.Last12Months -> stringResource(Res.string.export_last_12_months)
    DateRangeOption.Custom -> "${start.toDisplayFormat()} – ${end.toDisplayFormat()}"
  }

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

private fun LocalDate.toDatePickerMillis(): Long =
  LocalDateTime(year, month, day, 12, 0, 0)
    .toInstant(TimeZone.UTC)
    .toEpochMilliseconds()

private fun Long.toDatePickerLocalDate(): LocalDate =
  Instant.fromEpochMilliseconds(this)
    .toLocalDateTime(TimeZone.UTC).date
