package dev.fanfly.wingslog.feature.tasks.datamanager

import com.squareup.wire.Instant as WireInstant
import dev.fanfly.wingslog.core.datetime.toWireInstant
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * A forced due is a **date**, not a moment — and the conversions around it disagreed.
 *
 * Material's date picker reports the picked day as UTC midnight in millis. Read back in the
 * device zone that is 17:00 the day before anywhere west of Greenwich, so the form showed, and
 * then stored, the day before the one tapped. The fix is to never leave the picker's convention:
 * a forced due is a UTC calendar date on every side, so the same instant names the same day on
 * every device, and no zone arithmetic is ever applied to it.
 */

/** The picker's convention: a day is its UTC midnight in millis. */
fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

fun Long.pickerMillisToDate(): LocalDate =
  Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

/**
 * Stored as UTC noon of the day, so a reader that (wrongly) took the local date would still get
 * the right day anywhere within ±11 hours of UTC — belt and braces for the one reader this
 * module does not own.
 */
fun LocalDate.toDueInstant(): WireInstant =
  toWireInstant(atStartOfDayIn(TimeZone.UTC).epochSeconds + NOON_SECONDS)

/**
 * The day a stored forced due means: its UTC date. Correct for values written by [toDueInstant]
 * and for the UTC-midnight values every earlier build wrote, in every zone.
 */
fun WireInstant.toDueDate(): LocalDate =
  Instant.fromEpochSeconds(getEpochSecond()).toLocalDateTime(TimeZone.UTC).date

private const val NOON_SECONDS = 12L * 60 * 60
