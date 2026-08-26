package dev.fanfly.wingslog.feature.developeroptions.plugin

import androidx.compose.runtime.Composable

/**
 * A Developer Options section contributed by a feature.
 *
 * Resolved from Koin by `DeveloperOptionsScreen` (`getAll<DeveloperOptionsExtra>()`) rather than
 * passed down as a composable slot, so a feature can add a section without `feature:shell` — or
 * `feature:settings` — depending on it. Before this existed the screen took a single
 * `dogfoodContent` lambda that the shell filled, which meant the shell had to depend on every
 * contributor and could only ever host one.
 *
 * Bind with `bind DeveloperOptionsExtra::class` so `getAll` finds it. Note that two definitions of
 * the same type make a bare `get<DeveloperOptionsExtra>()` ambiguous — nothing should call it, and
 * `getAll` is the only supported read.
 */
interface DeveloperOptionsExtra {

  /**
   * Ascending. Fixed numbers rather than registration order, so a section does not move because
   * Koin modules were reordered. Leave gaps; `900+` is by convention the trailing "Debug tools" end
   * of the screen.
   */
  val order: Int

  /**
   * False hides the section outright. This is where a capability gate belongs — the feature that
   * owns the section knows whether it applies, and the host should not have to.
   */
  fun isAvailable(): Boolean = true

  /**
   * The section's content, rendered in [order] between dividers supplied by the host.
   *
   * [onNavigate] takes a route rather than a `NavController` so this interface — and therefore this
   * module — needs no navigation dependency. A section that does not navigate simply ignores it.
   */
  @Composable
  fun Content(onNavigate: (route: String) -> Unit)
}
