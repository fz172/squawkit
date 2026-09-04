package dev.fanfly.wingslog.feature.tasks.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.StarterTask
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test

/** What an accepted starter task turns into — an ordinary card the form could have produced. */
class StarterTasksTest {

  private val now = toWireInstant(1_700_000_000L)

  @Test
  fun aCalendarIntervalBecomesATimeRuleDatedFromNow() {
    val card = StarterTask(title = "HVAC filter", description = "Every season", interval_months = 3)
      .toMaintenanceTask(CanonicalTemplates.HOME, now)

    assertThat(card.id).isEmpty()
    assertThat(card.title).isEqualTo("HVAC filter")
    assertThat(card.notes).isEqualTo("Every season")
    assertThat(card.type).isEqualTo(ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION)
    val rule = card.rules.single().time_rule
    assertThat(rule?.interval_months).isEqualTo(3)
    // Without this the due engine falls back to "today" on every read — and logs a warning.
    assertThat(rule?.creation_date).isEqualTo(now)
  }

  @Test
  fun theTimeRuleTakesTheTemplatesMonthConvention() {
    // Aviation snaps to the end of the month; everything else is due on the anniversary.
    val airplane = StarterTask(title = "Annual", interval_months = 12)
      .toMaintenanceTask(AirplaneTemplate.TEMPLATE, now)
    assertThat(airplane.rules.single().time_rule?.due_on_anniversary).isFalse()

    val anniversary = ThingTemplate(
      id = "home",
      capabilities = Capabilities(month_intervals_due_on_anniversary = true),
    )
    val home = StarterTask(title = "Flush water heater", interval_months = 12)
      .toMaintenanceTask(anniversary, now)
    assertThat(home.rules.single().time_rule?.due_on_anniversary).isTrue()
  }

  @Test
  fun aMeterIntervalBecomesAMeterRuleAndBothSurviveTogether() {
    val card = StarterTask(
      title = "Oil change",
      meter_key = "odometer",
      interval = 5000f,
      interval_months = 6,
    ).toMaintenanceTask(CanonicalTemplates.AUTOMOTIVE, now)

    assertThat(card.rules).hasSize(2)
    assertThat(card.rules.mapNotNull { it.meter_rule }.single().meter_key).isEqualTo("odometer")
    assertThat(card.rules.mapNotNull { it.meter_rule }.single().interval).isEqualTo(5000f)
    assertThat(card.rules.mapNotNull { it.time_rule }.single().interval_months).isEqualTo(6)
  }

  @Test
  fun offTheAirplaneATaskBelongsToTheThingItself() {
    // Airframe / engine / propeller is aviation's enum; a car's oil change names no component.
    val card = StarterTask(title = "Oil change", interval_months = 6)
      .toMaintenanceTask(CanonicalTemplates.AUTOMOTIVE, now)

    assertThat(card.component).isEqualTo(ComponentType.COMPONENT_UNKNOWN)
  }

  @Test
  fun onTheAirplaneTheSlotKeyPicksTheComponentAndNoSlotMeansAirframe() {
    val airplane = AirplaneTemplate.TEMPLATE
    assertThat(StarterTask(title = "Annual", interval_months = 12).toMaintenanceTask(airplane, now).component)
      .isEqualTo(ComponentType.COMPONENT_AIRFRAME)
    assertThat(
      StarterTask(title = "Oil", component_slot_key = "engine", interval_months = 4)
        .toMaintenanceTask(airplane, now).component
    ).isEqualTo(ComponentType.COMPONENT_ENGINE)
    assertThat(
      StarterTask(title = "Prop", component_slot_key = "propeller", interval_months = 60)
        .toMaintenanceTask(airplane, now).component
    ).isEqualTo(ComponentType.COMPONENT_PROPELLER)
  }

  @Test
  fun aThingWithNoDnaCountsAsAnAirplane()  {
    // A Thing migrated by the cutover carries no template and predates the pivot.
    val card = StarterTask(title = "Annual", interval_months = 12).toMaintenanceTask(null, now)
    assertThat(card.component).isEqualTo(ComponentType.COMPONENT_AIRFRAME)
  }
}
