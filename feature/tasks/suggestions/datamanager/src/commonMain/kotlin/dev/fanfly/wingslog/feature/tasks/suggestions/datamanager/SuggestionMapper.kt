package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.tasks.datamanager.componentTypeForSlot
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.ForceCompliedStatus
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TaskOrigin
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import com.squareup.wire.Instant as WireInstant

/**
 * The [MaintenanceTask] an accepted suggestion becomes (docs/ai/task_population_design.md §7.4):
 * an ordinary card the form could have produced, plus where it came from.
 *
 * The first-due preview maps a suggestion the same way and asks the due engine (R29), so what the
 * review shows and what is saved cannot disagree.
 */
class SuggestionMapper(
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

  /**
   * [suggestion] as a task to add. [generationVersion] is the run's, for the origin. [documents]
   * are the run's source documents; the one the suggestion cites is copied onto the task, the same
   * blob (R37). The id is left for `TaskDataManager.addTask` to assign.
   */
  fun toTask(
    suggestion: TaskSuggestion,
    template: ThingTemplate?,
    generationVersion: String,
    documents: List<Attachment> = emptyList(),
  ): MaintenanceTask {
    val now = clock.now().toWireInstant()
    val sourceDocument = suggestion.source_document?.value_.orEmpty()
    return MaintenanceTask(
      title = suggestion.title,
      // A task cannot name engine #2 (§2), so the hint leads the notes.
      notes = listOf(suggestion.component_hint, suggestion.description)
        .filter { it.isNotBlank() }
        .joinToString("\n\n"),
      component = componentTypeForSlot(suggestion.component_slot_key, template),
      rules = suggestion.rules.map { rule ->
        val time = rule.time_rule ?: return@map rule
        // Dated now, with the template's month convention, as the task form stamps them.
        rule.copy(
          time_rule = time.copy(
            creation_date = now,
            due_on_anniversary = template?.capabilities?.month_intervals_due_on_anniversary ?: false,
          ),
        )
      },
      type = suggestion.type,
      reference_number = suggestion.reference_number,
      compliance_authority = suggestion.compliance_authority,
      is_one_time = suggestion.is_one_time,
      force_due_date = suggestion.first_due?.takeIf { suggestion.is_one_time }?.date?.let(::startOfDay),
      force_due_meter = suggestion.first_due?.takeIf { suggestion.is_one_time }?.meter,
      // No log is linked to a new task, so the due engine reads "last done" from here (R29).
      force_complied_status = suggestion.last_done?.let { done ->
        ForceCompliedStatus(complied_date = startOfDay(done.date), complied_meter = done.reading)
      },
      attachments = documents.filter { sourceDocument.isNotEmpty() && it.id == sourceDocument },
      origin = TaskOrigin(
        kind = if (sourceDocument.isNotEmpty()) {
          TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT
        } else {
          TaskOriginKind.TASK_ORIGIN_KIND_AI_THING
        },
        source_kind = suggestion.source_kind,
        citation = suggestion.citation,
        source_attachment_id = suggestion.source_document?.takeIf { sourceDocument.isNotEmpty() },
        page_ref = suggestion.page_ref,
        generation_version = generationVersion,
        suggested_at = now,
      ),
    )
  }

  /** A wire `yyyy-mm-dd` as the start of that day here, or null when it is not a date. */
  private fun startOfDay(date: String): WireInstant? =
    runCatching { LocalDate.parse(date) }.getOrNull()?.atStartOfDayIn(timeZone)?.toWireInstant()
}
