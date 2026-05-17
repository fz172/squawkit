package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import com.google.common.truth.Truth.assertThat
import com.squareup.wire.Instant as WireInstant
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.ForceCompliedStatus
import dev.fanfly.wingslog.aircraft.ImmediateRule
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.LinkedRule
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.OnConditionRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test

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
  fun engineRule_airframeComponent_tracksAirframeTime() {
    val card = card(
      id = "c1",
      component = ComponentType.COMPONENT_AIRFRAME,
      rules = listOf(engineRule(5f)),
    )
    val log = log(
      inspectionIds = listOf("c1"),
      airframeTime = 500.0,
      engineHour = 9999.0,
    )

    val result = manager.computeNextDue(card, listOf(log), listOf(card))

    assertThat(result.nextDueEngine).isEqualTo(505f)
    assertThat(result.status).isEqualTo(DueStatus.DUE_SOON)
  }

  @Test
  fun engineRule_engineComponent_tracksEngineHour() {
    val card = card(
      id = "c1",
      component = ComponentType.COMPONENT_ENGINE,
      rules = listOf(engineRule(10f)),
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
    val card = card(rules = listOf(InspectionRule(immediate_rule = ImmediateRule())))

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

    val result = manager.computeNextDue(child, emptyList(), listOf(parent, child))

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
    val card = card(id = "c1", rules = listOf(timeRule(12, creationDate = iso("2024-01-01"))))

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
    val card = card(id = "c1", rules = listOf(timeRule(12, creationDate = iso("2024-01-01"))))
    val unrelatedLog =
      log(id = "log-unrelated", timestamp = iso("2024-06-01"), inspectionIds = emptyList())

    val result = manager.computeNextDue(card, listOf(unrelatedLog), listOf(card))

    // Due date must be anchored to creation_date (2024-01-01) + 12 months = 2025-01-31 (EOM),
    // NOT to the unrelated log date (2024-06-01) + 12 months = 2025-06-30.
    assertThat(result.nextDueDate).isEqualTo(LocalDate(2025, 1, 31))
  }

  @Test
  fun timeRule_matchingLog_overridesCreationDate() {
    // When a matching log exists the implementation must use that log's date as the
    // base, regardless of what creation_date says.
    val card = card(id = "c1", rules = listOf(timeRule(12, creationDate = iso("2024-01-01"))))
    val matchingLog =
      log(id = "log-match", inspectionIds = listOf("c1"), timestamp = iso("2026-01-01"))

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
      rules = listOf(engineRule(50f)),
      forceComplied = ForceCompliedStatus(complied_date = iso("2026-04-13")),
    )
    val complianceLog = log(inspectionIds = listOf("c1"), engineHour = 100.0)
    val currentHourLog = log(id = "log2", engineHour = 400.0)

    val result = manager.computeNextDue(card, listOf(complianceLog, currentHourLog), listOf(card))

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
    force_due_engine_hour = forceDueEngine,
    is_one_time = isOneTime,
    force_complied_status = forceComplied,
  )

  private fun log(
    id: String = "log",
    timestamp: WireInstant? = null,
    airframeTime: Double = 0.0,
    engineHour: Double = 0.0,
    inspectionIds: List<String> = emptyList(),
  ): MaintenanceLog = MaintenanceLog(
    id = id,
    timestamp = timestamp,
    airframe_time = airframeTime,
    engine_hour = engineHour,
    inspection_ids = inspectionIds,
  )

  private fun timeRule(months: Int, creationDate: WireInstant? = null): InspectionRule =
    InspectionRule(time_rule = TimeRule(interval_months = months, creation_date = creationDate))

  private fun engineRule(hours: Float): InspectionRule =
    InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = hours))

  private fun iso(date: String): WireInstant =
    WireInstant.ofEpochSecond(Instant.parse("${date}T00:00:00Z").epochSeconds)

  companion object {
    private val CURRENT_INSTANT: Instant = Instant.parse("2026-04-13T00:00:00Z")
  }
}
