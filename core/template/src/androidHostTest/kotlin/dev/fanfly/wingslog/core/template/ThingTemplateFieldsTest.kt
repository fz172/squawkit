package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import org.junit.Test

/**
 * Reading a template's declared fields instead of a string resource (#703).
 *
 * The six strings this replaces read correctly for aviation and wrongly for everything else — a
 * bike's dashboard said "Airframe Time" because the label was fixed and only the number came from
 * data.
 */
class ThingTemplateFieldsTest {

  private val airplane = AirplaneTemplate.TEMPLATE

  @Test
  fun aMeterLabelComesFromTheTemplate() {
    assertThat(airplane.meterLabel(MeterKeys.AIRFRAME_HOURS, ifAbsent = "x"))
      .isEqualTo("Airframe Time")
    assertThat(airplane.meterLabelWithUnit(MeterKeys.AIRFRAME_HOURS, ifAbsent = "x"))
      .isEqualTo("Airframe Time (hrs)")
  }

  @Test
  fun automotiveReadsItsOwnMeterRatherThanTheAviationOne() {
    // The bug in one assertion: the same screen, a different template, a different word.
    val automotive = CanonicalTemplates.AUTOMOTIVE

    assertThat(automotive.meter("odometer")?.label).isEqualTo("Odometer")
    assertThat(automotive.meter("odometer")?.unit_label).isEqualTo("mi")
    assertThat(automotive.meter(MeterKeys.AIRFRAME_HOURS)).isNull()
  }

  @Test
  fun aNestedSlotIsFound() {
    // Slots nest — an engine lives under the airframe — so a top-level scan would miss every
    // component a log actually attaches to.
    assertThat(airplane.slot("engine")?.label).isEqualTo("Engine")
    assertThat(airplane.slot("blade")?.label).isEqualTo("Blade")
    assertThat(airplane.slot(SlotKeys.AIRFRAME)?.label).isEqualTo("Airframe")
  }

  @Test
  fun aSpecLabelComesFromTheTemplate() {
    assertThat(airplane.specLabel(SpecKeys.TAIL_NUMBER, ifAbsent = "x"))
      .isEqualTo("Tail Number")
    // A home declares no tail number, which is the whole argument against a universal core.
    assertThat(CanonicalTemplates.HOME.specField(SpecKeys.TAIL_NUMBER)).isNull()
    assertThat(CanonicalTemplates.HOME.specLabel("address", ifAbsent = "x"))
      .isEqualTo("Address")
  }

  @Test
  fun theSerialLabelIsComposedFromTheSlotName() {
    assertThat(airplane.slotSerialLabel(SlotKeys.AIRFRAME, ifAbsent = "x"))
      .isEqualTo("Airframe Serial")
  }

  @Test
  fun anUndeclaredKeyFallsBackRatherThanRenderingBlank() {
    // These call sites are aviation-shaped screens that #729 and #730 replace. Until then a preset
    // declaring none of these keys still reaches them, and a blank label is worse than the shipped
    // string.
    assertThat(CanonicalTemplates.HOME.meterLabel(MeterKeys.ENGINE_HOURS, ifAbsent = "Engine Time"))
      .isEqualTo("Engine Time")
    assertThat(CanonicalTemplates.CUSTOM.slotLabel(SlotKeys.AIRFRAME, ifAbsent = "Airframe"))
      .isEqualTo("Airframe")
  }

  @Test
  fun noTemplateAtAllStillRendersALabel() {
    // An account-level screen has no template selected and must still caption its fields.
    val none: dev.fanfly.wingslog.thing.ThingTemplate? = null

    assertThat(none.meterLabel(MeterKeys.AIRFRAME_HOURS, ifAbsent = "Airframe Time"))
      .isEqualTo("Airframe Time")
    assertThat(none.slot("engine")).isNull()
    assertThat(none.specField(SpecKeys.MAKE)).isNull()
  }
}
