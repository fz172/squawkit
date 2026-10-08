package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.CurrentReading
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.slotKeyFor
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.id.TemplateId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.suggesttasks.ComponentSummary
import dev.fanfly.wingslog.rpc.suggesttasks.ExistingTask
import dev.fanfly.wingslog.rpc.suggesttasks.LogSummary
import dev.fanfly.wingslog.rpc.suggesttasks.MeterSummary
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksRequest
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestionContext
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone

/**
 * The request an AI suggestion run sends about one Thing (docs/ai/task_population_design.md §7.2,
 * PRD R10–R13): the Thing's template, specs, components, meters, live tasks and log history, read
 * through the ordinary managers.
 *
 * **Nothing personal leaves the device, by construction.** The proto has no field for a technician,
 * cost, attachment, comment or serial, and this fills only what it has: a spec field the template
 * marks as an identifier (tail number, VIN, address) is left out, as is any user-invented
 * `custom_N` field, whose meaning nothing declares. Nothing from another Thing or the profile is
 * read (R13).
 */
class SuggestionContextBuilder(
  private val fleetManager: FleetManager,
  private val taskDataManager: TaskDataManager,
  private val logManager: MaintenanceLogManager,
  private val templateRegistry: TemplateRegistry,
  private val scopeResolver: ThingScopeResolver,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

  /** The request for [thingId], and the uid of the tree it lives in. */
  suspend fun build(thingId: String, entryPoint: String): SuggestTasksRequest {
    val thing = fleetManager.loadThing(thingId)
      .filterNotNull()
      .first()
    val template = templateRegistry.forThingWithFallback(thing)
    // A shared Thing lives in its host's tree: users/{hostUid}/thing/{thingId}.
    val hostUid = scopeResolver.resolveNow(thingId).hostUid.orEmpty()
    val tasks = taskDataManager.observeTasks(thingId)
      .first()
    val logs = logManager.observeLogs(thingId)
      .first()
    // What the dashboard shows, worked out from the records rather than read off the stored
    // overview, which a client that predates manual readings rebuilds without them (#1368).
    val current = logManager.observeCurrentReadings(thingId)
      .first()

    val context = SuggestionContext(
      template_id = TemplateId(value_ = template.id),
      template_version = template.version,
      specs = sendable(thing.spec, template.spec_fields),
      components = thing.components.flatMap {
        componentSummaries(
          it,
          template
        )
      },
      meters = template.meters.map { meter ->
        meterSummary(
          meter.key,
          meter.unit_label,
          meter.component_slot_key,
          current
        )
      },
      existing_tasks = tasks.map { task ->
        ExistingTask(
          id = MaintenanceTaskId(value_ = task.id),
          title = task.title,
          component_slot_key = slotKeyFor(task.component),
          rules = task.rules,
          type = task.type,
          reference_number = task.reference_number,
        )
      },
      lexicon_task_noun = templateRegistry.lexiconFor(template).taskNoun.singular,
    )
    val base = SuggestTasksRequest(
      thing_id = ThingId(value_ = thingId),
      host_uid = UserId(value_ = hostUid),
      context = context,
      entry_point = entryPoint,
    )
    return withLogs(
      base,
      logs.sortedByDescending {
        it.timestamp?.getEpochSecond() ?: 0L
      }
        .map(::logSummary)
    )
  }

  /**
   * Adds [newestFirst] logs to [base], dropping the oldest until the request is under
   * [MAX_REQUEST_BYTES] and at most [MAX_LOGS] (design §7.2; the server's cap is 512 KiB).
   */
  internal fun withLogs(
    base: SuggestTasksRequest,
    newestFirst: List<LogSummary>
  ): SuggestTasksRequest {
    var kept = newestFirst.take(MAX_LOGS)
    while (true) {
      val request = base.copy(
        context = base.context!!.copy(
          logs = kept,
          logs_truncated = kept.size < newestFirst.size
        ),
      )
      if (kept.isEmpty() || request.encode().size <= MAX_REQUEST_BYTES) return request
      // Drop the oldest tenth at a time: one at a time is quadratic on a long history.
      kept = kept.take(kept.size - maxOf(1, kept.size / 10))
    }
  }

  private fun logSummary(log: MaintenanceLog) = LogSummary(
    id = MaintenanceLogId(value_ = log.id),
    date = log.timestamp?.toLocalDate(timeZone)
      ?.toString()
      .orEmpty(),
    readings = log.readings,
    // A log has no title of its own; its work description says what was done.
    title = "",
    work_description = log.work_description,
    component_slot_key = slotKeyFor(log.component_type),
  )

  private fun componentSummaries(
    component: Component,
    template: ThingTemplate
  ): List<ComponentSummary> {
    val slot = template.component_slots.findSlot(component.slot_key)
    return listOf(
      ComponentSummary(
        slot_key = component.slot_key,
        make = component.make,
        model = component.model,
        spec = sendable(component.spec, slot?.spec_fields.orEmpty()),
      ),
    ) + component.children.flatMap { componentSummaries(it, template) }
  }

  private companion object {
    const val MAX_LOGS = 500
    const val MAX_REQUEST_BYTES = 400 * 1024

    /** The specs the template declares and does not mark as identifying (R12). */
    fun sendable(specs: List<Spec>, declared: List<SpecField>): List<Spec> {
      val allowed = declared.filterNot { it.is_identifier }
        .map { it.key }
        .toSet()
      return specs.filter { it.key in allowed && it.value_.isNotBlank() }
    }

    fun meterSummary(
      key: String,
      unit: String,
      slotKey: String,
      current: List<CurrentReading>
    ): MeterSummary {
      val reading = current.firstOrNull { it.meterKey == key }?.value
      return MeterSummary(
        key = key,
        unit_label = unit,
        component_slot_key = slotKey,
        current = reading ?: 0.0,
        has_current = reading != null,
      )
    }

    fun List<ComponentSlot>.findSlot(key: String): ComponentSlot? =
      firstNotNullOfOrNull { slot ->
        if (slot.slot_key == key) slot else slot.children.findSlot(
          key
        )
      }
  }
}
