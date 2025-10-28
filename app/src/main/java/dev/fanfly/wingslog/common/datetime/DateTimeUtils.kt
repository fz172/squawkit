package dev.fanfly.wingslog.common.datetime

import com.google.protobuf.Timestamp
import com.google.protobuf.timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.ExperimentalTime


fun Timestamp.toLocalDate(): LocalDate {
  return Instant.ofEpochSecond(seconds, nanos.toLong())
    .atZone(ZoneId.of("UTC"))
    .toLocalDate()
}

fun LocalDate.toDisplayFormat(): String {
  val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
  return format(formatter)
}

@OptIn(ExperimentalTime::class)
fun Timestamp.toInstant() = kotlin.time.Instant.fromEpochSeconds(this.seconds, this.nanos)

@OptIn(ExperimentalTime::class)
fun kotlin.time.Instant.toTimestamp() = timestamp {
  seconds = epochSeconds
  nanos = nanosecondsOfSecond

}