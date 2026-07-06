package dev.fanfly.wingslog.core.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatUtilsTest {

  @Test
  fun formatToOneDecimalPlace_truncatesExtraDecimals() {
    assertThat(12.34.formatToOneDecimalPlace()).isEqualTo("12.3")
  }

  @Test
  fun formatToOneDecimalPlace_roundsHalfUp() {
    assertThat(12.35.formatToOneDecimalPlace()).isEqualTo("12.4")
    assertThat(9.96.formatToOneDecimalPlace()).isEqualTo("10.0")
  }

  @Test
  fun formatToOneDecimalPlace_wholeNumberKeepsTrailingZero() {
    assertThat(12.0.formatToOneDecimalPlace()).isEqualTo("12.0")
  }

  @Test
  fun formatToOneDecimalPlace_zero() {
    assertThat(0.0.formatToOneDecimalPlace()).isEqualTo("0.0")
  }

  @Test
  fun formatToOneDecimalPlace_valueBelowOne_padsLeadingZero() {
    assertThat(0.34.formatToOneDecimalPlace()).isEqualTo("0.3")
    assertThat(0.05.formatToOneDecimalPlace()).isEqualTo("0.1")
  }

  @Test
  fun formatToOneDecimalPlace_negativeValues() {
    assertThat((-12.34).formatToOneDecimalPlace()).isEqualTo("-12.3")
    assertThat((-0.34).formatToOneDecimalPlace()).isEqualTo("-0.3")
  }
}
