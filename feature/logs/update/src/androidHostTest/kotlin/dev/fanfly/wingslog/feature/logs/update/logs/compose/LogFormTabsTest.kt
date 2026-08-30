package dev.fanfly.wingslog.feature.logs.update.logs.compose

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.Capabilities
import org.junit.Test

/**
 * That the hours tab is removed for a template with no meters.
 *
 * The airplane template sets `meters = true`, so against the shipped set this gate is
 * indistinguishable from no gate at all. Only `meters = false` separates them.
 */
class LogFormTabsTest {

  @Test
  fun aTemplateWithoutMetersHasNoHoursTab() {
    val tabs = logFormTabsFor(Capabilities(meters = false))

    assertThat(tabs).doesNotContain(LogFormTab.HOURS)
    assertThat(tabs).containsExactly(LogFormTab.WORK, LogFormTab.RECORDS)
      .inOrder()
  }

  @Test
  fun removingTheHoursTabDoesNotRenumberTheOthers() {
    // Without the identity dispatch, dropping HOURS would make page 1 the records tab while the row
    // still labelled it "Hours" — the wrong form under the wrong heading, and no error anywhere.
    val tabs = logFormTabsFor(Capabilities(meters = false))

    assertThat(tabs[1]).isEqualTo(LogFormTab.RECORDS)
    assertThat(tabs[1].spec).isEqualTo(LOG_RECORDS_TAB)
  }

  @Test
  fun theAirplaneSetIsUnchangedFromWhatShipped() {
    assertThat(logFormTabsFor(Capabilities(meters = true))).containsExactly(
      LogFormTab.WORK,
      LogFormTab.HOURS,
      LogFormTab.RECORDS,
    )
      .inOrder()
  }
}
