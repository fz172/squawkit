package dev.fanfly.wingslog.feature.export.update.selection

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_all_time
import wingslog.feature.export.sharedassets.generated.resources.export_last_12_months
import wingslog.feature.export.sharedassets.generated.resources.export_thing_summary_more

/** Joins formats in canonical order: "PDF", "PDF + CSV", "PDF, CSV + XLSX". */
internal fun joinFormats(formats: Set<ExportFormat>): String {
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
internal fun thingSummary(tailNumbers: List<String>): String =
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
internal fun rangeSummary(state: ExportUiState.Configuring): String =
  rangeSummary(state.dateRange, state.customStart, state.customEnd)

@Composable
internal fun rangeSummary(
  range: DateRangeOption,
  start: LocalDate,
  end: LocalDate
): String =
  when (range) {
    DateRangeOption.AllTime -> stringResource(Res.string.export_all_time)
    DateRangeOption.Last12Months -> stringResource(Res.string.export_last_12_months)
    DateRangeOption.Custom -> "${start.toDisplayFormat()} – ${end.toDisplayFormat()}"
  }
