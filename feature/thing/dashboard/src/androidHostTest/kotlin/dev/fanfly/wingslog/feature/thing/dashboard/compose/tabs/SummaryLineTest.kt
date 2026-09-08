package dev.fanfly.wingslog.feature.thing.dashboard.compose.tabs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the Recent activity rail may render for a description.
 *
 * The row centres the badge, the text and the date against each other, so any line the text keeps
 * beyond the visible one makes the text box taller than its neighbours and lifts the words above
 * them. A description saved from the resolve-squawk prefill ends in a newline whenever the note
 * under it is emptied, which is what put two rows out of line on the dashboard.
 */
class SummaryLineTest {

  @Test
  fun `a trailing newline leaves no empty second line`() {
    assertThat("Resolve squawk \"Debug fuel pressure high\"\n".asSummaryLine())
      .isEqualTo("Resolve squawk \"Debug fuel pressure high\"")
  }

  @Test
  fun `a blank line between two lines collapses to one space`() {
    assertThat("Replaced left magneto.\n\nMag drop within limits.".asSummaryLine())
      .isEqualTo("Replaced left magneto. Mag drop within limits.")
  }

  @Test
  fun `a windows line ending collapses the same way`() {
    assertThat("Oil change.\r\nFilter cut, no metal.".asSummaryLine())
      .isEqualTo("Oil change. Filter cut, no metal.")
  }

  @Test
  fun `a single line is left alone`() {
    assertThat("Resolve squawk \"Oil purge lifter check\"".asSummaryLine())
      .isEqualTo("Resolve squawk \"Oil purge lifter check\"")
  }

  @Test
  fun `a description of only whitespace comes back empty`() {
    assertThat("  \n\t ".asSummaryLine()).isEmpty()
  }
}
