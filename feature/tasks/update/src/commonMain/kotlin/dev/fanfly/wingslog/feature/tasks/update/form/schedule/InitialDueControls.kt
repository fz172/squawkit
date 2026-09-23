package dev.fanfly.wingslog.feature.tasks.update.form.schedule

/**
 * The create form's "First due" controls — the force-due override, offered once, at creation.
 *
 * Absent on edit: an existing task reschedules from the Adjustments tab, where the banner can
 * show what the schedule currently says next to what the user is changing it to.
 */
data class InitialDueControls(
  val forceOverrideDate: Boolean,
  val onForceOverrideDateChange: (Boolean) -> Unit,
  val forcedDateMillis: Long?,
  val onForcedDateMillisChange: (Long?) -> Unit,
  val onDateClick: () -> Unit,
  val forceOverrideEngine: Boolean,
  val onForceOverrideEngineChange: (Boolean) -> Unit,
  val forcedEngineHours: String,
  val onForcedEngineHoursChange: (String) -> Unit,
)
