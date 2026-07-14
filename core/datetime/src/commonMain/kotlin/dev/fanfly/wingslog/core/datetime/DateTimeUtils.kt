package dev.fanfly.wingslog.core.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.squareup.wire.Instant as WireInstant

/**
 * Converts this WireInstant to the calendar date it falls on in [timeZone] — the device's zone
 * by default, which is the date a user reading it on screen expects to see.
 *
 * Fields that encode a wall date as UTC midnight rather than a real instant — a Material date
 * picker selection, such as a certificate expiration — must pass [TimeZone.UTC] to read that
 * date back unshifted.
 */
fun WireInstant.toLocalDate(
  timeZone: TimeZone = TimeZone.currentSystemDefault(),
): LocalDate {
  return toInstant().toLocalDateTime(timeZone).date
}

fun WireInstant.toInstant(): Instant = Instant.fromEpochSeconds(
  this.getEpochSecond(),
  this.getNano()
    .toLong()
)

fun Instant.toWireInstant(): WireInstant =
  toWireInstant(epochSeconds, nanosecondsOfSecond)

expect fun toWireInstant(epochSeconds: Long, nanos: Int = 0): WireInstant

private val DisplayDateFormat = LocalDate.Format {
  monthNumber()
  char('/')
  day()
  char('/')
  year()
}

private val WordDateFormat = LocalDate.Format {
  monthName(MonthNames.ENGLISH_ABBREVIATED)
  char(' ')
  day()
  char(',')
  char(' ')
  year()
}

fun LocalDate.toDisplayFormat(numberOnly: Boolean = true): String {
  return if (numberOnly) DisplayDateFormat.format(this)
  else WordDateFormat.format(this)
}
