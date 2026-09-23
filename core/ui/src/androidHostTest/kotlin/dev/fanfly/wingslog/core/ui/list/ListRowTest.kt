package dev.fanfly.wingslog.core.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListRowTest {

  @get:Rule
  val rule = createComposeRule()

  @Test
  fun rowIsAtLeastTheRowHeight_evenWithOneShortLine() {
    rule.setContent {
      MaterialTheme {
        ListRow(title = "Oil leak", modifier = Modifier.testTag("row"))
      }
    }

    // A list only scans as a column if every row is the same height, whatever it carries.
    rule.onNodeWithTag("row")
      .assertHeightIsAtLeast(Spacing.rowHeight)
  }

  @Test
  fun everySlotRenders() {
    rule.setContent {
      MaterialTheme {
        ListRow(
          title = "Oil leak",
          metadata = "12 Sep 2026 · Weeping from the accessory case",
          leading = { Text("lead") },
          trailing = { Text("trail") },
          supporting = { Text("matched on N533SL") },
        )
      }
    }

    rule.onNodeWithText("Oil leak")
      .assertExists()
    rule.onNodeWithText("12 Sep 2026 · Weeping from the accessory case")
      .assertExists()
    rule.onNodeWithText("lead")
      .assertExists()
    rule.onNodeWithText("trail")
      .assertExists()
    rule.onNodeWithText("matched on N533SL")
      .assertExists()
  }

  @Test
  fun tappingTheRowFiresOnClick() {
    var clicks = 0
    rule.setContent {
      MaterialTheme {
        ListRow(
          title = "Oil leak",
          modifier = Modifier.testTag("row"),
          onClick = { clicks++ },
        )
      }
    }

    rule.onNodeWithTag("row")
      .performClick()
    rule.waitForIdle()

    assertThat(clicks).isEqualTo(1)
  }

  @Test
  fun longTextTruncatesRatherThanGrowingTheRow() {
    val long =
      "Replaced the left magneto per service bulletin SB-1234 and ran a mag drop check " +
        "and a compression test on every cylinder"
    rule.setContent {
      MaterialTheme {
        Column {
          ListRow(
            title = "Short",
            metadata = "Short",
            modifier = Modifier.testTag("short")
          )
          ListRow(
            title = long,
            metadata = long,
            modifier = Modifier.testTag("long")
          )
        }
      }
    }

    // Both lines are capped at one, so the row that carries four sentences is exactly as tall as
    // the row that carries four words. Absolute heights are left alone: font metrics differ
    // between this runtime and a device, and what the row promises is a constant, not a number.
    assertThat(heightOf("long")).isEqualTo(heightOf("short"))
  }

  @Test
  fun theSupportingLineIsTheOneThingTheRowGrowsFor() {
    rule.setContent {
      MaterialTheme {
        Column {
          ListRow(
            title = "Oil leak",
            metadata = "12 Sep",
            modifier = Modifier.testTag("plain")
          )
          ListRow(
            title = "Oil leak",
            metadata = "12 Sep",
            modifier = Modifier.testTag("noted"),
            supporting = { Text("matched on N533SL") },
          )
        }
      }
    }

    assertThat(heightOf("noted")).isGreaterThan(heightOf("plain"))
  }

  private fun heightOf(tag: String): Float {
    val bounds = rule.onNodeWithTag(tag)
      .getUnclippedBoundsInRoot()
    return (bounds.bottom - bounds.top).value
  }
}
