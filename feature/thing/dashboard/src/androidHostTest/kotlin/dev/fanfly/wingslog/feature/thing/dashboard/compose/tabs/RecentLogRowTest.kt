package dev.fanfly.wingslog.feature.thing.dashboard.compose.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * Where a Recent activity row puts the words the reader can see.
 *
 * A description saved from the resolve-squawk prefill keeps a trailing newline whenever the note
 * under it is emptied. That line is invisible but it is measured, and it used to lift the
 * description clear of the pill and the date beside it.
 *
 * The row also pins those three to a shared baseline, which this cannot assert: Robolectric gives
 * every text the same font metrics, so baseline and centre alignment land in the same place here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecentLogRowTest {

  @get:Rule
  val rule = createComposeRule()

  private fun log(description: String) = MaintenanceLog(
    id = "log-1",
    timestamp = Instant.fromEpochSeconds(1_756_000_000)
      .toWireInstant(),
    work_description = description,
    component_type = ComponentType.COMPONENT_ENGINE,
  )

  /** Where the text sits in the row, rather than where its own box happens to start. */
  private fun baselineOf(text: String): Float =
    rule.onNodeWithText(text, substring = true)
      .let {
        it.getUnclippedBoundsInRoot().top.value +
          it.getAlignmentLinePosition(FirstBaseline).value
      }

  @Test
  fun `a description ending in a blank line does not lift the words`() {
    var description by mutableStateOf("Resolve squawk \"Debug fuel pressure high\"")
    rule.setContent {
      WingslogTheme(darkTheme = true) {
        RecentLogRow(
          log = log(description),
          onClick = {})
      }
    }
    rule.waitForIdle()
    val clean = baselineOf("Debug fuel")

    description = "Resolve squawk \"Debug fuel pressure high\"\n"
    rule.waitForIdle()

    assertThat(baselineOf("Debug fuel")).isWithin(0.5f)
      .of(clean)
  }
}
