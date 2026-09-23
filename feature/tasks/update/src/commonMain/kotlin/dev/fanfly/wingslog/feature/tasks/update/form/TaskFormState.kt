package dev.fanfly.wingslog.feature.tasks.update.form

import androidx.lifecycle.ViewModel
import dev.fanfly.wingslog.feature.tasks.datamanager.forcedDueMeter
import dev.fanfly.wingslog.feature.tasks.datamanager.toDueDate
import dev.fanfly.wingslog.feature.tasks.datamanager.toPickerMillis
import dev.fanfly.wingslog.feature.tasks.update.form.schedule.ScheduleState
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceTask

/**
 * WIP values for the add/edit task form. Held in the ViewModel (not composable `remember`) so the
 * fields survive the form composables being torn down and re-created when the OS file picker
 * returns — see #254. The `initialX` baselines are captured on seed (edit) or construction (add)
 * to drive unsaved-changes detection.
 */
data class TaskFormState(
  val title: String = "",
  val component: ComponentType = ComponentType.COMPONENT_AIRFRAME,
  val type: ComplianceType = ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
  val schedule: ScheduleState = ScheduleState(),
  val refNumber: String = "",
  val complianceAuthority: String = "",
  val complianceNotes: String = "",
  val forceOverrideEngine: Boolean = false,
  val forcedEngineHours: String = "",
  val forceOverrideDate: Boolean = false,
  val forcedDateMillis: Long? = null,
  val initialTitle: String = "",
  val initialComponent: ComponentType = ComponentType.COMPONENT_AIRFRAME,
  val initialType: ComplianceType = ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
  val initialSchedule: ScheduleState = ScheduleState(),
  val initialRefNumber: String = "",
  val initialComplianceAuthority: String = "",
  val initialComplianceNotes: String = "",
  val initialForceOverrideEngine: Boolean = false,
  val initialForcedEngineHours: String = "",
  val initialForceOverrideDate: Boolean = false,
  val initialForcedDateMillis: Long? = null,
) {
  val hasChanges: Boolean
    get() = title != initialTitle ||
      component != initialComponent ||
      type != initialType ||
      schedule != initialSchedule ||
      refNumber != initialRefNumber ||
      complianceAuthority != initialComplianceAuthority ||
      complianceNotes != initialComplianceNotes ||
      forceOverrideEngine != initialForceOverrideEngine ||
      (forceOverrideEngine && forcedEngineHours != initialForcedEngineHours) ||
      forceOverrideDate != initialForceOverrideDate ||
      (forceOverrideDate && forcedDateMillis != initialForcedDateMillis)

  companion object {
    fun fromTask(card: MaintenanceTask): TaskFormState {
      val schedule = ScheduleState.fromTask(card)
      // `force_due_meter` first, falling back to the legacy float — the override may have been
      // set by a build that predates the keyed field (#759).
      val forcedDue = card.forcedDueMeter()
      val forceOverrideEngine = forcedDue != null
      val forcedEngineHours = forcedDue?.value?.toString() ?: ""
      val forceOverrideDate = card.force_due_date != null
      val forcedDateMillis =
        card.force_due_date?.toDueDate()
          ?.toPickerMillis()
      return TaskFormState(
        title = card.title,
        component = card.component,
        type = card.type,
        schedule = schedule,
        refNumber = card.reference_number,
        complianceAuthority = card.compliance_authority,
        complianceNotes = card.compliance_details,
        forceOverrideEngine = forceOverrideEngine,
        forcedEngineHours = forcedEngineHours,
        forceOverrideDate = forceOverrideDate,
        forcedDateMillis = forcedDateMillis,
        initialTitle = card.title,
        initialComponent = card.component,
        initialType = card.type,
        initialSchedule = schedule,
        initialRefNumber = card.reference_number,
        initialComplianceAuthority = card.compliance_authority,
        initialComplianceNotes = card.compliance_details,
        initialForceOverrideEngine = forceOverrideEngine,
        initialForcedEngineHours = forcedEngineHours,
        initialForceOverrideDate = forceOverrideDate,
        initialForcedDateMillis = forcedDateMillis,
      )
    }
  }
}
