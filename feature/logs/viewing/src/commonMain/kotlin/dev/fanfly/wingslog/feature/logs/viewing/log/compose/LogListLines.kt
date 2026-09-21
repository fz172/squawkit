package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.ads.model.ListRow
import dev.fanfly.wingslog.feature.ads.model.withAdSlotsGrouped
import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlinx.datetime.LocalDate

/** One lazy item of the work-log list. [key] is unique in the list and stable. */
sealed interface LogListLine {
  val key: String

  /** Names the month the entries under it fall in. [month] is its first day; null is undated. */
  data class MonthHeader(val month: LocalDate?, val count: Int) : LogListLine {
    override val key: String get() = "month-${month ?: "undated"}"
  }

  /**
   * A log on the spine. [connectsUp] and [connectsDown] say whether the line reaches the entry
   * directly above or below; a header or an ad in between breaks it.
   */
  data class Entry(
    val log: MaintenanceLog,
    val connectsUp: Boolean = false,
    val connectsDown: Boolean = false,
    /** The newest log of all, whose dot is lit. */
    val isLatest: Boolean = false,
  ) : LogListLine {
    override val key: String get() = log.id
  }

  data class Ad(val slotIndex: Int) : LogListLine {
    override val key: String get() = "ad-$slotIndex"
  }
}

/**
 * Flattens [logs] — already newest first — into month headers, entries and ad slots. The order is
 * never changed: a header is emitted wherever the month changes, so a filtered list keeps its
 * months and an undated log heads its own group wherever it falls.
 */
fun logListLines(logs: List<MaintenanceLog>, showAds: Boolean): List<LogListLine> {
  val months = logs.map { it.timestamp?.toLocalDate()?.let { date -> LocalDate(date.year, date.month, 1) } }
  val entries = buildList<LogListLine> {
    logs.forEachIndexed { index, log ->
      val month = months[index]
      if (index == 0 || month != months[index - 1]) {
        val run = months.drop(index).takeWhile { it == month }.size
        add(LogListLine.MonthHeader(month, run))
      }
      add(LogListLine.Entry(log, isLatest = index == 0))
    }
  }
  val lines = if (showAds) {
    withAdSlotsGrouped(entries) { it is LogListLine.MonthHeader }.map { row ->
      when (row) {
        is ListRow.Ad -> LogListLine.Ad(row.slotIndex)
        is ListRow.Item -> row.value
      }
    }
  } else {
    entries
  }
  return lines.mapIndexed { index, line ->
    if (line !is LogListLine.Entry) return@mapIndexed line
    line.copy(
      connectsUp = lines.getOrNull(index - 1) is LogListLine.Entry,
      connectsDown = lines.getOrNull(index + 1) is LogListLine.Entry,
    )
  }
}
