package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.feature.tasks.datamanager.defaultMeterKey
import dev.fanfly.wingslog.feature.tasks.datamanager.withForcedDueMeter
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.ForceCompliedStatus
import dev.fanfly.wingslog.thing.ImmediateRule
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.LinkedRule
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.MeterRule
import dev.fanfly.wingslog.thing.OnConditionRule
import dev.fanfly.wingslog.thing.TimeRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant
import com.squareup.wire.Instant as WireInstant

class TaskDueManagerImplTest {

  private lateinit var clock: Clock
  private lateinit var manager: TaskDueManagerImpl

  @Before
  fun setUp() {
    clock = mockk()
    every { clock.now() } returns CURRENT_INSTANT
    manager = TaskDueManagerImpl(clock, TimeZone.UTC)
  }

  @Test
  fun monthInterval_snapsToEndOfMonth_byDefault() {
    // Aviation's convention, and what every rule stored before the flag existed means.
    val card = card(id = "c1", rules = listOf(timeRule(12)))
    val log = log(inspectionIds = listOf("c1"), timestamp = iso("2025-12-14"))

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2026, 12, 31))
  }

  @Test
  fun monthInterval_dueOnAnniversary_whenTheRuleSaysSo() {
    // A water heater flushed on the 14th is next due on the 14th (PRD §4.6).
    val card = card(id = "c1", rules = listOf(timeRule(12, dueOnAnniversary = true)))
    val log = log(inspectionIds = listOf("c1"), timestamp = iso("2025-12-14"))

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2026, 12, 14))
  }

  @Test
  fun forceComplied_advancesOnTheAnniversaryToo() {
    val card = card(
      id = "c1",
      rules = listOf(timeRule(6, creationDate = iso("2025-01-10"), dueOnAnniversary = true)),
      forceComplied = ForceCompliedStatus(complied_date = iso("2026-04-01")),
    )

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    // 10 Jan 2025 → 10 Jul 2025 → 10 Jan 2026 → 10 Jul 2026: the first cycle past 13 Apr 2026.
    assertThat(result.nextDueDate).isEqualTo(LocalDate(2026, 7, 10))
  }

  @Test
  fun oneTimeCard_withMatchingLog_returnsComplied() {
    val card = card(id = "c1", isOneTime = true, rules = listOf(timeRule(12)))
    val log = log(inspectionIds = listOf("c1"), timestamp = iso("2024-01-01"))

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.COMPLIED)
  }

  @Test
  fun oneTimeCard_withForceComplied_returnsComplied() {
    val card = card(
      id = "c1",
      isOneTime = true,
      rules = listOf(timeRule(12)),
      forceComplied = ForceCompliedStatus(complied_date = iso("2024-01-01")),
    )

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.COMPLIED)
  }

  @Test
  fun oneTimeCard_withoutLogOrForceComplied_notComplied() {
    val card = card(id = "c1", isOneTime = true, rules = listOf(timeRule(12)))

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.status).isNotEqualTo(DueStatus.COMPLIED)
  }

  @Test
  fun forcedDueDate_inPast_overdue() {
    val card = card(forceDueDate = iso("2026-03-01"))

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.OVERDUE)
    assertThat(result.nextDueDate).isEqualTo(LocalDate(2026, 3, 1))
  }

  @Test
  fun forcedDueDate_withinOneMonth_dueSoon() {
    val card = card(forceDueDate = iso("2026-05-01"))

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.DUE_SOON)
  }

  @Test
  fun forcedDueDate_farInFuture_normal() {
    val card = card(forceDueDate = iso("2027-01-01"))

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
    assertThat(result.nextDueDate).isEqualTo(LocalDate(2027, 1, 1))
  }

  @Test
  fun forcedDueEngine_belowCurrentMetric_overdue() {
    val card = card(
      component = ComponentType.COMPONENT_ENGINE,
      forceDueEngine = 80f,
    )
    val log = log(engineHour = 100.0)

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.OVERDUE)
    assertThat(result.nextDueEngine).isEqualTo(80f)
  }

  @Test
  fun forcedDueEngine_withinTenOfCurrentMetric_dueSoon() {
    val card = card(
      component = ComponentType.COMPONENT_ENGINE,
      forceDueEngine = 105f,
    )
    val log = log(engineHour = 100.0)

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.DUE_SOON)
  }

  @Test
  fun forcedDueEngine_farAboveCurrentMetric_normal() {
    val card = card(
      component = ComponentType.COMPONENT_ENGINE,
      forceDueEngine = 200f,
    )
    val log = log(engineHour = 100.0)

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
  }

  @Test
  fun timeRule_pastLogBeyondInterval_overdue() {
    val card = card(id = "c1", rules = listOf(timeRule(12)))
    val log = log(inspectionIds = listOf("c1"), timestamp = iso("2024-01-01"))

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.OVERDUE)
    assertThat(result.nextDueDate).isEqualTo(LocalDate(2025, 1, 31))
  }

  @Test
  fun timeRule_recentLog_normal() {
    val card = card(id = "c1", rules = listOf(timeRule(12)))
    val log = log(inspectionIds = listOf("c1"), timestamp = iso("2026-01-01"))

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
    assertThat(result.nextDueDate).isEqualTo(LocalDate(2027, 1, 31))
  }

  @Test
  fun timeRule_noLogs_usesCurrentDateAsBase() {
    val card = card(id = "c1", rules = listOf(timeRule(12)))

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2027, 4, 30))
    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
  }

  @Test
  fun timeRule_multipleRules_picksEarliestDate() {
    val card = card(id = "c1", rules = listOf(timeRule(12), timeRule(6)))
    val log = log(inspectionIds = listOf("c1"), timestamp = iso("2024-01-01"))

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2024, 7, 31))
  }

  @Test
  fun timeRule_yearsInterval_snapsToEndOfMonth() {
    val card = card(id = "c1", rules = listOf(timeRuleYears(6)))
    val log = log(inspectionIds = listOf("c1"), timestamp = iso("2026-07-19"))

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2032, 7, 31))
  }

  @Test
  fun aRuleOnAirframeHoursIgnoresEngineHours() {
    val card = card(
      id = "c1",
      component = ComponentType.COMPONENT_AIRFRAME,
      rules = listOf(meterRule(MeterKeys.AIRFRAME_HOURS, 5f)),
    )
    val log = log(
      inspectionIds = listOf("c1"),
      airframeTime = 500.0,
      engineHour = 9999.0,
    )

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueEngine).isEqualTo(505f)
    // NORMAL is the tell: a full 5-hour cycle is left. Reading engine hours by mistake would put
    // this 9,494 hours past due.
    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
  }

  @Test
  fun aMileageRuleGoesOverdueAgainstItsOwnMeter() {
    // The status comparison used to read whatever meter the card's *component* implied, so an
    // odometer due of 85,000 was measured against 0 airframe hours and stayed NORMAL forever.
    val card = card(id = "c1", rules = listOf(meterRule("odometer", 5000f)))
    val logs = listOf(
      log(
        id = "l1",
        inspectionIds = listOf("c1"),
        readings = listOf(MeterReading("odometer", value_ = 80000.0))
      ),
      log(
        id = "l2",
        readings = listOf(MeterReading("odometer", value_ = 86000.0))
      ),
    )

    val result = manager.computeNextDue(card, logs, listOf(card))

    assertThat(result.nextDueEngine).isEqualTo(85000f)
    assertThat(result.status).isEqualTo(DueStatus.OVERDUE)
  }

  @Test
  fun theDueSoonWindowScalesWithTheInterval() {
    // 500 miles out of 5,000 is a warning; the old fixed window was 10, which in miles is the
    // rest of the afternoon.
    val card = card(id = "c1", rules = listOf(meterRule("odometer", 5000f)))
    val logs = listOf(
      log(
        id = "l1",
        inspectionIds = listOf("c1"),
        readings = listOf(MeterReading("odometer", value_ = 80000.0))
      ),
      log(
        id = "l2",
        readings = listOf(MeterReading("odometer", value_ = 84600.0))
      ),
    )

    assertThat(manager.computeNextDue(card, logs, listOf(card)).status)
      .isEqualTo(DueStatus.DUE_SOON)
  }

  @Test
  fun aShortIntervalIsNotPermanentlyDueSoon() {
    // The fixed 10-hour window meant any rule with an interval under 10 hours reported DUE_SOON
    // from the moment it was logged.
    val card = card(id = "c1", rules = listOf(meterRule(MeterKeys.AIRFRAME_HOURS, 5f)))
    val log = log(id = "l1", inspectionIds = listOf("c1"), airframeTime = 500.0)

    assertThat(manager.computeNextDue(card, listOf(log), listOf(card)).status)
      .isEqualTo(DueStatus.NORMAL)
  }

  @Test
  fun theNearestDueWinsAcrossDifferentMeters() {
    // Two rules in different units. Comparing the raw due values picks the 105-hour inspection
    // purely because 105 < 85,000, with 50 miles left on the other. Remaining-until-due at least
    // asks how far each has to go.
    val card = card(
      id = "c1",
      rules = listOf(
        meterRule("odometer", 5000f),
        meterRule(MeterKeys.AIRFRAME_HOURS, 100f),
      ),
    )
    val logs = listOf(
      log(
        id = "l1",
        inspectionIds = listOf("c1"),
        airframeTime = 5.0,
        readings = listOf(MeterReading("odometer", value_ = 80000.0)),
      ),
      log(
        id = "l2",
        readings = listOf(MeterReading("odometer", value_ = 84950.0))
      ),
    )

    val result = manager.computeNextDue(card, logs, listOf(card))

    assertThat(result.nextDueMeterKey).isEqualTo("odometer")
    assertThat(result.nextDueEngine).isEqualTo(85000f)
  }

  @Test
  fun aRuleOnEngineHoursIgnoresAirframeTime() {
    val card = card(
      id = "c1",
      component = ComponentType.COMPONENT_ENGINE,
      rules = listOf(meterRule(MeterKeys.ENGINE_HOURS, 10f)),
    )
    val log = log(
      inspectionIds = listOf("c1"),
      airframeTime = 9999.0,
      engineHour = 50.0,
    )

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueEngine).isEqualTo(60f)
  }

  @Test
  fun onConditionRule_applied_setsFlagAndKeepsNormal() {
    val card = card(
      rules = listOf(InspectionRule(on_condition_rule = OnConditionRule())),
    )

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.isOnCondition).isTrue()
    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
    assertThat(result.nextDueDate).isNull()
    assertThat(result.nextDueEngine).isNull()
  }

  @Test
  fun immediateRule_applied_overdue() {
    val card =
      card(rules = listOf(InspectionRule(immediate_rule = ImmediateRule())))

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.isImmediate).isTrue()
    assertThat(result.status).isEqualTo(DueStatus.OVERDUE)
  }

  @Test
  fun linkedRule_applied_inheritsParentDueDate() {
    val parent = card(id = "parent", rules = listOf(timeRule(12)))
    val child = card(
      id = "child",
      rules = listOf(
        InspectionRule(linked_rule = LinkedRule(parent_inspection_id = "parent")),
      ),
    )

    val result =
      manager.computeNextDue(child, emptyList(), listOf(parent, child))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2027, 4, 30))
    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
  }

  @Test
  fun linkedRule_cycleDetected_returnsNormal() {
    val a = card(
      id = "a",
      rules = listOf(
        InspectionRule(linked_rule = LinkedRule(parent_inspection_id = "b")),
      ),
    )
    val b = card(
      id = "b",
      rules = listOf(
        InspectionRule(linked_rule = LinkedRule(parent_inspection_id = "a")),
      ),
    )

    val result = manager.computeNextDue(a, emptyList(), listOf(a, b))

    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
    assertThat(result.nextDueDate).isNull()
    assertThat(result.nextDueEngine).isNull()
  }

  @Test
  fun timeRule_noLogs_withCreationDate_usesCreationDateAsBase() {
    // No matching logs; creation_date set to 2024-01-01 with a 12-month interval.
    // Expected next due: 2025-01-31 (EOM), which is before CURRENT_INSTANT (2026-04-13) → OVERDUE.
    val card = card(
      id = "c1",
      rules = listOf(timeRule(12, creationDate = iso("2024-01-01")))
    )

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2025, 1, 31))
    assertThat(result.status).isEqualTo(DueStatus.OVERDUE)
  }

  @Test
  fun timeRule_unrelatedLogAdded_doesNotMoveDueDate() {
    // Regression: an unrelated maintenance log (no matching inspection ID) must not
    // shift the due date of an unrelated time-rule inspection.
    // Before the fix the implementation used allLogs' earliest date as the base,
    // so adding any log would silently move the due date.
    val card = card(
      id = "c1",
      rules = listOf(timeRule(12, creationDate = iso("2024-01-01")))
    )
    val unrelatedLog =
      log(
        id = "log-unrelated",
        timestamp = iso("2024-06-01"),
        inspectionIds = emptyList()
      )

    val result =
      manager.computeNextDue(card, listOf(unrelatedLog), listOf(card))

    // Due date must be anchored to creation_date (2024-01-01) + 12 months = 2025-01-31 (EOM),
    // NOT to the unrelated log date (2024-06-01) + 12 months = 2025-06-30.
    assertThat(result.nextDueDate).isEqualTo(LocalDate(2025, 1, 31))
  }

  @Test
  fun timeRule_matchingLog_overridesCreationDate() {
    // When a matching log exists the implementation must use that log's date as the
    // base, regardless of what creation_date says.
    val card = card(
      id = "c1",
      rules = listOf(timeRule(12, creationDate = iso("2024-01-01")))
    )
    val matchingLog =
      log(
        id = "log-match",
        inspectionIds = listOf("c1"),
        timestamp = iso("2026-01-01")
      )

    val result = manager.computeNextDue(card, listOf(matchingLog), listOf(card))

    // Base = log date 2026-01-01 + 12 months = 2027-01-31 (EOM), not creation_date + 12 months.
    assertThat(result.nextDueDate).isEqualTo(LocalDate(2027, 1, 31))
    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
  }

  @Test
  fun forceComplied_newerThanLatestLog_advancesToNextCycle() {
    val card = card(
      id = "c1",
      rules = listOf(timeRule(12)),
      forceComplied = ForceCompliedStatus(complied_date = iso("2024-06-01")),
    )
    val log = log(inspectionIds = listOf("c1"), timestamp = iso("2024-05-01"))

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2026, 5, 31))
  }

  @Test
  fun forceComplied_multiCycleTimeOverdue_advancesPastToday() {
    // 12-month rule, creation 2019-01-01, never logged → first due 2020-01-31 (EOM).
    // Force-complied today (2026-04-13): must advance past currentDate, not just one cycle.
    // 2020-01-31 + 12mo × 7 iterations = 2027-01-31 (first date > 2026-04-13).
    val card = card(
      id = "c1",
      rules = listOf(timeRule(12, creationDate = iso("2019-01-01"))),
      forceComplied = ForceCompliedStatus(complied_date = iso("2026-04-13")),
    )

    val result = manager.computeNextDue(card, emptyList(), listOf(card))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2027, 1, 31))
    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
  }

  @Test
  fun forceComplied_multiCycleEngineOverdue_advancesPastCurrentMetric() {
    // 50-hour rule; last compliance at 100h → nextDue = 150h.
    // Current engine at 400h — 5 cycles overdue.
    // After force-comply: must advance until nextDue > 400h.
    // 150 → 200 → 250 → 300 → 350 → 400 → 450 (first value > 400).
    val card = card(
      id = "c1",
      component = ComponentType.COMPONENT_ENGINE,
      rules = listOf(meterRule(MeterKeys.ENGINE_HOURS, 50f)),
      forceComplied = ForceCompliedStatus(complied_date = iso("2026-04-13")),
    )
    val complianceLog = log(inspectionIds = listOf("c1"), engineHour = 100.0)
    val currentHourLog = log(id = "log2", engineHour = 400.0)

    val result = manager.computeNextDue(
      card,
      listOf(complianceLog, currentHourLog),
      listOf(card)
    )

    assertThat(result.nextDueEngine).isEqualTo(450f)
    assertThat(result.status).isEqualTo(DueStatus.NORMAL)
  }

  @Test
  fun forceComplied_olderThanLatestLog_ignored() {
    val card = card(
      id = "c1",
      rules = listOf(timeRule(12)),
      forceComplied = ForceCompliedStatus(complied_date = iso("2024-01-01")),
    )
    val log = log(inspectionIds = listOf("c1"), timestamp = iso("2024-05-01"))

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueDate).isEqualTo(LocalDate(2025, 5, 31))
  }

  // --- The due value carries the meter it is measured in (#759) ---

  @Test
  fun aMeterRuleReportsItsOwnKey() {
    // Without this the card fell back to hours, so a car scheduled every 5,000 miles rendered
    // "5000.0 HRS" while the editor that created it said "mi".
    val card = card(
      id = "c1",
      component = ComponentType.COMPONENT_ENGINE,
      rules = listOf(InspectionRule(meter_rule = MeterRule("odometer", 5000f))),
    )
    // Referencing the card is what makes this its last completion — the interval counts from there.
    val log = log(
      id = "l1",
      inspectionIds = listOf("c1"),
      readings = listOf(MeterReading("odometer", value_ = 80000.0)),
    )

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueMeterKey).isEqualTo("odometer")
    assertThat(result.nextDueEngine).isEqualTo(85000f)
  }

  @Test
  fun anAviationRuleReportsTheMeterItNames() {
    // An airframe card tracked airframe time, everything else engine hours. Preserved, so an
    // aviation task renders exactly as before.
    val airframe = card(
      id = "c1",
      component = ComponentType.COMPONENT_AIRFRAME,
      rules = listOf(meterRule(MeterKeys.AIRFRAME_HOURS, 50f)),
    )

    val result = manager.computeNextDue(
      airframe,
      listOf(
        log(
          id = "l1",
          inspectionIds = listOf("c1"),
          airframeTime = 100.0
        )
      ),
      listOf(airframe),
    )

    assertThat(result.nextDueMeterKey).isEqualTo(MeterKeys.AIRFRAME_HOURS)
    assertThat(result.nextDueEngine).isEqualTo(150f)
  }

  private fun card(
    id: String = "card",
    isOneTime: Boolean = false,
    rules: List<InspectionRule> = emptyList(),
    component: ComponentType = ComponentType.COMPONENT_AIRFRAME,
    forceDueDate: WireInstant? = null,
    forceDueEngine: Float = 0f,
    forceComplied: ForceCompliedStatus? = null,
  ): MaintenanceTask = MaintenanceTask(
    id = id,
    component = component,
    rules = rules,
    force_due_date = forceDueDate,
    is_one_time = isOneTime,
    force_complied_status = forceComplied,
  ).let { task ->
    // The override lands in whichever meter the card implies, which is what the retired
    // `force_due_engine_hour` always meant (#761).
    if (forceDueEngine > 0f) {
      task.withForcedDueMeter(task.defaultMeterKey(), forceDueEngine)
    } else {
      task
    }
  }

  private fun log(
    id: String = "log",
    timestamp: WireInstant? = null,
    airframeTime: Double = 0.0,
    engineHour: Double = 0.0,
    inspectionIds: List<String> = emptyList(),
    readings: List<MeterReading> = emptyList(),
  ): MaintenanceLog = MaintenanceLog(
    id = id,
    timestamp = timestamp,
    inspection_ids = inspectionIds,
    // The aviation parameters stay, as the readings they always described — a fixture saying
    // "a log with 500 airframe hours" reads better than one spelling out a MeterReading (#761).
    readings = readings +
      listOfNotNull(
        airframeTime.takeIf { it > 0.0 }
          ?.let { MeterReading(MeterKeys.AIRFRAME_HOURS, value_ = it) },
        engineHour.takeIf { it > 0.0 }
          ?.let { MeterReading(MeterKeys.ENGINE_HOURS, value_ = it) },
      ),
  )

  private fun timeRule(
    months: Int,
    creationDate: WireInstant? = null,
    dueOnAnniversary: Boolean = false,
  ): InspectionRule =
    InspectionRule(
      time_rule = TimeRule(
        interval_months = months,
        creation_date = creationDate,
        due_on_anniversary = dueOnAnniversary,
      )
    )

  private fun timeRuleYears(
    years: Int,
    creationDate: WireInstant? = null
  ): InspectionRule =
    InspectionRule(
      time_rule = TimeRule(
        interval_years = years,
        creation_date = creationDate
      )
    )

  private fun meterRule(key: String, interval: Float): InspectionRule =
    InspectionRule(meter_rule = MeterRule(meter_key = key, interval = interval))

  private fun iso(date: String): WireInstant =
    WireInstant.ofEpochSecond(Instant.parse("${date}T00:00:00Z").epochSeconds)

  companion object {
    private val CURRENT_INSTANT: Instant = Instant.parse("2026-04-13T00:00:00Z")
  }
}
