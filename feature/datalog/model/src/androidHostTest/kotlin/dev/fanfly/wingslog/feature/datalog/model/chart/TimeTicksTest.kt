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
    assertThat(TimeTicks.ticks(ViewWindow(0, 255), 60)).containsExactly(0, 60, 120, 180, 240).inOrder()
    assertThat(TimeTicks.ticks(ViewWindow(70, 130), 30)).containsExactly(90, 120).inOrder()
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
    assertThat(TimeTicks.xOf(127.5, ViewWindow(0, 255), 1000)).isWithin(1e-3f).of(500f)
  }
}
