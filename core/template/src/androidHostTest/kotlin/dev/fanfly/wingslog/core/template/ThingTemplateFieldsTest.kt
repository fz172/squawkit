package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.ThingTemplate
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
  fun onTheAirplaneTheComponentPicksTheMeter() {
    // An engine task counts engine hours, a prop task prop hours; the airframe owns the meter no
    // slot claims. "Airframe Time" on an engine task was the label the form used to show.
    assertThat(airplane.meterForComponent(ComponentType.COMPONENT_ENGINE)?.key)
      .isEqualTo(MeterKeys.ENGINE_HOURS)
    assertThat(airplane.meterForComponent(ComponentType.COMPONENT_PROPELLER)?.key)
      .isEqualTo(MeterKeys.PROP_HOURS)
    assertThat(airplane.meterForComponent(ComponentType.COMPONENT_AIRFRAME)?.key)
      .isEqualTo(MeterKeys.AIRFRAME_HOURS)
  }

  @Test
  fun offTheAirplaneTheComponentIsIgnoredAndTheFirstMeterWins() {
    // The enum is aviation's; a car's task is filed against the car and counts its odometer.
    assertThat(CanonicalTemplates.AUTOMOTIVE.meterForComponent(ComponentType.COMPONENT_ENGINE)?.key)
      .isEqualTo(MeterKeys.ODOMETER)
    assertThat(CanonicalTemplates.HOME.meterForComponent(ComponentType.COMPONENT_AIRFRAME)).isNull()
  }

  @Test
  fun aMeterLabelComesFromTheTemplate() {
    assertThat(airplane.meterLabel(MeterKeys.AIRFRAME_HOURS, ifAbsent = "x"))
      .isEqualTo("Airframe Time")
    assertThat(
      airplane.meterLabelWithUnit(
        MeterKeys.AIRFRAME_HOURS,
        ifAbsent = "x"
      )
    )
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
    assertThat(airplane.slot("propeller")?.label).isEqualTo("Propeller")
    assertThat(airplane.slot("blade")?.label).isEqualTo("Blade")
  }

  @Test
  fun onlyTheAeroplaneIsDescribedByComponentType() {
    // The gate on the task and log forms' component picker. Its three options ARE `ComponentType`,
    // which PRD §6 freezes to aviation — so a boat's propulsion, a car's tyres and a house's
    // nothing-at-all cannot be named by it, and those presets get no picker (#732).
    assertThat(airplane.usesComponentTypes).isTrue()
    assertThat(CanonicalTemplates.BOAT.usesComponentTypes).isFalse()
    assertThat(CanonicalTemplates.AUTOMOTIVE.usesComponentTypes).isFalse()
    assertThat(CanonicalTemplates.BIKE.usesComponentTypes).isFalse()
    assertThat(CanonicalTemplates.HOME.usesComponentTypes).isFalse()
    assertThat(CanonicalTemplates.CUSTOM.usesComponentTypes).isFalse()
  }

  @Test
  fun aThingWithNoTemplateOfItsOwnCountsAsAnAeroplane() {
    // A template reaches the composition from the selected Thing's DNA, and a Thing migrated by
    // the cutover carries none — it predates the pivot, so it can only be an aeroplane. Reading
    // null as "not aviation" would take the picker away from exactly the accounts that have
    // always had it.
    assertThat((null as ThingTemplate?).usesComponentTypes).isTrue()
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
    assertThat(airplane.slotSerialLabel(SlotKeys.PROPELLER, ifAbsent = "x"))
      .isEqualTo("Propeller Serial")
  }

  @Test
  fun anUndeclaredKeyFallsBackRatherThanRenderingBlank() {
    // These call sites are aviation-shaped screens that #729 and #730 replace. Until then a preset
    // declaring none of these keys still reaches them, and a blank label is worse than the shipped
    // string.
    assertThat(
      CanonicalTemplates.HOME.meterLabel(
        MeterKeys.ENGINE_HOURS,
        ifAbsent = "Engine Time"
      )
    )
      .isEqualTo("Engine Time")
    assertThat(
      CanonicalTemplates.CUSTOM.slotLabel(
        SlotKeys.ENGINE,
        ifAbsent = "Engine"
      )
    )
      .isEqualTo("Engine")
  }

  @Test
  fun everyPresetNamesItsOwnMeterSet() {
    // The log form's tab said "Hours" for all of them — aviation's word on a car's odometer.
    assertThat(AirplaneTemplate.TEMPLATE.metersLabel(ifAbsent = "Hours")).isEqualTo(
      "Hours"
    )
    assertThat(CanonicalTemplates.AUTOMOTIVE.metersLabel(ifAbsent = "Hours"))
      .isEqualTo("Odometer")
    // A distance and ride hours: the pair needs a word neither of them supplies.
    assertThat(CanonicalTemplates.BIKE.metersLabel(ifAbsent = "Hours")).isEqualTo(
      "Readings"
    )
    assertThat(CanonicalTemplates.BOAT.metersLabel(ifAbsent = "Hours")).isEqualTo(
      "Hours"
    )
  }

  @Test
  fun aTemplateDeclaringNoMetersLabelFallsBackToItsOnlyMeter() {
    // DNA written before `meters_label` existed still reaches the form, and a preset with one
    // meter is named perfectly well by that meter.
    val oneMeter = CanonicalTemplates.AUTOMOTIVE.copy(meters_label = "")
    assertThat(oneMeter.metersLabel(ifAbsent = "Hours")).isEqualTo("Odometer")

    // Several meters and no declared word: only then does the caller's own string stand.
    val several = AirplaneTemplate.TEMPLATE.copy(meters_label = "")
    assertThat(several.metersLabel(ifAbsent = "Hours")).isEqualTo("Hours")
    assertThat(CanonicalTemplates.HOME.metersLabel(ifAbsent = "Hours")).isEqualTo(
      "Hours"
    )
  }

  @Test
  fun noTemplateAtAllStillRendersALabel() {
    // An account-level screen has no template selected and must still caption its fields.
    val none: ThingTemplate? = null

    assertThat(
      none.meterLabel(
        MeterKeys.AIRFRAME_HOURS,
        ifAbsent = "Airframe Time"
      )
    )
      .isEqualTo("Airframe Time")
    assertThat(none.slot("engine")).isNull()
    assertThat(none.specField(SpecKeys.MAKE)).isNull()
  }
}
