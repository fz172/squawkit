package dev.fanfly.wingslog.feature.logs.viewing.list

import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.ads.model.ListRow
import dev.fanfly.wingslog.feature.ads.model.withAdSlotsGrouped
import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlinx.datetime.LocalDate

/**
 * Flattens [logs] — already newest first — into month headers, entries, gaps and ad slots. The
 * order is never changed: a header is emitted wherever the month changes, so a filtered list keeps
 * its months and an undated log heads its own group wherever it falls.
 *
 * [allLogs] is the same list before any filter, in the same order. Where it holds logs between two
 * neighbours of [logs], a [LogListLine.Gap] says how many. Unfiltered, the two are equal and there
 * are no gaps; nothing is reported before the first match or after the last.
 */
fun logListLines(
  logs: List<MaintenanceLog>,
  showAds: Boolean,
  allLogs: List<MaintenanceLog> = logs,
): List<LogListLine> {
  val months = logs.map {
    it.timestamp?.toLocalDate()
      ?.let { date -> LocalDate(date.year, date.month, 1) }
  }
  val positions = allLogs.map { it.id }
  val entries = buildList<LogListLine> {
    logs.forEachIndexed { index, log ->
      val month = months[index]
      if (index == 0 || month != months[index - 1]) {
        val run = months.drop(index)
          .takeWhile { it == month }.size
        add(LogListLine.MonthHeader(month, run, firstLogId = log.id))
      }
      // Newest of all the logs, not of the matches: a lit dot on an old entry would date it wrongly.
      add(LogListLine.Entry(log, isLatest = log.id == positions.firstOrNull()))
      // The gap belongs to the entry above it, ahead of any header: the dashes run on from that
      // entry's dot, and the header below starts the next group clean.
      val next = logs.getOrNull(index + 1) ?: return@forEachIndexed
      val omitted = positions.indexOf(next.id) - positions.indexOf(log.id) - 1
      if (omitted > 0) add(LogListLine.Gap(omitted, afterLogId = log.id))
    }
  }
  val lines = if (showAds) {
    withAdSlotsGrouped(entries) { it !is LogListLine.Entry }.map { row ->
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
      connectsUp = lines.getOrNull(index - 1).isOnSpine,
      connectsDown = lines.getOrNull(index + 1).isOnSpine,
    )
  }
}

private val LogListLine?.isOnSpine: Boolean
  get() = this is LogListLine.Entry || this is LogListLine.Gap
