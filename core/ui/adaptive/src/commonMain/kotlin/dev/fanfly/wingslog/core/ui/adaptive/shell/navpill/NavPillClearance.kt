package dev.fanfly.wingslog.core.ui.adaptive.shell.navpill

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

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

/**
 * Bottom padding for a section that shows the add-FAB **as well as** the pill — Squawks, Tasks,
 * Logs and Data logs. [LocalNavPillClearance] alone clears the pill but not the FAB riding above it
 * at the trailing edge, which is what hid the last row of every one of those lists behind it.
 *
 * Zero on tiers without a pill, where the FAB sits in the scaffold's own slot and needs no help.
 */
val navPillAndFabClearance: Dp
  @Composable get() {
    val pill = LocalNavPillClearance.current
    return if (pill == 0.dp) pill else pill + Spacing.buttonHeight + Spacing.extraLarge
  }
