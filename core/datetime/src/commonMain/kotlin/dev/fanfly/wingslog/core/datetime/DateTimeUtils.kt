package dev.fanfly.wingslog.core.datetime

import com.squareup.wire.Instant as WireInstant
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

/**
 * Converts this WireInstant to a LocalDate in the given zone (UTC by default).
 */
fun WireInstant.toLocalDate(timeZone: TimeZone = TimeZone.UTC): LocalDate {
  return toInstant().toLocalDateTime(timeZone).date
}

fun WireInstant.toInstant(): Instant =
  Instant.fromEpochSeconds(this.getEpochSecond(), this.getNano().toLong())


expect fun toWireInstant(epochSeconds: Long, nanos: Int =0): WireInstant

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
