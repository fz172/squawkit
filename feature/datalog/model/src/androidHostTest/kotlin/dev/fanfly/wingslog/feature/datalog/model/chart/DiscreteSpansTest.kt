package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscreteSpansTest {

  @Test
  fun fromTextCollapsesCompoundAndShortSpansAndSkipsInactive() {
    val time = intArrayOf(0, 5, 10, 15, 20, 25)
    val values = arrayOf<String?>(
      null,
      "LOI",
      "LOI",
      "NONE",
      "OIL PRESS / FPCM FAULT / NO AOA CAL",
      "OIL PRESS / FPCM FAULT / NO AOA CAL",
    )
    val spans = DiscreteSpans.fromText(values, time, durationSeconds = 30)

    assertThat(spans).hasSize(2)
    assertThat(spans[0].startSec).isEqualTo(5)
    assertThat(spans[0].endSec).isEqualTo(15)
    assertThat(spans[0].pillLabel).isEqualTo("LOI")
    assertThat(spans[0].tone).isEqualTo(SpanTone.CAUTION)

    assertThat(spans[1].startSec).isEqualTo(20)
    assertThat(spans[1].endSec).isEqualTo(30)
    assertThat(spans[1].pillLabel).isEqualTo("OIL PRESS +2")
    assertThat(spans[1].tokens).containsExactly(
      "OIL PRESS",
      "FPCM FAULT",
      "NO AOA CAL"
    ).inOrder()
    assertThat(spans[1].tone).isEqualTo(SpanTone.FAULT)
  }

  @Test
  fun fromDiscreteOnlyEmitsActiveSpans() {
    val time = intArrayOf(0, 2, 4, 6, 8)
    val values = floatArrayOf(0f, 1f, 1f, 0f, 1f)
    val spans = DiscreteSpans.fromDiscrete(
      values,
      time,
      durationSeconds = 10,
      activeLabel = "FPCM FAULT"
    )

    assertThat(spans).hasSize(2)
    assertThat(spans[0].startSec).isEqualTo(2)
    assertThat(spans[0].endSec).isEqualTo(6)
    assertThat(spans[0].tone).isEqualTo(SpanTone.FAULT)
    assertThat(spans[1].startSec).isEqualTo(8)
    assertThat(spans[1].endSec).isEqualTo(10)
  }

  @Test
  fun fromSteppedNumericFormatsMHzAndCollapses() {
    val time = intArrayOf(0, 10, 20, 30)
    val values = floatArrayOf(122.7f, 122.7f, 118.35f, 118.35f)
    val spans = DiscreteSpans.fromSteppedNumeric(
      values,
      time,
      durationSeconds = 40,
      unit = "MHz"
    )

    assertThat(spans).hasSize(2)
    assertThat(spans[0].pillLabel).isEqualTo("122.700")
    assertThat(spans[0].startSec).isEqualTo(0)
    assertThat(spans[0].endSec).isEqualTo(20)
    assertThat(spans[1].pillLabel).isEqualTo("118.350")
    assertThat(spans[1].startSec).isEqualTo(20)
    assertThat(spans[1].endSec).isEqualTo(40)
  }
}
