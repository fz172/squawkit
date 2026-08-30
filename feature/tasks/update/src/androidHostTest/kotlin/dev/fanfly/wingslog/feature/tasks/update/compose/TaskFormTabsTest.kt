package dev.fanfly.wingslog.feature.tasks.update.compose

import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.Capabilities
import org.junit.Test

/**
 * That the compliance tab is removed, not merely disabled, when a template does not have it.
 *
 * The airplane template sets `compliance = true`, so against the shipped set this gate is
 * indistinguishable from no gate at all. Only calling it with `compliance = false` separates them.
 */
class TaskFormTabsTest {

  private val airplane = Capabilities(compliance = true)
  private val house = Capabilities(compliance = false)

  @Test
  fun theFailOpenDefaultRemovesNothing() {
    // #660: a wrong default must not silently drop the compliance tab for aviation users.
    assertThat(taskFormTabsFor(CurrentThingTemplate.ALL_ENABLED, includeAdjustments = true))
      .containsExactly(
        TaskFormTab.IDENTITY,
        TaskFormTab.COMPLIANCE,
        TaskFormTab.SCHEDULE,
        TaskFormTab.ADJUSTMENTS,
      ).inOrder()
  }

  @Test
  fun aTemplateWithoutComplianceHasNoComplianceTab() {
    val tabs = taskFormTabsFor(house, includeAdjustments = false)

    assertThat(tabs).doesNotContain(TaskFormTab.COMPLIANCE)
    assertThat(tabs).containsExactly(TaskFormTab.IDENTITY, TaskFormTab.SCHEDULE)
      .inOrder()
  }

  @Test
  fun removingATabDoesNotRenumberTheOthers() {
    // The reason the screens dispatch on identity rather than page number. With `0 -> 1 -> 2 ->`,
    // dropping compliance would have made page 1 the schedule tab while the row still labelled it
    // "Compliance" — the schedule form rendered under the wrong heading, with no error anywhere.
    val tabs = taskFormTabsFor(house, includeAdjustments = true)

    assertThat(tabs[1]).isEqualTo(TaskFormTab.SCHEDULE)
    assertThat(tabs[1].spec).isEqualTo(SCHEDULE_TAB)
    assertThat(tabs.map { it.spec }).doesNotContain(COMPLIANCE_TAB)
  }

  @Test
  fun theAirplaneSetIsUnchangedFromWhatShipped() {
    // Phase 2's acceptance criterion, for this gate: four tabs on edit, three on add, same order.
    assertThat(
      taskFormTabsFor(
        airplane,
        includeAdjustments = true
      )
    ).containsExactly(
      TaskFormTab.IDENTITY,
      TaskFormTab.COMPLIANCE,
      TaskFormTab.SCHEDULE,
      TaskFormTab.ADJUSTMENTS,
    )
      .inOrder()

    assertThat(
      taskFormTabsFor(
        airplane,
        includeAdjustments = false
      )
    ).containsExactly(
      TaskFormTab.IDENTITY,
      TaskFormTab.COMPLIANCE,
      TaskFormTab.SCHEDULE,
    )
      .inOrder()
  }

  @Test
  fun addNeverOffersAdjustments() {
    // There is nothing to adjust on a task that does not exist yet.
    assertThat(taskFormTabsFor(airplane, includeAdjustments = false))
      .doesNotContain(TaskFormTab.ADJUSTMENTS)
  }
}
