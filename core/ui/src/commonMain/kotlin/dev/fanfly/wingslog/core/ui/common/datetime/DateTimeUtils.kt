package dev.fanfly.wingslog.core.ui.common.datetime

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.squareup.wire.Instant as WireInstant

fun WireInstant.toLocalDate(): LocalDate {
    // Convert WireInstant (epoch seconds and nanos) to kotlinx.datetime.Instant
    val instant = Instant.fromEpochSeconds(this.epochSecond, this.nano.toInt())
    return instant.toLocalDateTime(TimeZone.UTC).date
}

expect fun LocalDate.toDisplayFormat(): String
