package dev.fanfly.wingslog.feature.inspection.model

import kotlinx.datetime.LocalDate

enum class DueStatus {
  NORMAL,
  DUE_SOON,
  OVERDUE,
  COMPLIED
}

/**
 * Metadata computed for an inspection card, describing when it is next due and its current status.
 */
data class DueMetadata(
  val nextDueDate: LocalDate? = null,
  val nextDueEngine: Float? = null,
  val isOnCondition: Boolean = false,
  val isImmediate: Boolean = false,
  val status: DueStatus = DueStatus.NORMAL,
)
