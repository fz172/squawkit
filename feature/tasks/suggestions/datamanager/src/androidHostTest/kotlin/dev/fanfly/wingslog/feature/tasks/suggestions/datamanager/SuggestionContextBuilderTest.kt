package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.rpc.suggesttasks.LogSummary
import dev.fanfly.wingslog.task.ComplianceType
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.StarterTask
import dev.fanfly.wingslog.task.TimeRule
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.Technician
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Test

class SuggestionContextBuilderTest {

  private val template = ThingTemplate(
    id = "airplane",
    version = 13,
    spec_fields = listOf(
      SpecField(key = "make"),
      SpecField(key = "model"),
      SpecField(key = "tail_number", is_identifier = true),
    ),
    component_slots = listOf(
      ComponentSlot(
        slot_key = "engine",
        spec_fields = listOf(SpecField(key = "displacement"), SpecField(key = "engine_serial", is_identifier = true)),
        children = listOf(ComponentSlot(slot_key = "propeller")),
      ),
    ),
    meters = listOf(
      MeterDef(key = "airframe_hours", unit_label = "hrs"),
      MeterDef(key = "engine_hours", unit_label = "hrs", component_slot_key = "engine"),
      MeterDef(key = "hobbs", unit_label = "hrs"),
    ),
    starter_tasks = listOf(StarterTask(title = "Annual", interval_months = 12)),
  )

  private val thing = Thing(
    id = THING,
    template = template,
    spec = listOf(
      Spec(key = "make", value_ = "Sling"),
      Spec(key = "model", value_ = "TSi"),
      Spec(key = "tail_number", value_ = "N123AB"),
      Spec(key = "custom_1", value_ = "Hangar 7", label = "Where it lives"),
    ),
    components = listOf(
      Component(
        id = "c-engine",
        slot_key = "engine",
        make = "Rotax",
        model = "915 iS",
        serial = "SN-123456",
        spec = listOf(Spec(key = "displacement", value_ = "1352"), Spec(key = "engine_serial", value_ = "X")),
        children = listOf(Component(id = "c-prop", slot_key = "propeller", make = "Airmaster", serial = "P-9")),
      ),
    ),
  )

  private fun log(id: String, epochSeconds: Long, description: String = "Oil change") = MaintenanceLog(
    id = id,
    timestamp = toWireInstant(epochSeconds),
    work_description = description,
    component_type = ComponentType.COMPONENT_ENGINE,
    readings = listOf(MeterReading(meter_key = "engine_hours", value_ = 380.0)),
    // Everything below must never leave the device (R12).
    technician = Technician(name = "Jane Mechanic"),
    technician_id = "tech-1",
    component_serial = "SN-123456",
    attachments = listOf(Attachment(id = "photo")),
  )

  private fun builder(
    logs: List<MaintenanceLog> = listOf(log("log-1", JUNE_1)),
    tasks: List<MaintenanceTask> = emptyList(),
    hostUid: String = "host-uid",
  ): SuggestionContextBuilder {
    val fleet = mockk<FleetManager> { every { loadThing(THING) } returns flowOf(thing) }
    val taskData = mockk<TaskDataManager> { every { observeTasks(THING) } returns flowOf(tasks) }
    val logManager = mockk<MaintenanceLogManager> {
      every { observeLogs(THING) } returns flowOf(logs)
      every { observeMaintenanceOverview(THING) } returns flowOf(
        MaintenanceOverview(
          current = listOf(
            MeterReading(meter_key = "engine_hours", component_id = "c-engine", value_ = 410.0),
            MeterReading(meter_key = "engine_hours", component_id = "c-engine-2", value_ = 395.0),
            MeterReading(meter_key = "airframe_hours", value_ = 412.0),
          ),
        ),
      )
    }
    val registry = mockk<TemplateRegistry> { every { lexiconFor(any()) } returns GenericLexicon.LEXICON }
    val scopes = mockk<ThingScopeResolver> {
      coEvery { resolveNow(THING) } returns EntityScope.thingChildUnsafe(hostUid, THING)
    }
    return SuggestionContextBuilder(fleet, taskData, logManager, registry, scopes, TimeZone.UTC)
  }

  @Test
  fun `names the Thing and its host tree, and carries the template and its pack`() = runTest {
    val request = builder(hostUid = "the-host").build(THING, "overview")

    assertThat(request.thing_id?.value_).isEqualTo(THING)
    assertThat(request.host_uid?.value_).isEqualTo("the-host")
    assertThat(request.entry_point).isEqualTo("overview")
    assertThat(request.context?.template_id?.value_).isEqualTo("airplane")
    assertThat(request.context?.template_version).isEqualTo(13)
    assertThat(request.context?.static_pack?.map { it.title }).containsExactly("Annual")
    assertThat(request.context?.lexicon_task_noun).isNotEmpty()
  }

  @Test
  fun `sends no identifier, no invented field and no serial`() = runTest {
    val context = builder().build(THING, "overview").context!!

    assertThat(context.specs.map { it.key }).containsExactly("make", "model")
    val engine = context.components.first { it.slot_key == "engine" }
    assertThat(engine.spec.map { it.key }).containsExactly("displacement")
    // Child components are listed too; ComponentSummary has no serial field at all.
    assertThat(context.components.map { it.slot_key }).containsExactly("engine", "propeller")
    assertThat(context.encode().decodeToString()).doesNotContain("SN-123456")
    assertThat(context.encode().decodeToString()).doesNotContain("N123AB")
  }

  @Test
  fun `takes each meter's highest current reading, and says when there is none`() = runTest {
    val meters = builder().build(THING, "overview").context!!.meters.associateBy { it.key }

    assertThat(meters.getValue("engine_hours").current).isEqualTo(410.0)
    assertThat(meters.getValue("engine_hours").component_slot_key).isEqualTo("engine")
    assertThat(meters.getValue("airframe_hours").has_current).isTrue()
    assertThat(meters.getValue("hobbs").has_current).isFalse()
  }

  @Test
  fun `describes existing tasks by slot`() = runTest {
    val task = MaintenanceTask(
      id = "t1",
      title = "Annual",
      component = ComponentType.COMPONENT_ENGINE,
      rules = listOf(InspectionRule(time_rule = TimeRule(interval_months = 12))),
      type = ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN,
      reference_number = "SB-1",
    )

    val existing = builder(tasks = listOf(task)).build(THING, "overview").context!!.existing_tasks.single()

    assertThat(existing.id?.value_).isEqualTo("t1")
    assertThat(existing.component_slot_key).isEqualTo("engine")
    assertThat(existing.rules).isEqualTo(task.rules)
    assertThat(existing.reference_number).isEqualTo("SB-1")
  }

  @Test
  fun `summarises logs newest first, with nothing personal`() = runTest {
    val logs = listOf(log("older", JUNE_1), log("newer", JUNE_1 + DAY))

    val summaries = builder(logs = logs).build(THING, "overview").context!!.logs

    assertThat(summaries.map { it.id?.value_ }).containsExactly("newer", "older").inOrder()
    val newest = summaries.first()
    assertThat(newest.date).isEqualTo("2026-06-02")
    assertThat(newest.work_description).isEqualTo("Oil change")
    assertThat(newest.component_slot_key).isEqualTo("engine")
    assertThat(newest.readings.single().value_).isEqualTo(380.0)
    assertThat(newest.encode().decodeToString()).doesNotContain("Jane Mechanic")
  }

  @Test
  fun `keeps the newest 500 logs and says history was cut`() = runTest {
    val logs = (0 until 600).map { log("log-$it", JUNE_1 + it * DAY) }

    val context = builder(logs = logs).build(THING, "overview").context!!

    assertThat(context.logs).hasSize(500)
    assertThat(context.logs.first().id?.value_).isEqualTo("log-599")
    assertThat(context.logs_truncated).isTrue()
  }

  @Test
  fun `drops the oldest logs until the request is under 400 KiB`() = runTest {
    val builder = builder(logs = emptyList())
    val base = builder.build(THING, "overview")
    val big = "x".repeat(4_000)

    val request = builder.withLogs(base, (0 until 200).map { LogSummary(work_description = big, date = "2026-06-01") })

    assertThat(request.encode().size).isAtMost(400 * 1024)
    assertThat(request.context!!.logs.size).isLessThan(200)
    assertThat(request.context!!.logs_truncated).isTrue()
  }

  @Test
  fun `a short history is sent whole`() = runTest {
    assertThat(builder().build(THING, "overview").context!!.logs_truncated).isFalse()
  }

  private companion object {
    const val THING = "thing-1"
    const val DAY = 86_400L
    val JUNE_1 = Instant.parse("2026-06-01T12:00:00Z").epochSeconds
  }
}
