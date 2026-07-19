package dev.fanfly.wingslog.core.ui.adaptive.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bottom padding a section's scrolling content must add so its last rows clear the floating pill
 * ([FloatingPillNavigationBar]) instead of hiding behind it. Published by the compact shell and read
 * by each section's scroll root — see `Modifier`-level use at the `verticalScroll` columns and the
 * Logs `LazyColumn`'s `contentPadding`. Defaults to `0.dp`, so tiers without the pill (rail/sidebar,
 * and every non-compact window) are unaffected.
 */
val LocalNavPillClearance = compositionLocalOf { 0.dp }

/**
 * Height the floating pill occupies **above** the system navigation-bar inset — its chip plus the
 * inner/outer padding in [FloatingPillNavigationBar]. The shell adds the live `navigationBars` inset
 * to this to derive [LocalNavPillClearance]. Keep in sync if the pill's paddings change.
 */
val FloatingPillNavBarHeight: Dp = 72.dp

/** One destination in the [FloatingPillNavigationBar]. */
data class FloatingNavItem(
  /** Resolved, human-readable label. Shown on the selected chip and used as the icon's a11y name. */
  val label: String,
  val icon: ImageVector,
  val selected: Boolean,
  val onClick: () -> Unit,
)

/**
 * A floating, pill-shaped bottom navigation bar for the COMPACT (phone) tier — the SquawkIt take on
 * the Google Photos redesign (github.com/fz172/squawkit/issues/187).
 *
 * The pill is a rounded [Surface] inset from the screen edges rather than a docked, full-width
 * `NavigationBar`. The selected destination expands into a filled chip (icon + label) in the
 * primary-container tone; the rest stay as plain text labels. It is rendered as a bottom **overlay**
 * (not a `Scaffold` `bottomBar`) so section content scrolls edge-to-edge underneath it; content
 * clears the pill via [LocalNavPillClearance] rather than by the scaffold reserving its height.
 *
 * The section add-FAB is intentionally left to the scaffold's own floating-action slot (it rides
 * above the pill) rather than being welded to the bar: not every section has an add action, and a
 * detached button beside a four-item labelled pill does not fit a 320dp phone (iPhone SE) without
 * overflow. Keeping them separate holds the pill centered and stable across sections.
 */
@Composable
fun FloatingPillNavigationBar(
  items: List<FloatingNavItem>,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxWidth()
      // Clear the system gesture / navigation bar, then float above it with a small margin.
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
      tonalElevation = 3.dp,
      // A floating element needs a cast shadow to lift off the content scrolling beneath it; tonal
      // elevation alone (the card convention) wouldn't separate it from same-tone surfaces.
      shadowElevation = 6.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        items.forEach { item -> PillItem(item) }
      }
    }
  }
}

@Composable
private fun PillItem(item: FloatingNavItem) {
  // clip before selectable so the tap ripple is bounded to the pill / circle shape.
  val base = Modifier.clip(CircleShape)
    .selectable(
      selected = item.selected,
      onClick = item.onClick,
      role = Role.Tab,
    )
  if (item.selected) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier = base,
    ) {
      Row(
        modifier = Modifier.heightIn(min = 40.dp)
          .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          item.icon,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Text(
          item.label,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  } else {
    Box(
      // Text-only when collapsed; keep a comfortable touch target height.
      modifier = base.heightIn(min = 40.dp)
        .padding(horizontal = 12.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        item.label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
