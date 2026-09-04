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
    val holder = holder()
    // Selecting is now required to get a template's own capabilities. Until the six presets of
    // #721-#723 shipped, one preset was the only possible answer and construction alone was
    // enough; `default` retires itself the moment a second preset exists, exactly as its KDoc
    // said it would.
    holder.set(AirplaneTemplate.TEMPLATE)
    val capabilities = holder.capabilities.value

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
    assertThat(default.component_serial_prompt).isEqualTo(airplane.component_serial_prompt)
  }

  @Test
  fun capabilitiesAreUsableImmediatelyAfterConstruction() {
    // The failure #660 names: a capability read *before* anything is selected must not remove UI.
    // It fails open rather than resolving to a preset — with seven presets there is no single right
    // answer, so the guarantee is that the first read is usable, not that it is anyone's template.
    assertThat(holder().capabilities.value).isEqualTo(CurrentThingTemplate.ALL_ENABLED)
    assertThat(holder().capabilities.value.components).isTrue()
    assertThat(holder().capabilities.value.meters).isTrue()
  }

  @Test
  fun withNoSelectionTheWordsAreGenericRatherThanAnyOnePresets() {
    // The user-visible consequence of shipping the six presets (#721-#723), and a deliberate one:
    // account-level screens — settings, redeem, invite — used to read "aircraft" because airplane
    // was the only preset. On a mixed account no template's word is right, so they now read the
    // generic lexicon. Picking one arbitrarily would caption a homeowner's screen in aviation.
    val holder = holder()

    assertThat(holder.template.value).isNull()
    assertThat(holder.lexicon.value).isEqualTo(GenericLexicon.LEXICON)
    assertThat(holder.templateId).isEqualTo(CurrentThingTemplate.UNKNOWN_TEMPLATE_ID)
  }

  @Test
  fun clearReturnsToTheNoSelectionDefault() {
    // An empty fleet is "no template" now that more than one preset exists — clear() must restore
    // that state rather than leaving the last selection's words behind.
    val holder = holder()
    holder.set(AirplaneTemplate.TEMPLATE)
    holder.clear()

    assertThat(holder.template.value).isNull()
    assertThat(holder.lexicon.value).isEqualTo(GenericLexicon.LEXICON)
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
