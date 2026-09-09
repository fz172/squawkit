package dev.fanfly.wingslog.feature.search.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.ComponentType
import org.junit.Test

/** Which component chips are worth offering (see [visibleComponentOptions]). */
class VisibleComponentOptionsTest {

  private val options = listOf(
    ComponentType.COMPONENT_AIRFRAME,
    ComponentType.COMPONENT_ENGINE,
    ComponentType.COMPONENT_PROPELLER,
    ComponentType.COMPONENT_UNKNOWN,
  )

  private fun counts(vararg pairs: Pair<ComponentType, Int>): (ComponentType) -> Int {
    val map = pairs.toMap()
    return { map[it] ?: 0 }
  }

  @Test
  fun anOptionNothingWasFiledAgainstIsDropped() {
    // The case this exists for: nothing is ever filed as "Not recorded", so the chip only ever
    // meant "show nothing".
    val visible = visibleComponentOptions(
      options,
      selected = emptySet(),
      count = counts(
        ComponentType.COMPONENT_AIRFRAME to 18,
        ComponentType.COMPONENT_ENGINE to 12,
        ComponentType.COMPONENT_PROPELLER to 0,
        ComponentType.COMPONENT_UNKNOWN to 0,
      ),
    )

    assertThat(visible).containsExactly(
      ComponentType.COMPONENT_AIRFRAME,
      ComponentType.COMPONENT_ENGINE,
    ).inOrder()
  }

  @Test
  fun aSelectedOptionSurvivesItsOwnZero() {
    // Its count is zero *because* it is selected. Hiding it would strand a filter with no way
    // left to undo it.
    val visible = visibleComponentOptions(
      options,
      selected = setOf(ComponentType.COMPONENT_PROPELLER),
      count = counts(ComponentType.COMPONENT_AIRFRAME to 18),
    )

    assertThat(visible).containsExactly(
      ComponentType.COMPONENT_AIRFRAME,
      ComponentType.COMPONENT_PROPELLER,
    )
  }

  @Test
  fun withoutCountsNothingIsHidden() {
    // A caller that cannot count has not said an option is empty, only that it does not know.
    assertThat(visibleComponentOptions(options, selected = emptySet(), count = null))
      .containsExactlyElementsIn(options).inOrder()
  }

  @Test
  fun everythingEmptyLeavesNothingToAsk() {
    assertThat(visibleComponentOptions(options, selected = emptySet(), count = counts()))
      .isEmpty()
  }
}
