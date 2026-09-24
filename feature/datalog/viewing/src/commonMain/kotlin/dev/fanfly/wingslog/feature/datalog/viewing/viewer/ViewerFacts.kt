package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.datetime.toClockText
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.datalog.model.chart.isPlottable
import dev.fanfly.wingslog.feature.datalog.viewing.chart.InfoFact
import dev.fanfly.wingslog.feature.datalog.viewing.chart.formatSeriesValue
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogRow
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_airframe_hours
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_date
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_duration
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_engine_hours
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_file
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_identity
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_offset
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_product
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_rate
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_rate_value
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_samples
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_series
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_series_value
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_software
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_start
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_system_id
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_unit

/** The Info tab's lines (PRD R26), in the order the mock lists them. */
@Composable
internal fun viewerFacts(record: DataLog, row: DataLogRow): List<InfoFact> {
  val source = record.source
  val plottable = record.series.count { it.isPlottable }
  return listOfNotNull(
    InfoFact(
      stringResource(Res.string.data_log_fact_date),
      row.startLocal.date.toDisplayFormat(numberOnly = false)
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_start),
      row.startLocal.time.toClockText()
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_offset),
      offsetText(record.utc_offset_minutes)
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_duration),
      formatDuration(row.durationSeconds)
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_samples),
      record.sample_count.toString()
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_rate),
      stringResource(
        Res.string.data_log_fact_rate_value,
        formatSeriesValue(record.sample_rate_hz)
      )
    ),
    InfoFact(
      stringResource(Res.string.data_log_fact_series),
      stringResource(
        Res.string.data_log_fact_series_value,
        plottable,
        record.series.size - plottable
      )
    ),
    InfoFact(stringResource(Res.string.data_log_fact_file), record.file_name),
    source?.product?.takeIf { it.isNotBlank() }
      ?.let { InfoFact(stringResource(Res.string.data_log_fact_product), it) },
    source?.unit?.takeIf { it.isNotBlank() }
      ?.let { InfoFact(stringResource(Res.string.data_log_fact_unit), it) },
    source?.software_version?.takeIf { it.isNotBlank() }
      ?.let { InfoFact(stringResource(Res.string.data_log_fact_software), it) },
    source?.system_id?.takeIf { it.isNotBlank() }
      ?.let {
        InfoFact(
          stringResource(Res.string.data_log_fact_system_id),
          it
        )
      },
    source?.identity?.takeIf { it.isNotBlank() }
      ?.let { InfoFact(stringResource(Res.string.data_log_fact_identity), it) },
    source?.airframe_hours?.takeIf { it.isNotBlank() }
      ?.let {
        InfoFact(
          stringResource(Res.string.data_log_fact_airframe_hours),
          it
        )
      },
    source?.engine_hours?.takeIf { it.isNotBlank() }
      ?.let {
        InfoFact(
          stringResource(Res.string.data_log_fact_engine_hours),
          it
        )
      },
  )
}
