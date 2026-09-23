package dev.fanfly.wingslog.feature.tasks.model

/** Overdue or due soon: the task belongs in the overview's attention rail. */
val MaintenanceTaskWithStatus.needsAttention: Boolean
  get() = dueStatus.status == DueStatus.OVERDUE || dueStatus.status == DueStatus.DUE_SOON
