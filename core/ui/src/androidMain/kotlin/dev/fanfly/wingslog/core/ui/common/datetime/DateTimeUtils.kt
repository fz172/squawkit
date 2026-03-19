package dev.fanfly.wingslog.core.ui.common.datetime

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.squareup.wire.Instant as WireInstant


fun WireInstant.toLocalDate(): LocalDate {
  return atZone(ZoneId.of("UTC")).toLocalDate()
}

fun LocalDate.toDisplayFormat(): String {
  val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
  return format(formatter)
}
