package dev.fanfly.wingslog.core.ui.adaptive.compose

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LayoutTierTest {

  @Test
  fun belowMediumMin_isCompact() {
    assertThat(layoutTierFor(0.dp)).isEqualTo(LayoutTier.COMPACT)
    assertThat(layoutTierFor(360.dp)).isEqualTo(LayoutTier.COMPACT)
    assertThat(layoutTierFor(699.dp)).isEqualTo(LayoutTier.COMPACT)
  }

  @Test
  fun mediumRange_isMedium() {
    assertThat(layoutTierFor(700.dp)).isEqualTo(LayoutTier.MEDIUM)
    assertThat(layoutTierFor(900.dp)).isEqualTo(LayoutTier.MEDIUM)
    assertThat(layoutTierFor(1039.dp)).isEqualTo(LayoutTier.MEDIUM)
  }

  @Test
  fun expandedRange_isExpanded() {
    assertThat(layoutTierFor(1040.dp)).isEqualTo(LayoutTier.EXPANDED)
    assertThat(layoutTierFor(1179.dp)).isEqualTo(LayoutTier.EXPANDED)
  }

  @Test
  fun atOrAboveLargeMin_isLarge() {
    assertThat(layoutTierFor(1180.dp)).isEqualTo(LayoutTier.LARGE)
    assertThat(layoutTierFor(1920.dp)).isEqualTo(LayoutTier.LARGE)
  }

  @Test
  fun derivedFlags_matchTier() {
    assertThat(LayoutTier.COMPACT.isCompact).isTrue()
    assertThat(LayoutTier.COMPACT.hasSideNav).isFalse()

    assertThat(LayoutTier.MEDIUM.hasSideNav).isTrue()
    assertThat(LayoutTier.MEDIUM.hasFullSidebar).isTrue()

    assertThat(LayoutTier.EXPANDED.hasFullSidebar).isTrue()
    assertThat(LayoutTier.EXPANDED.hasDashboardRail).isFalse()

    assertThat(LayoutTier.LARGE.hasFullSidebar).isTrue()
    assertThat(LayoutTier.LARGE.hasDashboardRail).isTrue()
  }
}