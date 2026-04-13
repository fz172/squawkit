package dev.fanfly.wingslog.core.ui.common.datetime

import com.squareup.wire.Instant as WireInstant
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

/**
 *  Convert WireInstant (epoch seconds and nanos) to LocalDate
 */
fun WireInstant.toLocalDate(timeZone: TimeZone = TimeZone.UTC): LocalDate {
  return toInstant().toLocalDateTime(timeZone).date
}

fun WireInstant.toInstant(): Instant =
  Instant.fromEpochSeconds(this.getEpochSecond(), this.getNano().toLong())

private val DisplayDateFormat = LocalDate.Format {
  monthNumber()
  char('/')
  day()
  char('/')
  year()
}

fun LocalDate.toDisplayFormat(): String {
  return DisplayDateFormat.format(this)
}
