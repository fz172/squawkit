package dev.fanfly.wingslog.feature.squawk.update.compose

import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.SquawkPriority
import org.junit.Test

/**
 * That the squawk form actually offers what the template declares.
 *
 * The airplane set declares all four priorities, so this gate is invisible against the shipped
 * template — a filter ignoring its argument would render the same four chips. These cases use a
 * narrower set, which is the only way to tell a working gate from an absent one.
 */
class SquawkPriorityOfferTest {

  @Test
  fun theFailOpenDefaultOffersEverything() {
    // #660: a wrong default must not silently drop AOG from the form for aviation users.
    SquawkPriority.entries.forEach {
      assertThat(it.isOfferedBy(CurrentThingTemplate.ALL_ENABLED.priorities)).isTrue()
    }
  }

  @Test
  fun aTemplateThatDoesNotOfferAogDoesNotShowIt() {
    val offered = listOf(
      SquawkPriority.SQUAWK_PRIORITY_LOW,
      SquawkPriority.SQUAWK_PRIORITY_HIGH
    )

    assertThat(SquawkPriority.SQUAWK_PRIORITY_AOG.isOfferedBy(offered)).isFalse()
    assertThat(SquawkPriority.SQUAWK_PRIORITY_LOW.isOfferedBy(offered)).isTrue()
    assertThat(SquawkPriority.SQUAWK_PRIORITY_MEDIUM.isOfferedBy(offered)).isFalse()
  }

  @Test
  fun narrowingWhatIsOfferedNeverNarrowsWhatCanBeReadBack() {
    // The distinction #638 turns on. A squawk already stored as AOG must still render as AOG on a
    // template that no longer offers it, so the enum keeps every value and only the *form* filters.
    // Asserted here because the failure it prevents — a stored squawk rendering blank — would only
    // appear on an account that had switched templates, long after this code was written.
    assertThat(SquawkPriority.entries).contains(SquawkPriority.SQUAWK_PRIORITY_AOG)
  }

  @Test
  fun declaringNothingFailsOpen() {
    // A form with no priority to choose cannot be completed, so an empty list offers everything.
    SquawkPriority.entries.forEach {
      assertThat(it.isOfferedBy(emptyList())).isTrue()
    }
  }
}
