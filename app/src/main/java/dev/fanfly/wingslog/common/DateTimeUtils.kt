package dev.fanfly.wingslog.dev.fanfly.wingslog.common

import com.google.protobuf.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


fun Timestamp.toLocalDate(): LocalDate {
  return Instant.ofEpochSecond(seconds, nanos.toLong())
    .atZone(ZoneId.of("UTC"))
    .toLocalDate()
}

fun LocalDate.toDisplayFormat(): String {
  val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
  return format(formatter)
}