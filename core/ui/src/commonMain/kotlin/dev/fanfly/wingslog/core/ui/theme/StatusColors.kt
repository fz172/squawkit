package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * One visual role for text/icons and compact status chips.
 *
 * Feature code maps domain values to these roles; it must not invent new status
 * colors. The values mirror the task due-state language that established them.
 */
@Immutable
data class StatusTone(
  /** Text, icon, and indicator color when rendered on a regular surface. */
  val accent: Color,
  /** Compact status chip background. */
  val container: Color,
  /** Compact status chip foreground paired with [container]. */
  val onContainer: Color,
)

enum class StatusTier {
  BLOCKING,
  CRITICAL,
  CAUTION,
  POSITIVE,
  NEUTRAL,
}

@Immutable
data class StatusColors(
  /** Grounds the aircraft: AOG and similarly immediate operational stops only. */
  val blocking: StatusTone,
  /** Overdue work and high-priority attention. */
  val critical: StatusTone,
  /** Due soon and medium-priority attention. */
  val caution: StatusTone,
  /** Completed, current, or otherwise healthy states. */
  val positive: StatusTone,
  /** Low priority and inactive status information. */
  val neutral: StatusTone,
)

fun StatusColors.toneFor(tier: StatusTier): StatusTone = when (tier) {
  StatusTier.BLOCKING -> blocking
  StatusTier.CRITICAL -> critical
  StatusTier.CAUTION -> caution
  StatusTier.POSITIVE -> positive
  StatusTier.NEUTRAL -> neutral
}

internal fun statusColorsFor(colorScheme: ColorScheme, darkTheme: Boolean): StatusColors {
  val blocking = if (darkTheme) {
    StatusTone(
      // Standalone AOG labels/icons render on dark surfaces, not on this container.
      accent = colorScheme.error,
      container = colorScheme.errorContainer,
      onContainer = colorScheme.onErrorContainer,
    )
  } else {
    StatusTone(
      accent = colorScheme.error,
      container = colorScheme.error,
      onContainer = colorScheme.onError,
    )
  }
  val critical = if (darkTheme) {
    StatusTone(
      accent = colorScheme.error,
      container = colorScheme.error,
      onContainer = colorScheme.onError,
    )
  } else {
    StatusTone(
      accent = colorScheme.error,
      container = colorScheme.errorContainer,
      onContainer = colorScheme.onErrorContainer,
    )
  }

  return StatusColors(
    blocking = blocking,
    critical = critical,
    caution = StatusTone(
      accent = if (darkTheme) StatusWarningDark else StatusWarningLight,
      container = if (darkTheme) StatusWarningContainerDark else StatusWarningContainerLight,
      onContainer = if (darkTheme) StatusWarningDark else StatusWarningLight,
    ),
    positive = StatusTone(
      accent = if (darkTheme) StatusOkDark else StatusOkLight,
      container = if (darkTheme) StatusOkContainerDark else StatusOkContainerLight,
      onContainer = if (darkTheme) StatusOkDark else StatusOkLight,
    ),
    neutral = StatusTone(
      accent = colorScheme.onSurfaceVariant,
      container = colorScheme.surfaceVariant,
      onContainer = colorScheme.onSurfaceVariant,
    ),
  )
}

internal val LocalStatusColors = staticCompositionLocalOf {
  statusColorsFor(lightColorScheme(), darkTheme = false)
}

val MaterialTheme.statusColors: StatusColors
  @Composable
  @ReadOnlyComposable
  get() = LocalStatusColors.current
