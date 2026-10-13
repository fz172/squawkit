package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.CurrentReading
import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.rpc.suggesttasks.LogSummary
import dev.fanfly.wingslog.task.ComplianceType
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TimeRule
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

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
        spec_fields = listOf(
          SpecField(key = "displacement"),
          SpecField(
            key = "engine_serial",
            is_identifier = true
          )
        ),
        children = listOf(ComponentSlot(slot_key = "propeller")),
      ),
    ),
    meters = listOf(
      MeterDef(key = "airframe_hours", unit_label = "hrs"),
      MeterDef(
        key = "engine_hours",
        unit_label = "hrs",
        component_slot_key = "engine"
      ),
      MeterDef(key = "hobbs", unit_label = "hrs"),
    ),
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
        spec = listOf(
          Spec(key = "displacement", value_ = "1352"),
          Spec(key = "engine_serial", value_ = "X")
        ),
        children = listOf(
          Component(
            id = "c-prop",
            slot_key = "propeller",
            make = "Airmaster",
            serial = "P-9"
          )
        ),
      ),
    ),
  )

  private fun log(
    id: String,
    epochSeconds: Long,
    description: String = "Oil change"
  ) = MaintenanceLog(
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
    val fleet =
      mockk<FleetManager> { every { loadThing(THING) } returns flowOf(thing) }
    val taskData = mockk<TaskDataManager> {
      every { observeTasks(THING) } returns flowOf(tasks)
    }
    val logManager = mockk<MaintenanceLogManager> {
      every { observeLogs(THING) } returns flowOf(logs)
      every { observeCurrentReadings(THING) } returns flowOf(
        listOf(
          CurrentReading("airframe_hours", 412.0, asOf = null, isManual = false),
          CurrentReading("engine_hours", 410.0, asOf = null, isManual = true),
        )
      )
    }
    val registry =
      mockk<TemplateRegistry> {
        every { lexiconFor(any()) } returns GenericLexicon.LEXICON
        every { forThingWithFallback(any()) } answers { firstArg<Thing>().template!! }
      }
    val scopes = mockk<ThingScopeResolver> {
      coEvery { resolveNow(THING) } returns EntityScope.thingChildUnsafe(
        hostUid,
        THING
      )
    }
    return SuggestionContextBuilder(
      fleet,
      taskData,
      logManager,
      registry,
      scopes,
    )
  }

  @Test
  fun `names the Thing and its host tree, and carries the template`() =
    runTest {
      val request = builder(hostUid = "the-host").build(THING, SuggestionEntryPoint.CURATED)

      assertThat(request.thing_id?.value_).isEqualTo(THING)
      assertThat(request.host_uid?.value_).isEqualTo("the-host")
      assertThat(request.entry_point).isEqualTo("curated")
      assertThat(request.context?.template_id?.value_).isEqualTo("airplane")
      assertThat(request.context?.template_version).isEqualTo(13)
      assertThat(request.context?.lexicon_task_noun).isNotEmpty()
    }

  @Test
  fun `sends no identifier, no invented field and no serial`() = runTest {
    val context = builder().build(THING, SuggestionEntryPoint.CURATED).context!!

    assertThat(context.specs.map { it.key }).containsExactly("make", "model")
    val engine = context.components.first { it.slot_key == "engine" }
    assertThat(engine.spec.map { it.key }).containsExactly("displacement")
    // Child components are listed too; ComponentSummary has no serial field at all.
    assertThat(context.components.map { it.slot_key }).containsExactly(
      "engine",
      "propeller"
    )
    assertThat(
      context.encode()
        .decodeToString()
    ).doesNotContain("SN-123456")
    assertThat(
      context.encode()
        .decodeToString()
    ).doesNotContain("N123AB")
  }

  @Test
  fun `takes each meter's current reading, and says when there is none`() =
    runTest {
      val meters = builder().build(
        THING,
        SuggestionEntryPoint.CURATED
      ).context!!.meters.associateBy { it.key }

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

    val existing = builder(tasks = listOf(task)).build(
      THING,
      SuggestionEntryPoint.CURATED
    ).context!!.existing_tasks.single()

    assertThat(existing.id?.value_).isEqualTo("t1")
    assertThat(existing.component_slot_key).isEqualTo("engine")
    assertThat(existing.rules).isEqualTo(task.rules)
    assertThat(existing.reference_number).isEqualTo("SB-1")
  }

  @Test
  fun `sends no log history`() = runTest {
    val logs = listOf(log("older", JUNE_1), log("newer", JUNE_1 + DAY))

    val context = builder(logs = logs).build(THING, SuggestionEntryPoint.CURATED).context!!

    assertThat(context.logs).isEmpty()
    assertThat(context.logs_truncated).isFalse()
  }

  private companion object {
    const val THING = "thing-1"
    const val DAY = 86_400L
    val JUNE_1 = Instant.parse("2026-06-01T12:00:00Z").epochSeconds
  }
}
