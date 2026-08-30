package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.thing.Section
import org.junit.Test

/**
 * That the template's capabilities actually reach the composition.
 *
 * **This is the only thing that can tell working wiring from broken wiring**, because the two look
 * identical on screen. If capabilities never arrived, `Capabilities.sections` would be empty, the
 * shell's fail-open fallback would return the same four sections in the same order, and the app
 * would be pixel-for-pixel unchanged. The same holds for priorities. A gate that is indistinguishable
 * from its own absence is not a gate, so the distinction is asserted here rather than trusted.
 */
class CurrentThingTemplateTest {

  private fun holder() = CurrentThingTemplate(BakedInTemplateRegistry())

  @Test
  fun publishesTheTemplatesOwnCapabilitiesNotTheFailOpenDefault() {
    val capabilities = holder().capabilities.value

    // The distinguishing fact: ALL_ENABLED declares no sections and no priorities, because a
    // fail-open default has no template to take an order from. The airplane set declares both.
    assertThat(CurrentThingTemplate.ALL_ENABLED.sections).isEmpty()
    assertThat(capabilities.sections).isNotEmpty()

    assertThat(capabilities).isEqualTo(AirplaneTemplate.AIRPLANE_CAPABILITIES)
    assertThat(capabilities.sections).containsExactly(
      Section.SECTION_DASHBOARD,
      Section.SECTION_SQUAWKS,
      Section.SECTION_TASKS,
      Section.SECTION_LOGS,
    ).inOrder()
  }

  @Test
  fun clearRestoresTheSolePresetRatherThanEmptying() {
    // An empty fleet is not "no template" while one preset exists — the same rule the lexicon
    // follows, and the reason a technician with no thing of their own still reads "aircraft".
    val holder = holder()
    holder.clear()

    assertThat(holder.capabilities.value.sections).isNotEmpty()
    assertThat(holder.lexicon.value).isEqualTo(AirplaneTemplate.AIRPLANE_LEXICON)
  }

  @Test
  fun lexiconAndCapabilitiesNeverDescribeDifferentThings() {
    // They are published together for this reason; a mapped-Flow derivation would have let a
    // reader see one thing's words beside another's feature set.
    val holder = holder()
    holder.set(AirplaneTemplate.TEMPLATE)

    assertThat(holder.lexicon.value).isEqualTo(holder.template.value?.lexicon)
    assertThat(holder.capabilities.value).isEqualTo(holder.template.value?.capabilities)
  }
}
