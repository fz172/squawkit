package dev.fanfly.wingslog.feature.dashboard.host.overview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MeterReadingInputTest {

  @Test
  fun aDecimalMeterKeepsDigitsAndOnePoint() {
    assertThat(filterMeterInput("1201.5", decimal = true)).isEqualTo("1201.5")
    assertThat(filterMeterInput("12.5.3", decimal = true)).isEqualTo("12.53")
    assertThat(filterMeterInput("-1,201.5 hrs", decimal = true)).isEqualTo("1.2015")
  }

  @Test
  fun aCommaIsReadAsTheDecimalPoint() {
    assertThat(filterMeterInput("1201,5", decimal = true)).isEqualTo("1201.5")
  }

  @Test
  fun aWholeNumberMeterTakesNoPoint() {
    // An odometer: "84512.0 mi" is not how anyone writes mileage.
    assertThat(filterMeterInput("84512.7", decimal = false)).isEqualTo("845127")
  }

  @Test
  fun whatIsTypedParsesToTheReading() {
    assertThat(parseMeterInput("1201.5")).isEqualTo(1201.5)
    assertThat(parseMeterInput("84512")).isEqualTo(84512.0)
    // Mid-typing: a trailing point is still the number before it.
    assertThat(parseMeterInput("12.")).isEqualTo(12.0)
  }

  @Test
  fun nothingAndZeroAreNotAReading() {
    // Zero is stored the same as "not recorded", so saving it would draw a dash.
    assertThat(parseMeterInput("")).isNull()
    assertThat(parseMeterInput(".")).isNull()
    assertThat(parseMeterInput("0")).isNull()
    assertThat(parseMeterInput("0.0")).isNull()
  }
}
