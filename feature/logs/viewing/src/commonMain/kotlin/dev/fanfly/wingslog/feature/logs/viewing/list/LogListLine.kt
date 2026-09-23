package dev.fanfly.wingslog.feature.logs.viewing.list

import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlinx.datetime.LocalDate

/** One lazy item of the work-log list. [key] is unique in the list and stable. */
sealed interface LogListLine {
  val key: String

  /**
   * Names the month the entries under it fall in. [month] is its first day; null is undated.
   * [firstLogId] keys it, so a month that somehow heads two runs still has two distinct keys.
   */
  data class MonthHeader(
    val month: LocalDate?,
    val count: Int,
    val firstLogId: String,
  ) : LogListLine {
    override val key: String get() = "month-$firstLogId"
  }

  /**
   * Logs the filter removed between the entry above and the entry below. The spine goes dashed
   * across it, so a line spanning six months never says nothing happened in them.
   */
  data class Gap(val omitted: Int, val afterLogId: String) : LogListLine {
    override val key: String get() = "gap-$afterLogId"
  }

  /**
   * A log on the spine. [connectsUp] and [connectsDown] say whether the line reaches the entry or
   * the [Gap] directly above or below; a header or an ad in between breaks it.
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
