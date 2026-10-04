package dev.fanfly.wingslog.feature.tasks.update.form

import dev.fanfly.wingslog.core.analytics.TaskOriginEdited
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TaskOriginKind

/**
 * The `task_origin_edited` event (PRD R50) for saving [after] over [before], or null when there is
 * nothing to report: a task made by hand or written before origins existed, or a save that changed
 * nothing the user sees. Curated tasks count as well as AI ones, so the two can be compared.
 */
internal fun originEditOf(
  templateId: String,
  before: MaintenanceTask,
  after: MaintenanceTask
): TaskOriginEdited? {
  val kind = before.origin?.kind ?: return null
  if (kind == TaskOriginKind.TASK_ORIGIN_KIND_USER || kind == TaskOriginKind.TASK_ORIGIN_KIND_UNSPECIFIED) return null
  val groups = buildList {
    if (before.title != after.title || before.notes != after.notes || before.component != after.component ||
      before.attachments != after.attachments
    ) add("details")
    if (before.rules != after.rules || before.is_one_time != after.is_one_time ||
      before.force_due_date != after.force_due_date
    ) add("schedule")
    if (before.type != after.type || before.reference_number != after.reference_number ||
      before.compliance_authority != after.compliance_authority ||
      before.compliance_details != after.compliance_details
    ) add("compliance")
  }
  if (groups.isEmpty()) return null
  return TaskOriginEdited(
    templateId = templateId,
    originKind = kind.name.removePrefix("TASK_ORIGIN_KIND_")
      .lowercase(),
    fieldGroup = groups.joinToString("+"),
  )
}
