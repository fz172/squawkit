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

  // --- The forced override, keyed (#759) ---

  @Test
  fun anOverrideSetBeforeTheKeyedFieldExistedStillReads() {
    // Every override in the field is in the legacy float. It meant whichever meter the card's
    // component implied, which is what the fallback preserves.
    val card = task().copy(force_due_engine_hour = 250f)

    assertThat(card.forcedDueMeter()).isEqualTo(MeterKeys.ENGINE_HOURS to 250f)
  }

  @Test
  fun aKeyedOverrideWinsAndCarriesItsMeter() {
    val card = task().withForcedDueMeter("odometer", 90000f)

    assertThat(card.forcedDueMeter()).isEqualTo("odometer" to 90000f)
  }

  @Test
  fun settingAnOverrideWritesBothFields() {
    // The legacy float is kept in step so a client that predates `force_due_meter` still sees the
    // override rather than losing it — the same bargain the overview's aviation fields make.
    val card = task().withForcedDueMeter("odometer", 90000f)

    assertThat(card.force_due_meter?.meter_key).isEqualTo("odometer")
    assertThat(card.force_due_engine_hour).isEqualTo(90000f)
  }

  @Test
  fun clearingAnOverrideClearsBoth() {
    // Zeroing only the keyed one would leave an old build showing an override the user removed.
    val cleared = task().withForcedDueMeter("odometer", 90000f)
      .withForcedDueMeter("odometer", null)

    assertThat(cleared.force_due_meter).isNull()
    assertThat(cleared.force_due_engine_hour).isEqualTo(0f)
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

    // With no meter rule it is whatever EngineHourRule always meant.
    assertThat(meterKeyFor(ComponentType.COMPONENT_AIRFRAME, emptyList()))
      .isEqualTo(MeterKeys.AIRFRAME_HOURS)
  }
}
