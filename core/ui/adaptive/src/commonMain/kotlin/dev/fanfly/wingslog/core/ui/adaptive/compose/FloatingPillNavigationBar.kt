package dev.fanfly.wingslog.core.ui.adaptive.compose

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
 * The visible bar is a rounded [Surface] inset from the screen edges rather than a docked, full-width
 * `NavigationBar`. The selected destination expands into a filled chip (icon + label) in the
 * primary-container tone; the rest stay as plain text labels. It sits in a `Scaffold` `bottomBar`
 * slot, so the scaffold reserves its height for content above and lifts snackbars / the section FAB
 * over it automatically.
 *
 * The whole slot is backed by an opaque [surfaceColor] band that reaches down through the system
 * navigation-bar inset (the hosts run edge-to-edge). Without it the app paints nothing behind the
 * floating pill and the transparent gap reads as a black bar under the toolbar.
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
  // Matches the section content's background so the band reads as one continuous surface with the
  // scrolling content above it, not as a distinct bar.
  surfaceColor: Color = MaterialTheme.colorScheme.surface,
) {
  Box(
    modifier = modifier.fillMaxWidth()
      .background(surfaceColor)
      // Clear the system gesture / navigation bar, then float above it with a small margin.
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
      tonalElevation = 3.dp,
      // A floating element needs a cast shadow to lift off the surface band beneath it; tonal
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
        Icon(item.icon, contentDescription = null, modifier = Modifier.size(20.dp))
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
