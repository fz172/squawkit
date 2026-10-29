package dev.fanfly.wingslog.feature.tasks.viewing

import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.feature.tasks.model.DueStatus

fun DueStatus.statusTier(): StatusTier = when (this) {
  DueStatus.OVERDUE -> StatusTier.CRITICAL
  DueStatus.DUE_SOON -> StatusTier.CAUTION
  DueStatus.NORMAL -> StatusTier.NEUTRAL
  DueStatus.COMPLIED -> StatusTier.POSITIVE
}
