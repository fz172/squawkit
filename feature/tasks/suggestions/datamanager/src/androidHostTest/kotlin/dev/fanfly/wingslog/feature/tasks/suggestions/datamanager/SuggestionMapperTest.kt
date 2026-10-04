package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.id.AttachmentId
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.rpc.suggesttasks.FirstDue
import dev.fanfly.wingslog.rpc.suggesttasks.LastDoneEvidence
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.ComplianceType
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MeterRule
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.task.TaskSourceKind
import dev.fanfly.wingslog.task.TimeRule
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.Capabilities
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

class SuggestionMapperTest {

  private val now = Instant.parse("2026-10-15T12:00:00Z")
  private val mapper = SuggestionMapper(
    clock = object : Clock {
      override fun now() = now
    },
    timeZone = TimeZone.UTC,
  )

  /** The airplane preset, the one that files tasks against the frozen ComponentType enum. */
  private val airplane = ThingTemplate(
    id = "airplane",
    capabilities = Capabilities(month_intervals_due_on_anniversary = false),
  )

  private val suggestion = TaskSuggestion(
    title = "Replace spark plugs",
    rationale = "Rotax recommends it.",
    description = "Use the specified plugs.",
    component_slot_key = "engine",
    component_hint = "Engine #2",
    rules = listOf(
      InspectionRule(
        meter_rule = MeterRule(
          meter_key = "engine_hours",
          interval = 200f
        )
      ),
      InspectionRule(time_rule = TimeRule(interval_months = 24)),
    ),
    type = ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN,
    reference_number = "SB-912-001",
    compliance_authority = "Rotax",
    source_kind = TaskSourceKind.TASK_SOURCE_KIND_MANUFACTURER_SCHEDULE,
    citation = "Rotax 915 iS MM",
    page_ref = "p. 5-12",
  )

  @Test
  fun `maps the card the form could have produced`() {
    val task = mapper.toTask(suggestion, airplane, "tasks-4")

    assertThat(task.id).isEmpty()
    assertThat(task.title).isEqualTo("Replace spark plugs")
    // A task cannot name engine #2, so the hint leads the notes.
    assertThat(task.notes).isEqualTo("Engine #2\n\nUse the specified plugs.")
    assertThat(task.component).isEqualTo(ComponentType.COMPONENT_ENGINE)
    assertThat(task.type).isEqualTo(ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN)
    assertThat(task.reference_number).isEqualTo("SB-912-001")
    assertThat(task.compliance_authority).isEqualTo("Rotax")
  }

  @Test
  fun `dates a time rule from now with the template's convention, and leaves meter rules alone`() {
    val anniversary = airplane.copy(
      capabilities = airplane.capabilities!!.copy(
        month_intervals_due_on_anniversary = true
      ),
    )

    val rules = mapper.toTask(suggestion, anniversary, "tasks-4").rules

    assertThat(rules[0]).isEqualTo(suggestion.rules[0])
    assertThat(rules[1].time_rule?.creation_date).isEqualTo(now.toWireInstant())
    assertThat(rules[1].time_rule?.due_on_anniversary).isTrue()
  }

  @Test
  fun `says it came from a run without documents`() {
    val origin = mapper.toTask(suggestion, airplane, "tasks-4").origin!!

    assertThat(origin.kind).isEqualTo(TaskOriginKind.TASK_ORIGIN_KIND_AI_THING)
    assertThat(origin.source_kind).isEqualTo(TaskSourceKind.TASK_SOURCE_KIND_MANUFACTURER_SCHEDULE)
    assertThat(origin.citation).isEqualTo("Rotax 915 iS MM")
    assertThat(origin.page_ref).isEqualTo("p. 5-12")
    assertThat(origin.generation_version).isEqualTo("tasks-4")
    assertThat(origin.suggested_at).isEqualTo(now.toWireInstant())
    assertThat(origin.source_attachment_id).isNull()
  }

  @Test
  fun `takes the origin the server gives, curated included`() {
    val curated =
      suggestion.copy(origin_kind = TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED)

    assertThat(mapper.toTask(curated, airplane, "tasks-4").origin?.kind)
      .isEqualTo(TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED)
  }

  @Test
  fun `a document suggestion carries its document, the same blob`() {
    val manual =
      Attachment(id = "blob-mm", name = "Rotax MM.pdf", sha256 = "abc")
    val cited =
      suggestion.copy(source_document = AttachmentId(value_ = "blob-mm"))

    val task = mapper.toTask(
      cited,
      airplane,
      "tasks-4",
      documents = listOf(manual, Attachment(id = "other"))
    )

    assertThat(task.origin?.kind).isEqualTo(TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT)
    assertThat(task.origin?.source_attachment_id?.value_).isEqualTo("blob-mm")
    assertThat(task.attachments).containsExactly(manual)
  }

  @Test
  fun `a one-time item is forced due at its first due date and reading`() {
    val once = suggestion.copy(
      is_one_time = true,
      first_due = FirstDue(
        date = "2026-12-01",
        meter = MeterReading(
          meter_key = "engine_hours",
          value_ = 500.0
        )
      ),
    )

    val task = mapper.toTask(once, airplane, "tasks-4")

    assertThat(task.is_one_time).isTrue()
    assertThat(task.force_due_date).isEqualTo(
      Instant.parse("2026-12-01T00:00:00Z")
        .toWireInstant()
    )
    assertThat(task.force_due_meter?.value_).isEqualTo(500.0)
  }

  @Test
  fun `a recurring item ignores a first due, and a bad date is dropped`() {
    val recurring = suggestion.copy(first_due = FirstDue(date = "2026-12-01"))
    assertThat(
      mapper.toTask(
        recurring,
        airplane,
        "tasks-4"
      ).force_due_date
    ).isNull()

    val badDate =
      suggestion.copy(is_one_time = true, first_due = FirstDue(date = "soon"))
    assertThat(
      mapper.toTask(
        badDate,
        airplane,
        "tasks-4"
      ).force_due_date
    ).isNull()
  }

  @Test
  fun `never ties the task to a log, even if a result carried last-done evidence`() {
    val done = suggestion.copy(
      last_done = LastDoneEvidence(
        log_id = MaintenanceLogId(value_ = "log-1"),
        date = "2026-05-02",
        reading = MeterReading(meter_key = "engine_hours", value_ = 380.0),
      ),
    )

    val task = mapper.toTask(done, airplane, "tasks-4")

    assertThat(task.force_complied_status).isNull()
    assertThat(task).isEqualTo(mapper.toTask(suggestion, airplane, "tasks-4"))
  }

  @Test
  fun `a Thing-level suggestion off the airplane preset has no component`() {
    val home = ThingTemplate(id = "home")

    assertThat(mapper.toTask(suggestion, home, "tasks-4").component).isEqualTo(
      ComponentType.COMPONENT_UNKNOWN
    )
  }
}
