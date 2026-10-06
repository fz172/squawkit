package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntervalFieldsTest {

  @Test
  fun aNumberAboveZeroAndWithinTheLimitIsTaken() {
    assertThat(parseInterval("50", MAX_METER_INTERVAL)).isEqualTo(50.0)
    assertThat(parseInterval("7.5", MAX_METER_INTERVAL)).isEqualTo(7.5)
    assertThat(parseInterval("1200", MAX_MONTHS)).isEqualTo(1200.0)
  }

  @Test
  fun nothingZeroAndNonsenseAreNotTaken() {
    assertThat(parseInterval("", MAX_MONTHS)).isNull()
    assertThat(parseInterval("0", MAX_MONTHS)).isNull()
    assertThat(parseInterval("1.2.3", MAX_METER_INTERVAL)).isNull()
  }

  @Test
  fun aNumberPastTheLimitIsNotTaken() {
    assertThat(parseInterval("1201", MAX_MONTHS)).isNull()
    // Would have become Int.MAX_VALUE days, or an infinite meter interval.
    assertThat(parseInterval("99999999999", MAX_DAYS)).isNull()
    assertThat(parseInterval("9".repeat(400), MAX_METER_INTERVAL)).isNull()
  }

  @Test
  fun aWholeMeterIntervalShowsWithoutADecimalPoint() {
    assertThat(50f.toFieldText()).isEqualTo("50")
    assertThat(5000f.toFieldText()).isEqualTo("5000")
    assertThat(7.5f.toFieldText()).isEqualTo("7.5")
  }
}
