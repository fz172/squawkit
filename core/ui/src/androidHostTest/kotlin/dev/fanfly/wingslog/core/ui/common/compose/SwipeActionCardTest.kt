package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SwipeActionCardTest {

  @get:Rule
  val rule = createComposeRule()

  private val resolve = SwipeAction(Icons.Default.Check, "Resolve", SwipeActionTone.POSITIVE, {})
  private val delete = SwipeAction(Icons.Default.Delete, "Delete", SwipeActionTone.DESTRUCTIVE, {})

  @Composable
  private fun Card(
    tag: String,
    actions: List<SwipeAction>,
    controller: SwipeRevealController,
    onClick: () -> Unit = {},
  ) {
    SwipeActionCard(actions = actions, controller = controller, key = tag, modifier = Modifier.width(320.dp)) {
      // The button sits at the trailing edge so it is still on screen once the card slides open.
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(80.dp)
          .testTag(tag),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Button(onClick = onClick, modifier = Modifier.testTag("$tag-click")) {}
      }
    }
  }

  /** Unclipped: an open card sits partly outside the root and a clipped left edge reads 0. */
  private fun cardLeft(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().left

  @Test
  fun dragPastThreshold_opensInEitherDirection_andBothRevealTheSameLabels() {
    rule.setContent {
      MaterialTheme { Card("card", listOf(resolve, delete), rememberSwipeRevealController()) }
    }

    rule.onNodeWithTag("card").performTouchInput { swipeLeft() }
    rule.waitForIdle()
    assertThat(cardLeft("card")).isLessThan(0.dp)
    rule.onNodeWithText("Resolve").assertIsDisplayed()
    rule.onNodeWithText("Delete").assertIsDisplayed()
    val openEnd = cardLeft("card")

    rule.onNodeWithTag("card").performTouchInput { swipeRight() }
    rule.waitForIdle()
    // From open-end a swipe right lands on closed; one more opens the start side.
    rule.onNodeWithTag("card").performTouchInput { swipeRight() }
    rule.waitForIdle()
    assertThat(cardLeft("card")).isGreaterThan(0.dp)
    assertThat(cardLeft("card")).isEqualTo(-openEnd)
    rule.onNodeWithText("Resolve").assertIsDisplayed()
    rule.onNodeWithText("Delete").assertIsDisplayed()
  }

  @Test
  fun dragUnderThreshold_snapsBackClosed() {
    rule.setContent {
      MaterialTheme { Card("card", listOf(resolve, delete), rememberSwipeRevealController()) }
    }

    rule.onNodeWithTag("card").performTouchInput {
      down(center)
      moveBy(Offset(-30f, 0f))
      up()
    }
    rule.waitForIdle()

    assertThat(cardLeft("card")).isEqualTo(0.dp)
  }

  @Test
  fun emptyActionList_doesNotMove() {
    rule.setContent {
      MaterialTheme { Card("card", emptyList(), rememberSwipeRevealController()) }
    }

    rule.onNodeWithTag("card").performTouchInput { swipeLeft() }
    rule.waitForIdle()

    assertThat(cardLeft("card")).isEqualTo(0.dp)
  }

  @Test
  fun openDistance_isTheActionRowWidth() {
    rule.setContent {
      MaterialTheme { Card("card", listOf(resolve, delete), rememberSwipeRevealController()) }
    }

    rule.onNodeWithTag("card").performTouchInput { swipeLeft() }
    rule.waitForIdle()

    val resolveBounds = rule.onNodeWithText("Resolve").getUnclippedBoundsInRoot()
    val deleteBounds = rule.onNodeWithText("Delete").getUnclippedBoundsInRoot()
    // The revealed row spans exactly the gap the card slid open, inset by ActionRowInset at each
    // end. Measuring the row inside its own inset is what made the card stop 4dp short and park
    // over the first icon, so this pins both edges rather than just the width.
    assertThat(resolveBounds.left).isEqualTo(320.dp + cardLeft("card") + ActionRowInset)
    assertThat(deleteBounds.right).isEqualTo(320.dp - ActionRowInset)
  }

  @Test
  fun tappingAnOpenCard_closesIt_withoutFiringItsClick() {
    var clicks = 0
    rule.setContent {
      MaterialTheme {
        Card("card", listOf(resolve, delete), rememberSwipeRevealController(), onClick = { clicks++ })
      }
    }
    rule.onNodeWithTag("card").performTouchInput { swipeLeft() }
    rule.waitForIdle()

    rule.onNodeWithTag("card-click").performClick()
    rule.waitForIdle()

    assertThat(clicks).isEqualTo(0)
    assertThat(cardLeft("card")).isEqualTo(0.dp)
  }

  @Test
  fun openingASecondCard_closesTheFirst() {
    rule.setContent {
      MaterialTheme {
        val controller = rememberSwipeRevealController()
        Column {
          Card("first", listOf(resolve, delete), controller)
          Card("second", listOf(delete), controller)
        }
      }
    }
    rule.onNodeWithTag("first").performTouchInput { swipeLeft() }
    rule.waitForIdle()
    assertThat(cardLeft("first")).isLessThan(0.dp)

    rule.onNodeWithTag("second").performTouchInput { swipeLeft() }
    rule.waitForIdle()

    assertThat(cardLeft("first")).isEqualTo(0.dp)
    assertThat(cardLeft("second")).isLessThan(0.dp)
  }
}
