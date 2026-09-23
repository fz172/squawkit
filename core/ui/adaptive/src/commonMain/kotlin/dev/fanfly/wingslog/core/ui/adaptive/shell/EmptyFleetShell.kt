package dev.fanfly.wingslog.core.ui.adaptive.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.adaptive.AdaptiveShellUiState
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.label
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.settings
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/**
 * Shell shown while the fleet is empty. There are no thing to drive the per-thing sections, so
 * they carry no content of their own. Per design:
 * - **full sidebar** — keep the sidebar but hide the switcher and mute the per-thing sections.
 *   They stay tappable, though: tapping any of them returns to the add-thing prompt, so a user
 *   who has opened Settings has an obvious way back (they read as "greyed out" but still respond).
 * - **narrower tiers** — drop the nav container entirely and surface Settings via the top-right
 *   account avatar button.
 *
 * The settings entry toggles: tapping it opens Settings, tapping it again returns to the
 * add-thing prompt (there is no other section to navigate back through).
 */
@Composable
internal fun EmptyFleetShell(
  tier: LayoutTier,
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onOpenSettings: () -> Unit,
  settingsContent: @Composable () -> Unit,
  emptyFleetContent: @Composable () -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val toggleSettings: () -> Unit = {
    if (state.section == ShellSection.SETTINGS) onSelectSection(ShellSection.DASHBOARD)
    else onOpenSettings()
  }
  val body: @Composable () -> Unit = {
    if (state.section == ShellSection.SETTINGS) settingsContent() else emptyFleetContent()
  }
  if (tier.hasFullSidebar) {
    Row(modifier = Modifier.fillMaxSize()) {
      WingsSidebar(
        state = state,
        onSelectSection = onSelectSection,
        onSelectThing = {},
        onAddThing = {},
        onOpenAccount = toggleSettings,
        sectionsMuted = true,
        showSwitcher = false,
      )
      VerticalDivider()
      Box(
        modifier = Modifier.weight(1f)
          .fillMaxHeight()
      ) {
        EmptyFleetScaffold(
          state = state,
          showAccountAction = false,
          onToggleSettings = toggleSettings,
          // In sidebar mode the Settings section provides its own header (nested NavHost), so the
          // scaffold's "Settings" bar would double up.
          showSettingsTopBar = false,
          snackbarHostState = snackbarHostState,
          content = body,
        )
      }
    }
  } else {
    EmptyFleetScaffold(
      state = state,
      showAccountAction = true,
      onToggleSettings = toggleSettings,
      snackbarHostState = snackbarHostState,
      content = body,
    )
  }
}

/**
 * Body wrapper for [EmptyFleetShell]. Shows a top bar only when it carries something: the account
 * avatar action (narrower tiers) or the "Account" title while Settings is open. Otherwise, the
 * add-thing prompt renders full-bleed, as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyFleetScaffold(
  state: AdaptiveShellUiState,
  showAccountAction: Boolean,
  onToggleSettings: () -> Unit,
  snackbarHostState: SnackbarHostState,
  showSettingsTopBar: Boolean = true,
  content: @Composable () -> Unit,
) {
  val inSettings = state.section == ShellSection.SETTINGS
  Scaffold(
    // An empty fleet is not a reason to have no snackbar host. It is the *most* likely moment to
    // need one: losing access to a shared thing that was your only thing lands you here, and
    // that is exactly when the "changes discarded" notice has to be seen (PRD D3).
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      if (showAccountAction || (inSettings && showSettingsTopBar)) {
        TopAppBar(
          title = {
            // No thing means no real section to name; only Settings gets a title here.
            Text(
              if (inSettings) state.section.label() else "",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          navigationIcon = {
            // While Settings is open, the content already shows the account avatar, so the top bar
            // offers a back affordance instead (there is no bottom nav to leave Settings from).
            if (showAccountAction && inSettings) {
              IconButton(onClick = onToggleSettings) {
                Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = stringResource(UiRes.string.back),
                )
              }
            }
          },
          actions = {
            if (showAccountAction && !inSettings) {
              IconButton(onClick = onToggleSettings) {
                AvatarIcon(
                  displayName = state.accountName,
                  photoUri = state.accountPhotoUrl,
                  size = Spacing.huge,
                  contentDescription = stringResource(UiRes.string.settings),
                )
              }
            }
          },
        )
      }
    },
  ) { padding ->
    // Same pane-width cap as ShellContent, so the empty-fleet prompt and Settings match the
    // populated shell on wide windows.
    Box(
      modifier = Modifier.fillMaxSize()
        .padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Box(
        modifier = Modifier.constrainedContentWidth(ContentWidth.Pane)
          .fillMaxHeight()
      ) {
        content()
      }
    }
  }
}
