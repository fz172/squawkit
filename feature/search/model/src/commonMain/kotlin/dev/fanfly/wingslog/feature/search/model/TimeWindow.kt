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

/**
 * What a chip tap leaves selected: tapping the window already in force clears back to [TimeWindow.All],
 * so a pick-one section can be emptied where it was set instead of through *Clear all*. The custom
 * chip stands for any custom range, so it compares by kind rather than by its dates.
 */
fun timeWindowAfterTap(current: TimeWindow, tapped: TimeWindow): TimeWindow = when {
  current == tapped -> TimeWindow.All
  current is TimeWindow.Custom && tapped is TimeWindow.Custom -> TimeWindow.All
  else -> tapped
}

/** Back from today (work done) or forward (task due). */
enum class TimeDirection { PAST, FUTURE }

fun TimeWindow.contains(
  date: LocalDate,
  today: LocalDate,
  direction: TimeDirection
): Boolean =
  when (this) {
    TimeWindow.All -> true
    is TimeWindow.LastMonths -> when (direction) {
      TimeDirection.PAST -> date >= today.minus(
        months,
        DateTimeUnit.MONTH
      ) && date <= today

      TimeDirection.FUTURE -> date >= today && date <= today.plus(
        months,
        DateTimeUnit.MONTH
      )
    }

    is TimeWindow.Custom -> date >= start && date <= end
  }
