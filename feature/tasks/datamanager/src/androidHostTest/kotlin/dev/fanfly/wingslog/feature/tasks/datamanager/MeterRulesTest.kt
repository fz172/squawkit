package dev.fanfly.wingslog.feature.tasks.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.EngineHourRule
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterRule
import org.junit.Test

/**
 * Which meter a rule schedules against (#759).
 *
 * `EngineHourRule` named its meter in its *type* rather than carrying it, so which one it meant
 * came from the card's component. That is why a car could not be scheduled on mileage at all: an
 * interval with no unit is only a number.
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
  fun anEngineHourRuleStillMeansWhatItAlwaysDid() {
    // Every aviation task in the field uses one. A task that silently stops coming due is worse
    // than one that cannot be created, so these keep computing untouched.
    val engine =
      task(rules = arrayOf(InspectionRule(engine_hour_rule = EngineHourRule(100f))))
    assertThat(engine.meterIntervalFor(engine.rules.single()))
      .isEqualTo(MeterKeys.ENGINE_HOURS to 100f)

    // An airframe card tracked airframe time rather than engine hours, which the default preserves.
    val airframe = task(
      component = ComponentType.COMPONENT_AIRFRAME,
      rules = arrayOf(InspectionRule(engine_hour_rule = EngineHourRule(50f))),
    )
    assertThat(airframe.meterIntervalFor(airframe.rules.single()))
      .isEqualTo(MeterKeys.AIRFRAME_HOURS to 50f)
  }

  @Test
  fun aTaskMayHoldBothRuleKindsAndTheKeyedOneIsRead() {
    // Not on one rule — `InspectionRule` is a oneof, so setting both there is impossible by
    // construction. A task carrying both is what a partial migration looks like: the old rule left
    // in place beside the new one.
    val card = task(
      rules = arrayOf(
        InspectionRule(engine_hour_rule = EngineHourRule(100f)),
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

    val engine =
      task(rules = arrayOf(InspectionRule(engine_hour_rule = EngineHourRule(0f))))
    assertThat(engine.meterIntervalFor(engine.rules.single())).isNull()
  }

  @Test
  fun aMeterRuleWithNoKeyFallsBackRatherThanSchedulingOnNothing() {
    val card =
      task(rules = arrayOf(InspectionRule(meter_rule = MeterRule("", 100f))))

    assertThat(card.meterIntervalFor(card.rules.single())).isNull()
  }
}
