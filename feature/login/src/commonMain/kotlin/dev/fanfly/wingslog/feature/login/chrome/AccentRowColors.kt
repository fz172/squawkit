package dev.fanfly.wingslog.feature.login.chrome

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * True when the resolved scheme is a dark one.
 *
 * The accent row needs a mid-dark blue in *both* themes — `primary` in light (#1A5FAE) and
 * `primaryContainer` in dark (#004785) — because each theme's other role is far too light to carry
 * white text. No single role does that, so the scheme is asked directly rather than reading the
 * app's appearance setting, which would be a second source of truth for the same fact.
 */
@Composable
@ReadOnlyComposable
private fun isDarkScheme(): Boolean =
  MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** The pressed/active row's background: the strongest blue each theme has that white text sits on. */
@Composable
@ReadOnlyComposable
internal fun accentRowColor(): Color = if (isDarkScheme()) {
  MaterialTheme.colorScheme.primaryContainer
} else {
  MaterialTheme.colorScheme.primary
}

/** Its chevron: the *other* blue, so it reads as a hint rather than a second label. */
@Composable
@ReadOnlyComposable
internal fun accentRowChevronColor(): Color = if (isDarkScheme()) {
  MaterialTheme.colorScheme.primary
} else {
  MaterialTheme.colorScheme.primaryContainer
}
