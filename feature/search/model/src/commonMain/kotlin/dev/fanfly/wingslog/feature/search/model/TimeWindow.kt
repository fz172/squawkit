package dev.fanfly.wingslog.feature.search.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A period a record’s date must fall in. Which date, and which way, is the adapter’s call. */
sealed interface TimeWindow {
  data object All : TimeWindow

  /** Ends today; inclusive at both ends. */
  data class LastMonths(val months: Int) : TimeWindow

  /** Inclusive at both ends. */
  data class Custom(val start: LocalDate, val end: LocalDate) : TimeWindow
}

/** Back from today (work done) or forward (task due). */
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
