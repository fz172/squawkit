package dev.fanfly.wingslog.feature.datalog.model

import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSource
import kotlin.time.Instant

/**
 * What a parser produces from raw bytes: the record-shaped metadata plus the columns.
 *
 * The importer copies the metadata into a `DataLog` record and adds what only it knows (id, blob,
 * hashes, derived flags). A viewer rebuilds the same value from the stored bytes, which is why the
 * parser version travels with it.
 */
data class ParsedDataLog(
  val format: DataLogFormat,
  val parserVersion: Int,
  val source: DataLogSource,
  val start: Instant,
  /**
   * [start] was inferred rather than read. A SkyView session that never got a GPS fix carries a
   * time of day and no date, so the date comes from the file name.
   */
  val startApproximate: Boolean = false,
  val utcOffsetMinutes: Int,
  val durationSeconds: Int,
  val sampleCount: Int,
  val sampleRateHz: Float,
  /** The catalogue, in source column order; entirely empty columns are already dropped. */
  val series: List<DataLogSeries>,
  val data: DataLogSeriesData,
)
