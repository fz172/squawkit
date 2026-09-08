package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SwipeRevealControllerTest {

  @Test
  fun openingAnotherCard_replacesTheKey() {
    val controller = SwipeRevealController()

    controller.open("a")
    controller.open("b")

    assertThat(controller.openKey).isEqualTo("b")
  }

  @Test
  fun closeIf_onlyReleasesTheKeyThatIsOpen() {
    val controller = SwipeRevealController()
    controller.open("a")

    controller.closeIf("b")
    assertThat(controller.openKey).isEqualTo("a")

    controller.closeIf("a")
    assertThat(controller.openKey).isNull()
  }

  @Test
  fun verticalScroll_closes_andConsumesNothing() {
    val controller = SwipeRevealController()
    controller.open("a")

    val consumed = controller.closeOnScroll.onPreScroll(Offset(0f, 12f), NestedScrollSource.UserInput)

    assertThat(consumed).isEqualTo(Offset.Zero)
    assertThat(controller.openKey).isNull()
  }

  @Test
  fun horizontalOnlyScroll_leavesTheCardOpen() {
    val controller = SwipeRevealController()
    controller.open("a")

    controller.closeOnScroll.onPreScroll(Offset(12f, 0f), NestedScrollSource.UserInput)

    assertThat(controller.openKey).isEqualTo("a")
  }
}
