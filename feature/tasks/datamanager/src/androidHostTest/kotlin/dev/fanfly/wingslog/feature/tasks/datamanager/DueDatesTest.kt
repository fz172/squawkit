package dev.fanfly.wingslog.feature.tasks.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Test

/** The picked day survives the trip through the picker's millis, storage, and any zone. */
class DueDatesTest {

  private val picked = LocalDate(2026, 9, 15)

  @Test
  fun pickerMillisRoundTrip() {
    assertThat(picked.toPickerMillis().pickerMillisToDate()).isEqualTo(picked)
  }

  @Test
  fun storedAndReadAsTheSameDayRegardlessOfZone() {
    // Neither side consults a zone, so there is nothing for a device in another one to disagree with.
    assertThat(picked.toDueInstant().toDueDate()).isEqualTo(picked)
  }

  @Test
  fun aLegacyUtcMidnightValueStillMeansThePickedDay() {
    // What every build before this one wrote — and what read as 14 September in Los Angeles.
    val legacy = toWireInstant(picked.toPickerMillis() / 1000)

    assertThat(legacy.toDueDate()).isEqualTo(picked)
  }

  @Test
  fun theStoredInstantReadsAsThePickedDayEvenThroughALocalZone() {
    // The belt-and-braces half: a reader that took the local date would still be right in LA
    // and Tokyo, because the value sits at UTC noon.
    val stored = picked.toDueInstant()
    for (zone in listOf("America/Los_Angeles", "Asia/Tokyo", "UTC")) {
      val local = kotlin.time.Instant.fromEpochSeconds(stored.getEpochSecond())
        .toLocalDateTime(TimeZone.of(zone)).date
      assertThat(local).isEqualTo(picked)
    }
  }
}
