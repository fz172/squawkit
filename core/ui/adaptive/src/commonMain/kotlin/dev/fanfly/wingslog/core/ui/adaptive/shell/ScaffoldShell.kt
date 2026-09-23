package dev.fanfly.wingslog.core.ui.adaptive.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.AdaptiveShellUiState
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.core.ui.adaptive.compose.FloatingNavItem
import dev.fanfly.wingslog.core.ui.adaptive.compose.FloatingPillNavBarHeight
import dev.fanfly.wingslog.core.ui.adaptive.compose.FloatingPillNavigationBar
import dev.fanfly.wingslog.core.ui.adaptive.label
import dev.fanfly.wingslog.core.ui.adaptive.perThingSections

/** COMPACT: section content under a floating pill bottom nav, with the switcher in the top bar. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ScaffoldShell(
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectThing: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onAddThing: () -> Unit,
  onEnterInviteCode: (() -> Unit)?,
  content: @Composable () -> Unit,
  fab: @Composable () -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  // Settings has no entry in the nav, so without this the only way out is to tap a tab. Remember the
  // last tabbed section so the Settings back button returns where the user was.
  val backTarget = remember { mutableStateOf(ShellSection.DASHBOARD) }
  if (state.section != ShellSection.SETTINGS) backTarget.value = state.section
  // Settings is shell state, not a nav destination, so the system back gesture would otherwise fall
  // through and close the app. Catch it here and return to the last tab. (Settings detail pages open
  // off the root nav controller, where this shell isn't composed, so they still pop back to the
  // Settings root normally.)
  BackHandler(enabled = state.section == ShellSection.SETTINGS) {
    onSelectSection(backTarget.value)
  }
  // Settings runs full-screen with no nav container, so hide the pill there; the top-bar back arrow
  // (wired via onExitSettings) is the way out.
  val showPill = state.section != ShellSection.SETTINGS
  // The pill is a bottom overlay, not a scaffold bar, so section content scrolls edge-to-edge
  // underneath it. Content clears it via LocalNavPillClearance = the live nav-bar inset plus the
  // pill's own height. Zero when the pill is hidden (Settings), so nothing over-pads there.
  val navBarInset = WindowInsets.navigationBars.asPaddingValues()
    .calculateBottomPadding()
  val pillClearance =
    if (showPill) navBarInset + FloatingPillNavBarHeight else 0.dp
  ShellContent(
    state = state,
    // The switcher lives in the top bar on COMPACT — there is no sidebar to host it, so it is the
    // only in-place way to switch thing.
    showTopBarSwitcher = true,
    onSelectThing = onSelectThing,
    onAddThing = onAddThing,
    onEnterInviteCode = onEnterInviteCode,
    onOpenSettings = onOpenSettings,
    onExitSettings = { onSelectSection(backTarget.value) },
    content = content,
    fab = fab,
    edgeToEdgeBottom = true,
    contentBottomClearance = pillClearance,
    bottomOverlay = {
      if (showPill) {
        // Section add-FAB rides above the pill at the trailing edge (empty for sections, e.g.
        // Dashboard, that have no add action — an empty box then draws nothing).
        Box(
          modifier = Modifier.align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = pillClearance),
        ) {
          fab()
        }
        FloatingPillNavigationBar(
          items = perThingSections().map { s ->
            FloatingNavItem(
              label = s.label(),
              icon = s.icon,
              selected = s == state.section,
              onClick = { onSelectSection(s) },
            )
          },
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    },
    snackbarHostState = snackbarHostState,
  )
}
