package dev.fanfly.wingslog.feature.tasks.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.MeterRule
import org.junit.Test

/**
 * Which meter a rule schedules against (#759).
 *
 * `EngineHourRule` named its meter in its *type* rather than carrying it, which is why a car could
 * not be scheduled on mileage at all: an interval with no unit is only a number. It was retired in
 * #761; what survives it is [defaultMeterKey], because a card with no meter rule still has to put
 * a forced override somewhere.
 */
class MeterRulesTest {

  private fun task(
    component: ComponentType = ComponentType.COMPONENT_ENGINE,
    vararg rules: InspectionRule,
  ) = MaintenanceTask(id = "t", component = component, rules = rules.toList())

  @Test
  fun aMeterRuleCarriesItsOwnKey() {
    // The whole point: "every 5,000 miles" is expressible.
    val card = task(
      rules = arrayOf(
        InspectionRule(
          meter_rule = MeterRule(
            "odometer",
            5000f
          )
        )
      )
    )

    assertThat(card.meterIntervalFor(card.rules.single()))
      .isEqualTo("odometer" to 5000f)
  }

  @Test
  fun anAviationRuleReadsAsTheMeterItAlwaysMeant() {
    // The backfill converted every stored EngineHourRule into one of these, so an aviation task
    // schedules exactly as it did — the meter is simply named now rather than implied.
    val card = task(
      rules = arrayOf(InspectionRule(meter_rule = MeterRule(MeterKeys.ENGINE_HOURS, 100f)))
    )

    assertThat(card.meterIntervalFor(card.rules.single()))
      .isEqualTo(MeterKeys.ENGINE_HOURS to 100f)
  }

  @Test
  fun aTaskMayHoldSeveralMeterRulesAndEachReadsItsOwn() {
    val card = task(
      rules = arrayOf(
        InspectionRule(meter_rule = MeterRule(MeterKeys.ENGINE_HOURS, 100f)),
        InspectionRule(meter_rule = MeterRule("odometer", 5000f)),
      ),
    )

    assertThat(card.rules.mapNotNull { card.meterIntervalFor(it) })
      .containsExactly(MeterKeys.ENGINE_HOURS to 100f, "odometer" to 5000f)
      .inOrder()
  }

  @Test
  fun aRuleThatSchedulesOnSomethingElseIsNotAMeterRule() {
    val card = task(rules = arrayOf(InspectionRule(on_condition_rule = null)))

    assertThat(card.meterIntervalFor(card.rules.single())).isNull()
  }

  @Test
  fun anIntervalOfZeroIsNotARule() {
    // A blank editor writes zero; it must not read back as "due immediately".
    val meter = task(
      rules = arrayOf(
        InspectionRule(
          meter_rule = MeterRule(
            "odometer",
            0f
          )
        )
      )
    )
    assertThat(meter.meterIntervalFor(meter.rules.single())).isNull()
  }

  @Test
  fun aMeterRuleWithNoKeyFallsBackRatherThanSchedulingOnNothing() {
    val card =
      task(rules = arrayOf(InspectionRule(meter_rule = MeterRule("", 100f))))

    assertThat(card.meterIntervalFor(card.rules.single())).isNull()
  }

  // --- The forced override, keyed (#759) ---

  @Test
  fun anOverrideWithNoKeyFallsBackToWhatTheCardImplies() {
    // The key is a string, so "unset" and "empty" are indistinguishable; an aviation override is
    // what an empty one has always meant.
    val card = task().copy(force_due_meter = MeterReading("", value_ = 250.0))

    assertThat(card.forcedDueMeter()).isEqualTo(MeterKeys.ENGINE_HOURS to 250f)
  }

  @Test
  fun aKeyedOverrideWinsAndCarriesItsMeter() {
    val card = task().withForcedDueMeter("odometer", 90000f)

    assertThat(card.forcedDueMeter()).isEqualTo("odometer" to 90000f)
  }

  @Test
  fun clearingAnOverrideRemovesIt() {
    val cleared = task().withForcedDueMeter("odometer", 90000f)
      .withForcedDueMeter("odometer", null)

    assertThat(cleared.force_due_meter).isNull()
    assertThat(cleared.forcedDueMeter()).isNull()
  }

  @Test
  fun theOverrideMeterFollowsTheRulesItIsMeasuredAgainst() {
    // An override is in the same meter the schedule is, so a car's is in miles.
    assertThat(
      meterKeyFor(
        ComponentType.COMPONENT_ENGINE,
        listOf(InspectionRule(meter_rule = MeterRule("odometer", 5000f))),
      ),
    ).isEqualTo("odometer")

    // With no meter rule it is the card's own default.
    assertThat(meterKeyFor(ComponentType.COMPONENT_AIRFRAME, emptyList()))
      .isEqualTo(MeterKeys.AIRFRAME_HOURS)
  }
}
