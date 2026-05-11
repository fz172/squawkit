package dev.fanfly.wingslog.feature.squawk.sharedassets

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.fanfly.wingslog.aircraft.SquawkPriority

fun SquawkPriority.chipColor(scheme: ColorScheme): Color = when (this) {
  SquawkPriority.SQUAWK_PRIORITY_AOG    -> scheme.error
  SquawkPriority.SQUAWK_PRIORITY_HIGH   -> scheme.errorContainer
  SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> scheme.tertiary
  else                                   -> scheme.primary
}

fun SquawkPriority.chipTextColor(scheme: ColorScheme): Color = when (this) {
  SquawkPriority.SQUAWK_PRIORITY_AOG    -> scheme.onError
  SquawkPriority.SQUAWK_PRIORITY_HIGH   -> scheme.onErrorContainer
  SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> scheme.onTertiary
  else                                   -> scheme.onPrimary
}
