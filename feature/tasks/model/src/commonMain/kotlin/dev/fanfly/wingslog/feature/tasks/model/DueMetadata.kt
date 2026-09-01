package dev.fanfly.wingslog.feature.tasks.model

import kotlinx.datetime.LocalDate

enum class DueStatus {
  NORMAL,
  DUE_SOON,
  OVERDUE,
  COMPLIED
}

/**
 * Metadata computed for a task card, describing when it is next due and its current status.
 */
data class DueMetadata(
  val nextDueDate: LocalDate? = null,
  val nextDueEngine: Float? = null,
  /**
   * Which meter [nextDueEngine] is measured in, when a rule named one (#759).
   *
   * Alongside rather than replacing the value, so every existing renderer keeps working while the
   * ones that should say "mi" rather than assume hours are moved over.
   */
  val nextDueMeterKey: String? = null,
  val isOnCondition: Boolean = false,
  val isImmediate: Boolean = false,
  val status: DueStatus = DueStatus.NORMAL,
)
