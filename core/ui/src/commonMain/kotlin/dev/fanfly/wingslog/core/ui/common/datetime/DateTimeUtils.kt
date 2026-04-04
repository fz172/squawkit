package dev.fanfly.wingslog.core.ui.common.datetime

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import com.squareup.wire.Instant as WireInstant

fun WireInstant.toLocalDate(): LocalDate {
  // Convert WireInstant (epoch seconds and nanos) to LocalDate
  val instant = Instant.fromEpochSeconds(this.getEpochSecond(), this.getNano().toLong())
  return instant.toLocalDateTime(TimeZone.UTC).date
}

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
