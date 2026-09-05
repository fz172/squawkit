@file:OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalLayoutApi::class,
  ExperimentalComposeUiApi::class,
)

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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.DatePickerDialog
import dev.fanfly.wingslog.core.ui.common.compose.GroupedCheckboxRow
import dev.fanfly.wingslog.core.ui.common.compose.GroupedLeadingIconChip
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.common.compose.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.update.viewmodel.DateRangeOption
import dev.fanfly.wingslog.feature.export.update.viewmodel.ExportUiState
import dev.fanfly.wingslog.feature.export.update.viewmodel.ThingSelectionRow
import dev.fanfly.wingslog.feature.subscription.viewing.ProUpsellSheet
import dev.fanfly.wingslog.feature.subscription.viewing.UpsellTrigger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.done
import wingslog.core.sharedassets.generated.resources.empty_add_thing
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.core.sharedassets.generated.resources.your_stuff
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_all_time
import wingslog.feature.export.sharedassets.generated.resources.export_back_to_setup
import wingslog.feature.export.sharedassets.generated.resources.export_clear_all
import wingslog.feature.export.sharedassets.generated.resources.export_custom
import wingslog.feature.export.sharedassets.generated.resources.export_custom_end_date
import wingslog.feature.export.sharedassets.generated.resources.export_custom_range_title
import wingslog.feature.export.sharedassets.generated.resources.export_custom_start_date
import wingslog.feature.export.sharedassets.generated.resources.export_date_range_section
import wingslog.feature.export.sharedassets.generated.resources.export_download
import wingslog.feature.export.sharedassets.generated.resources.export_email_sent_action
import wingslog.feature.export.sharedassets.generated.resources.export_error_details
import wingslog.feature.export.sharedassets.generated.resources.export_error_subtitle
import wingslog.feature.export.sharedassets.generated.resources.export_error_title
import wingslog.feature.export.sharedassets.generated.resources.export_estimated_size
import wingslog.feature.export.sharedassets.generated.resources.export_footer_thing_count
import wingslog.feature.export.sharedassets.generated.resources.export_format_csv_sub
import wingslog.feature.export.sharedassets.generated.resources.export_format_pdf_sub
import wingslog.feature.export.sharedassets.generated.resources.export_format_pick_one
import wingslog.feature.export.sharedassets.generated.resources.export_format_xlsx_sub
import wingslog.feature.export.sharedassets.generated.resources.export_formats_section
import wingslog.feature.export.sharedassets.generated.resources.export_history_action
import wingslog.feature.export.sharedassets.generated.resources.export_last_12_months
import wingslog.feature.export.sharedassets.generated.resources.export_location_downloads_squawkit
import wingslog.feature.export.sharedassets.generated.resources.export_location_files_squawkit
import wingslog.feature.export.sharedassets.generated.resources.export_no_thing_title
import wingslog.feature.export.sharedassets.generated.resources.export_primary_action
import wingslog.feature.export.sharedassets.generated.resources.export_progress_building_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_collecting_data
import wingslog.feature.export.sharedassets.generated.resources.export_progress_compressing_archive
import wingslog.feature.export.sharedassets.generated.resources.export_progress_saving_file
import wingslog.feature.export.sharedassets.generated.resources.export_progress_uploading_archive
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_attachments
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_attachments_included
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_file_subtitle
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_range
import wingslog.feature.export.sharedassets.generated.resources.export_running_stage_counter
import wingslog.feature.export.sharedassets.generated.resources.export_running_title
import wingslog.feature.export.sharedassets.generated.resources.export_select_all
import wingslog.feature.export.sharedassets.generated.resources.export_send_to_email_action
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_file_name
import wingslog.feature.export.sharedassets.generated.resources.export_stub_preview_location
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_auth
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_failed
import wingslog.feature.export.sharedassets.generated.resources.export_success_delivery_failed_title
import wingslog.feature.export.sharedassets.generated.resources.export_success_title
import wingslog.feature.export.sharedassets.generated.resources.export_thing_summary_more
import wingslog.feature.export.sharedassets.generated.resources.export_view_exports
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs
import kotlin.time.Instant
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
fun ExportSelectionScreen(
  state: ExportUiState,
  onNavigateBack: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onToggleThing: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onToggleFormat: (ExportFormat) -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
  onExport: () -> Unit,
  onCancel: () -> Unit,
  onDownloadExport: (exportId: String, filePath: String, fileName: String) -> Unit,
  onSendToEmail: () -> Unit,
  onDone: () -> Unit,
  onRetry: () -> Unit,
  onSeePlans: () -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  // There's no explicit "Done" action on the success screen anymore — the same cleanup (reset to
  // the last editable configuration) now happens on back navigation, gesture included.
  BackHandler(enabled = state is ExportUiState.Success) { onDone() }

  // The success screen's action bar isn't a real Scaffold bottomBar (it's pinned to the bottom of
  // the content column instead, matching ConfiguringContent's pattern), so Scaffold can't push the
  // snackbar above it automatically — measure it and pad the snackbar host ourselves.
  var successActionsHeight by remember { mutableStateOf(0.dp) }

  Scaffold(
    topBar = {
      ConstrainedTopBar(ContentWidth.Form) {
        WingsLogTopAppBar(
          title = stringResource(Res.string.feature_name_export_logs),
          onBackClick = when (state) {
            is ExportUiState.Running -> onCancel
            is ExportUiState.Success -> onDone
            else -> onNavigateBack
          },
          actions = {
            if (state is ExportUiState.Configuring && state.things.isNotEmpty()) {
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
    snackbarHost = {
      SnackbarHost(
        snackbarHostState,
        modifier = Modifier.padding(
          bottom = if (state is ExportUiState.Success) successActionsHeight else 0.dp
        ),
      )
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
        onToggleThing = onToggleThing,
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
        onDownload = onDownloadExport,
        onSendToEmail = onSendToEmail,
        onHistory = onNavigateToHistory,
        onSeePlans = onSeePlans,
        onActionsHeightChanged = { successActionsHeight = it },
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
  onToggleThing: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onToggleFormat: (ExportFormat) -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
  onNavigateToHistory: () -> Unit,
  onExport: () -> Unit,
) {
  if (!state.isLoadingThings && state.things.isEmpty()) {
    EmptyThingContent(modifier, onNavigateToHistory)
    return
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    ExportSetupList(
      state = state,
      onToggleThing = onToggleThing,
      onSelectAll = onSelectAll,
      onClearAll = onClearAll,
      onToggleFormat = onToggleFormat,
      onDateRangeChange = onDateRangeChange,
      onCustomRangeChange = onCustomRangeChange,
      modifier = Modifier
        .fillMaxHeight()
        .constrainedContentWidth(ContentWidth.Form)
        .padding(horizontal = Spacing.screenPadding),
      bottomPadding = ExportBottomBarReservedHeight,
    )
    if (state.things.isNotEmpty()) {
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
  onToggleThing: (String) -> Unit,
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
      val allSelected = state.selectedThingIds.size == state.things.size
      Section(
        // Neutral: the list spans every template on the account, so no one Thing's word
        // describes it — the same rule as the switcher's own chrome (§6).
        title = stringResource(CoreRes.string.your_stuff),
        action = if (state.things.size > 1) {
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
          rows = state.things.map { thing ->
            {
              ThingOptionRow(
                thing = thing,
                selected = thing.thingId in state.selectedThingIds,
                onClick = { onToggleThing(thing.thingId) },
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
        text = stringResource(Res.string.export_format_pick_one),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.statusColors.caution.accent,
        modifier = Modifier.padding(start = Spacing.extraSmall),
      )
    }
  }
}

// ─── Setup · Things ─────────────────────────────────────────────────────────

@Composable
private fun ThingOptionRow(
  thing: ThingSelectionRow,
  selected: Boolean,
  onClick: () -> Unit,
) {
  GroupedCheckboxRow(
    // Already resolved per row by the ViewModel, which is the only place that knows each Thing's
    // own template. The label chain guarantees a line, so there is no "Untitled" case left.
    title = thing.label,
    subtitle = thing.subtitle,
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
          // Counts a selection that may hold any mix of types, so not an aeroplane.
          imageVector = Icons.Default.Category,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = stringResource(
            Res.string.export_footer_thing_count,
            state.selectedThingIds.size,
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
            state.estimatedSizeBytes.formatFileSize()
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }

      Button(
        onClick = onExport,
        enabled = state.selectedThingIds.isNotEmpty() && state.formats.isNotEmpty(),
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
  // Neutral: one export can cover a car and a house at once, so "Collecting vehicle data" would
  // be wrong for half of it.
  ExportProgressStep.COLLECTING_DATA ->
    stringResource(Res.string.export_progress_collecting_data)

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
  onDownload: (exportId: String, filePath: String, fileName: String) -> Unit,
  onSendToEmail: () -> Unit,
  onHistory: () -> Unit,
  onSeePlans: () -> Unit,
  onActionsHeightChanged: (Dp) -> Unit = {},
) {
  val density = LocalDensity.current
  val fileName =
    state.fileName.ifBlank { stringResource(Res.string.export_stub_preview_file_name) }
  val location = state.displayLocation.ifBlank {
    when (state.displayLocationKind) {
      ExportDisplayLocation.DOWNLOADS_SQUAWKIT -> stringResource(Res.string.export_location_downloads_squawkit)
      ExportDisplayLocation.FILES_SQUAWKIT -> stringResource(Res.string.export_location_files_squawkit)
      ExportDisplayLocation.UNKNOWN -> stringResource(Res.string.export_stub_preview_location)
    }
  }

  var showUpsell by remember { mutableStateOf(false) }

  // The archive is always saved locally first; email is a separate, explicit action the user
  // takes below, never automatic.
  val emailSucceeded = state.persistedDeliveryState == "SENT"
  val deliveryFailed = state.persistedDeliveryState == "FAILED"
  // A failed email delivery folds into the receipt as a labeled status section rather than a
  // separate stacked card, so the success screen stays a single card.
  val deliveryFailure = if (deliveryFailed) {
    val reason = state.deliveryFailureMessage.ifBlank {
      stringResource(Res.string.export_success_delivery_failed)
    }
    val destination = state.deliveryInfo?.destinationEmail.orEmpty()
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
    title = stringResource(Res.string.export_success_title),
    subtitle = location,
    body = {
      ReceiptCard(
        fileName = fileName,
        sizeText = state.sizeBytes.formatFileSize(),
        formats = state.formats,
        thingSummary = thingSummary(state.selectedTailNumbers),
        rangeText = rangeSummary(
          state.dateRange,
          state.customStart,
          state.customEnd
        ),
        deliveryFailure = deliveryFailure,
      )
    },
    actions = {
      Row(
        modifier = Modifier.fillMaxWidth()
          .onGloballyPositioned { coordinates ->
            onActionsHeightChanged(with(density) { coordinates.size.height.toDp() })
          },
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        SuccessBarAction(
          modifier = Modifier.weight(1f),
          icon = Icons.Default.Download,
          label = stringResource(Res.string.export_download),
          onClick = { onDownload(state.exportId, state.filePath, fileName) },
        )
        when {
          state.deliveryInfo != null -> SuccessBarAction(
            modifier = Modifier.weight(1f),
            icon = if (emailSucceeded) Icons.Default.Check else Icons.Default.Mail,
            label = stringResource(
              if (emailSucceeded) Res.string.export_email_sent_action
              else Res.string.export_send_to_email_action
            ),
            enabled = !emailSucceeded && !state.isSendingEmail,
            onClick = onSendToEmail,
          )

          state.emailDeliveryLocked -> SuccessBarAction(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Lock,
            label = stringResource(Res.string.export_send_to_email_action),
            onClick = { showUpsell = true },
          )

          else -> Spacer(Modifier.weight(1f))
        }
        SuccessBarAction(
          modifier = Modifier.weight(1f),
          icon = Icons.Default.History,
          label = stringResource(Res.string.export_view_exports),
          onClick = onHistory,
        )
      }
    },
  )

  if (showUpsell) {
    ProUpsellSheet(
      trigger = UpsellTrigger.EMAIL_EXPORT,
      onSeePlans = {
        onSeePlans()
        showUpsell = false
      },
      onDismiss = { showUpsell = false },
    )
  }
}

/** One compact icon-over-label action in the success screen's bottom action bar. */
@Composable
private fun SuccessBarAction(
  icon: ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val contentColor = if (enabled) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.outline
  }
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(Spacing.chipCornerRadius))
      .let { if (enabled) it.clickable(onClick = onClick) else it }
      .padding(vertical = Spacing.small, horizontal = Spacing.extraSmall),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
      textAlign = TextAlign.Center,
      maxLines = 2,
    )
  }
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
  thingSummary: String,
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
      Icons.Default.Category,
      stringResource(CoreRes.string.your_stuff),
      thingSummary,
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
        label = stringResource(CoreRes.string.retry),
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
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  if (plain) {
    TextButton(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier.fillMaxWidth()
        .height(48.dp),
    ) {
      Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  } else {
    OutlinedButton(
      onClick = onClick,
      enabled = enabled,
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

// ─── Empty thing ─────────────────────────────────────────────────────────

@Composable
private fun EmptyThingContent(
  modifier: Modifier,
  onNavigateToHistory: () -> Unit,
) {
  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.FileDownload,
    heroColor = MaterialTheme.colorScheme.primary,
    heroContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
    title = stringResource(Res.string.export_no_thing_title),
    subtitle = stringResource(
      CoreRes.string.empty_add_thing,
      LexiconFormatter.withArticle(LocalThingLexicon.current.thingNoun),
    ),
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
private fun thingSummary(tailNumbers: List<String>): String =
  when (tailNumbers.size) {
    0 -> "—"
    1 -> tailNumbers[0]
    2 -> tailNumbers.joinToString(", ")
    else -> stringResource(
      Res.string.export_thing_summary_more,
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

private fun LocalDate.toDatePickerMillis(): Long =
  LocalDateTime(year, month, day, 12, 0, 0)
    .toInstant(TimeZone.UTC)
    .toEpochMilliseconds()

private fun Long.toDatePickerLocalDate(): LocalDate =
  Instant.fromEpochMilliseconds(this)
    .toLocalDateTime(TimeZone.UTC).date
