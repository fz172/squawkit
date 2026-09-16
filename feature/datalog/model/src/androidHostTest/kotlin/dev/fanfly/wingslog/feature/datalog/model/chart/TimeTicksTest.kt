package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import org.junit.Test

class TimeTicksTest {

  @Test
  fun theStepKeepsLabelsAtLeastTheMinimumApart() {
    // 255 s across 1000 px at 72 px per label: 5 s = 19.6 px, 10 = 39, 30 = 118 → 30.
    assertThat(TimeTicks.step(255, 1000, 72f)).isEqualTo(30)
    // Six hours across 400 px: only the hour step fits.
    assertThat(TimeTicks.step(6 * 3600, 400, 72f)).isEqualTo(3600)
    // Wider than the ladder can use still returns the top step rather than crowding.
    assertThat(TimeTicks.step(100 * 3600, 400, 72f)).isEqualTo(3600)
    assertThat(TimeTicks.step(0, 400, 72f)).isEqualTo(3600)
  }

  @Test
  fun ticksAreMultiplesInsideTheWindow() {
    assertThat(TimeTicks.ticks(ViewWindow(0, 255), 60)).containsExactly(
      0,
      60,
      120,
      180,
      240
    )
      .inOrder()
    assertThat(TimeTicks.ticks(ViewWindow(70, 130), 30)).containsExactly(
      90,
      120
    )
      .inOrder()
    assertThat(TimeTicks.ticks(ViewWindow(3, 4), 5)).isEmpty()
  }

  @Test
  fun labelsReadMmSsThenHMmSs() {
    assertThat(TimeTicks.label(0)).isEqualTo("00:00")
    assertThat(TimeTicks.label(255)).isEqualTo("04:15")
    assertThat(TimeTicks.label(3600)).isEqualTo("1:00:00")
    assertThat(TimeTicks.label(3_725)).isEqualTo("1:02:05")
  }

  @Test
  fun edgeLabelsShiftInward() {
    assertThat(TimeTicks.labelCenterX(0f, 40f, 300)).isEqualTo(20f)
    assertThat(TimeTicks.labelCenterX(300f, 40f, 300)).isEqualTo(280f)
    assertThat(TimeTicks.labelCenterX(150f, 40f, 300)).isEqualTo(150f)
    assertThat(TimeTicks.xOf(127.5, ViewWindow(0, 255), 1000)).isWithin(1e-3f)
      .of(500f)
  }

  @Test
  fun clockTicksLandOnWallClockMultiplesNotElapsedOnes() {
    // The log starts at 14:47:56; a 60 s step must label 14:48:00, 14:49:00, … not 14:47:56 + 60.
    val origin = 14 * 3600 + 47 * 60 + 56
    val ticks = TimeTicks.clockTicks(
      ViewWindow(0, 200),
      step = 60,
      originSecondsOfDay = origin
    )

    assertThat(ticks).containsExactly(4, 64, 124, 184)
      .inOrder()
    ticks.forEach { assertThat((origin + it) % 60).isEqualTo(0) }
  }

  @Test
  fun clockLabelsShowSecondsOnlyBelowAMinuteStepAndWrapPastMidnight() {
    assertThat(
      TimeTicks.clockLabel(
        14 * 3600 + 48 * 60,
        step = 60
      )
    ).isEqualTo("14:48")
    assertThat(
      TimeTicks.clockLabel(
        14 * 3600 + 48 * 60 + 5,
        step = 5
      )
    ).isEqualTo("14:48:05")
    assertThat(TimeTicks.clockLabel(9 * 60, step = 3600)).isEqualTo("00:09")
    // A log that runs past midnight keeps counting; the label wraps rather than reading 24:30.
    assertThat(
      TimeTicks.clockLabel(
        24 * 3600 + 30 * 60,
        step = 60
      )
    ).isEqualTo("00:30")
  }

  @Test
  fun clockTicksOnAnAlignedStartMatchTheElapsedTicks() {
    val window = ViewWindow(0, 300)
    assertThat(TimeTicks.clockTicks(window, step = 60, originSecondsOfDay = 0))
      .isEqualTo(TimeTicks.ticks(window, step = 60))
    assertThat(
      TimeTicks.clockTicks(
        window,
        step = 0,
        originSecondsOfDay = 10
      )
    ).isEmpty()
  }
}
