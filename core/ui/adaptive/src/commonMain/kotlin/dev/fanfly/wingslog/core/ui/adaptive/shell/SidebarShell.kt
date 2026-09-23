package dev.fanfly.wingslog.core.ui.adaptive.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** MEDIUM / EXPANDED / LARGE: the [WingsSidebar] beside the section content. */
@Composable
internal fun SidebarShell(
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
  Row(modifier = Modifier.fillMaxSize()) {
    WingsSidebar(
      state = state,
      onSelectSection = onSelectSection,
      onSelectThing = onSelectThing,
      onAddThing = onAddThing,
      onEnterInviteCode = onEnterInviteCode,
      onOpenAccount = onOpenSettings,
    )
    VerticalDivider()
    Box(
      modifier = Modifier.weight(1f)
        .fillMaxHeight()
    ) {
      ShellContent(
        state = state,
        showTopBarSwitcher = false,
        onSelectThing = onSelectThing,
        // Settings owns its chrome in sidebar mode: the section is a nested NavHost whose root and
        // detail screens render their own headers, so the shell bar would just double up.
        showTopBar = state.section != ShellSection.SETTINGS,
        content = content,
        fab = fab,
        snackbarHostState = snackbarHostState,
      )
    }
  }
}
