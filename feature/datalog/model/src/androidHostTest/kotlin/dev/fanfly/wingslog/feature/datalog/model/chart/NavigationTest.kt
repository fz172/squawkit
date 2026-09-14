package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import org.junit.Test

class NavigationTest {

  private val duration = 3600

  @Test
  fun zoomInHalvesTheSpanAroundTheAnchor() {
    // Anchor at the middle of the full log: the middle stays put.
    val v = Navigation.zoomAround(null, duration, anchorFraction = 0.5, factor = 2.0)!!
    assertThat(v).isEqualTo(ViewWindow(900, 2700))
    // Anchor at the left edge: the start stays put.
    assertThat(Navigation.zoomAround(null, duration, 0.0, 4.0)).isEqualTo(ViewWindow(0, 900))
  }

  @Test
  fun zoomClampsToFiveSecondsAndToTheLog() {
    val tiny = Navigation.zoomAround(ViewWindow(100, 110), duration, 0.5, 100.0)!!
    assertThat(tiny.lengthSeconds).isEqualTo(Navigation.MIN_SPAN_SECONDS)
    assertThat(Navigation.zoomAround(ViewWindow(100, 110), duration, 0.5, 100.0)!!.startSeconds).isAtLeast(0)
    // Zooming out past the log returns null (full), never a window wider than the log.
    assertThat(Navigation.zoomAround(ViewWindow(1000, 2000), duration, 0.5, 0.1)).isNull()
    assertThat(Navigation.zoomAround(ViewWindow(0, 1000), duration, 0.5, 0.5)).isEqualTo(ViewWindow(0, 2000))
    // A log shorter than the minimum span cannot zoom.
    assertThat(Navigation.zoomAround(null, 4, 0.5, 2.0)).isNull()
  }

  @Test
  fun panClampsAndIsANoOpAtFullZoomOut() {
    assertThat(Navigation.pan(null, duration, 100.0)).isNull()
    assertThat(Navigation.pan(ViewWindow(100, 200), duration, 50.0)).isEqualTo(ViewWindow(150, 250))
    assertThat(Navigation.pan(ViewWindow(100, 200), duration, -500.0)).isEqualTo(ViewWindow(0, 100))
    assertThat(Navigation.pan(ViewWindow(3400, 3500), duration, 500.0)).isEqualTo(ViewWindow(3500, 3600))
  }

  @Test
  fun brushMapsPixelsToTimeInsideTheCurrentView() {
    // Full log across 1000 px: brushing 250..500 px selects 900..1800 s.
    assertThat(Navigation.brushToWindow(null, duration, 250f, 500f, 1000)).isEqualTo(ViewWindow(900, 1800))
    // Reversed drag gives the same window.
    assertThat(Navigation.brushToWindow(null, duration, 500f, 250f, 1000)).isEqualTo(ViewWindow(900, 1800))
    // Inside a zoomed view the pixels map to that view's span.
    assertThat(Navigation.brushToWindow(ViewWindow(1000, 2000), duration, 0f, 500f, 1000)).isEqualTo(ViewWindow(1000, 1500))
  }

  @Test
  fun aTinyBrushExpandsToTheMinimumSpanAndAnEmptyOneChangesNothing() {
    val v = Navigation.brushToWindow(ViewWindow(0, 100), duration, 500f, 510f, 1000)!!
    assertThat(v.lengthSeconds).isEqualTo(Navigation.MIN_SPAN_SECONDS)
    assertThat((v.startSeconds + v.endSeconds) / 2).isWithin(1).of(50)
    assertThat(Navigation.brushToWindow(ViewWindow(0, 100), duration, 300f, 300f, 1000)).isEqualTo(ViewWindow(0, 100))
    assertThat(Navigation.brushToWindow(null, duration, 0f, 1000f, 1000)).isNull()
  }
}
