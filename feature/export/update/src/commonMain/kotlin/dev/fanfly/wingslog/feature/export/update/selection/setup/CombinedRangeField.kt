package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.popup.DatePickerDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.done
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_custom_range_title
import wingslog.feature.export.sharedassets.generated.resources.export_receipt_range
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * Single combined range readout ("MM/DD/YYYY → MM/DD/YYYY") that opens a range picker, rather than
 * two stranded date fields. Both bounds are always set, so neither can be left at a stale default.
 */
@Composable
internal fun CombinedRangeField(
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
        text = stringResource(Res.string.export_receipt_range),
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

private fun LocalDate.toDatePickerMillis(): Long =
  LocalDateTime(year, month, day, 12, 0, 0)
    .toInstant(TimeZone.UTC)
    .toEpochMilliseconds()

private fun Long.toDatePickerLocalDate(): LocalDate =
  Instant.fromEpochMilliseconds(this)
    .toLocalDateTime(TimeZone.UTC).date
