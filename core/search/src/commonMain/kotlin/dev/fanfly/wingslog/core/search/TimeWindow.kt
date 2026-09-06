package dev.fanfly.wingslog.core.search

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * A period a record’s date must fall in. Which date, and whether the period reaches into the past
 * or the future, is the [RecordAdapter]’s call — a log is filtered by when the work was done, an
 * active task by when it is due (design §3.1).
 */
sealed interface TimeWindow {
  data object All : TimeWindow

  /** The preset periods: `LastMonths(3)`, `LastMonths(12)`. Ends today, inclusive at both ends. */
  data class LastMonths(val months: Int) : TimeWindow

  /** A user-chosen range, inclusive at both ends. */
  data class Custom(val start: LocalDate, val end: LocalDate) : TimeWindow
}

/** Whether a preset counts back from today (work done, squawk raised) or forward (task due). */
enum class TimeDirection { PAST, FUTURE }

fun TimeWindow.contains(date: LocalDate, today: LocalDate, direction: TimeDirection): Boolean =
  when (this) {
    TimeWindow.All -> true
    is TimeWindow.LastMonths -> when (direction) {
      TimeDirection.PAST -> date >= today.minus(months, DateTimeUnit.MONTH) && date <= today
      TimeDirection.FUTURE -> date >= today && date <= today.plus(months, DateTimeUnit.MONTH)
    }
    is TimeWindow.Custom -> date >= start && date <= end
  }
