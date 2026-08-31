package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
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

  private fun holder() =
    CurrentThingTemplate(BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE))

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
    )
      .inOrder()
  }

  @Test
  fun theAirplaneSetAndTheFailOpenDefaultAgreeOnEveryFlag() {
    // **Why Phase 2 is invisible whichever path a gate takes** (#660).
    //
    // Every gate reads capabilities from one of two places: the selected thing's template, or the
    // fail-open default when none applies. If those two disagreed on any boolean, the same screen
    // would render differently depending on whether the fleet had loaded — a race nobody would
    // reproduce reliably and everybody would blame on something else.
    //
    // The list flags are deliberately not compared: ALL_ENABLED declares no sections and no
    // priorities, because a default has no template to take an order from, and each gate's own
    // "fail-open removes nothing" test covers what that empty list means where it is read.
    val airplane = AirplaneTemplate.AIRPLANE_CAPABILITIES
    val default = CurrentThingTemplate.ALL_ENABLED

    assertThat(default.components).isEqualTo(airplane.components)
    assertThat(default.meters).isEqualTo(airplane.meters)
    assertThat(default.compliance).isEqualTo(airplane.compliance)
    assertThat(default.technicians).isEqualTo(airplane.technicians)
    assertThat(default.technician_certificates).isEqualTo(airplane.technician_certificates)
    assertThat(default.component_serial_prompt).isEqualTo(airplane.component_serial_prompt)
  }

  @Test
  fun capabilitiesAreUsableImmediatelyAfterConstruction() {
    // The other failure #660 names: a capability read *before the registry resolves*. There is no
    // such window — the registry is baked into the build, so the holder resolves in its constructor
    // and the first read already has the airplane set rather than an empty one.
    assertThat(holder().capabilities.value.sections).isNotEmpty()
    assertThat(holder().template.value).isNotNull()
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
