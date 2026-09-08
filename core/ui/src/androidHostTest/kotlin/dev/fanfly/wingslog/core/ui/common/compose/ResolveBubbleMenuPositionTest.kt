package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private val WINDOW = IntSize(width = 400, height = 800)
private val POPUP = IntSize(width = 300, height = 100)
private const val GAP = 14
private const val MARGIN = 16

class ResolveBubbleMenuPositionTest {

  @Test
  fun centredBottomBarAnchor_isUnchanged_bubbleAboveWithTailInTheMiddle() {
    val anchor = IntRect(left = 150, top = 700, right = 250, bottom = 750)

    val placement = placeBubble(anchor, WINDOW, POPUP, GAP, MARGIN)

    assertThat(placement.offset.x).isEqualTo(50)
    assertThat(placement.offset.y).isEqualTo(700 - 100 - GAP)
    assertThat(placement.tailSide).isEqualTo(BubbleTailSide.Bottom)
    assertThat(placement.tailCenterX).isEqualTo(POPUP.width / 2)
  }

  @Test
  fun anchorNearTheLeftEdge_clampsXToTheMargin_andTailTracksTheAnchor() {
    val anchor = IntRect(left = 20, top = 400, right = 100, bottom = 450)

    val placement = placeBubble(anchor, WINDOW, POPUP, GAP, MARGIN)

    assertThat(placement.offset.x).isEqualTo(MARGIN)
    assertThat(placement.tailCenterX).isEqualTo(anchor.center.x - MARGIN)
    assertThat(placement.tailCenterX).isLessThan(POPUP.width / 2)
  }

  @Test
  fun anchorNearTheRightEdge_clampsXToTheMargin_andTailTracksTheAnchor() {
    val anchor = IntRect(left = 300, top = 400, right = 380, bottom = 450)

    val placement = placeBubble(anchor, WINDOW, POPUP, GAP, MARGIN)

    assertThat(placement.offset.x).isEqualTo(WINDOW.width - POPUP.width - MARGIN)
    assertThat(placement.tailCenterX).isEqualTo(anchor.center.x - placement.offset.x)
    assertThat(placement.tailCenterX).isGreaterThan(POPUP.width / 2)
  }

  @Test
  fun anchorAtTheTop_flipsBelow_withTheTailOnTop() {
    val anchor = IntRect(left = 150, top = 40, right = 250, bottom = 90)

    val placement = placeBubble(anchor, WINDOW, POPUP, GAP, MARGIN)

    assertThat(placement.tailSide).isEqualTo(BubbleTailSide.Top)
    assertThat(placement.offset.y).isEqualTo(anchor.bottom + GAP)
  }

  @Test
  fun tailStaysClearOfBothCorners() {
    assertThat(clampTailCenter(tailCenterX = 2f, width = 300f, cornerPx = 16f, tailWidthPx = 16f))
      .isEqualTo(24f)
    assertThat(clampTailCenter(tailCenterX = 298f, width = 300f, cornerPx = 16f, tailWidthPx = 16f))
      .isEqualTo(276f)
    assertThat(clampTailCenter(tailCenterX = 150f, width = 300f, cornerPx = 16f, tailWidthPx = 16f))
      .isEqualTo(150f)
  }
}
