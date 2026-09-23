package dev.fanfly.wingslog.feature.export.update.history

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.export.ExportRecordDateRange
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_all_time
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_body
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_body_cloud_only
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_body_device_and_cloud
import wingslog.feature.export.sharedassets.generated.resources.export_last_12_months
import wingslog.feature.export.sharedassets.generated.resources.export_last_n_months

@Composable
internal fun deleteConfirmBody(record: ExportRecord): String = when {
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

/** Aircraft tail summary ("N1234X" / "N1234X +2"), falling back to the file name for legacy records. */
internal fun thingSummary(record: ExportRecord): String {
  // Records written before the label fix carry a blank tail number for anything that is not an
  // aeroplane. Fall through rather than showing a dash for each one.
  val tails = record.aircraft.map {
    it.tail_number.ifBlank { it.make_model }
      .ifBlank { "—" }
  }
  return when (tails.size) {
    0 -> record.file_name
    1 -> tails[0]
    else -> "${tails[0]} +${tails.size - 1}"
  }
}

/** "{formats} · {range}" scope line, blank for legacy records that carry no metadata. */
@Composable
internal fun scopeLine(record: ExportRecord): String {
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
internal fun formatDate(epochMillis: Long): String =
  Instant.fromEpochMilliseconds(epochMillis)
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date
    .toDisplayFormat()
