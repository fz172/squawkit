package dev.fanfly.wingslog.core.ui.adaptive.shell

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.AdaptiveShellUiState
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedFloatingAction
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.DetailPaneState
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalDetailPane
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalNavPillClearance
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.shell.switcher.TopBarSwitcher
import dev.fanfly.wingslog.core.ui.adaptive.title
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.settings
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/** Shared by every populated tier: the top bar plus the host-provided section body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShellContent(
  state: AdaptiveShellUiState,
  showTopBarSwitcher: Boolean,
  onSelectThing: (String) -> Unit,
  onAddThing: (() -> Unit)? = null,
  onEnterInviteCode: (() -> Unit)? = null,
  onOpenSettings: (() -> Unit)? = null,
  // Non-sidebar tiers only: leaves the Settings section back to the tabbed views. Null in sidebar
  // mode, where the sidebar itself is the way out.
  onExitSettings: (() -> Unit)? = null,
  // When false the section provides its own chrome (e.g. the sidebar-tier Settings section, whose
  // nested screens carry their own top bars). The bar is omitted so content fills the pane.
  showTopBar: Boolean = true,
  content: @Composable () -> Unit,
  // Per-section FAB; the host decides which sections show one. Settings never does.
  fab: @Composable () -> Unit = {},
  // Bottom overlay drawn over the content (aligns itself within the content Box). Empty on sidebar
  // tiers; on COMPACT it carries the floating pill, which rides above the content rather than
  // reserving layout height, so the content scrolls edge-to-edge beneath it.
  bottomOverlay: @Composable BoxScope.() -> Unit = {},
  // COMPACT only: lets content run under the pill / system nav bar (bottom inset dropped) and lifts
  // the FAB and snackbars above the pill by [contentBottomClearance].
  edgeToEdgeBottom: Boolean = false,
  // Bottom padding the section content, FAB, and snackbars must add to clear the floating pill.
  // Published to content via [LocalNavPillClearance]; 0 on tiers without a pill.
  contentBottomClearance: Dp = 0.dp,
  snackbarHostState: SnackbarHostState,
) {
  // Full-screen Settings (compact) has no bottom nav bar to occupy the system navigation-bar area,
  // so let its content run edge-to-edge under the transparent system bar instead of stopping above
  // it (which leaves an opaque scrim). The settings list re-adds the bottom inset to its own scroll
  // so the last row still clears the gesture bar.
  val fullScreenSettings =
    onExitSettings != null && state.section == ShellSection.SETTINGS
  // The top action bar scrolls off with the section content and only returns once the content is back
  // at the top (exitUntilCollapsed) — so it reads as part of the content rather than peeking back on
  // any reverse flick. It's a single shell-level bar shared across every section, so switching sections
  // leaves no scroll event to bring it back — reset the offset on each section change so the bar slides
  // back down over the new (top-anchored) content instead of staying collapsed.
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  LaunchedEffect(state.section) {
    scrollBehavior.state.contentOffset = 0f
    animate(
      initialValue = scrollBehavior.state.heightOffset,
      targetValue = 0f,
    ) { value, _ -> scrollBehavior.state.heightOffset = value }
  }
  val detailPane = remember { DetailPaneState() }
  CompositionLocalProvider(LocalDetailPane provides detailPane) {
    Scaffold(
      // Let the section's scrolling list drive the top bar's collapse/expand.
      modifier =
        if (showTopBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
      // Settings is full-screen and has no add action; suppress the FAB there. The FAB rides the
      // trailing edge of the same width-capped frame as the content so they stay aligned on LARGE. On
      // COMPACT the FAB instead rides in the bottom overlay above the floating pill (see the caller's
      // bottomOverlay), so the scaffold slot is used only on the sidebar tiers.
      floatingActionButton = {
        if (!edgeToEdgeBottom && state.section != ShellSection.SETTINGS) {
          // Stepped in past an open detail pane, so it rides the list rather than the pane.
          Box(Modifier.padding(end = detailPane.width)) {
            ConstrainedFloatingAction(ContentWidth.Pane) { fab() }
          }
        }
      },
      contentWindowInsets =
        if (fullScreenSettings || edgeToEdgeBottom) {
          // Let content run edge-to-edge under the floating pill / system nav bar; the section lists
          // re-add the bottom space they need via LocalNavPillClearance.
          ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        } else {
          ScaffoldDefaults.contentWindowInsets
        },
      // Lift snackbars above the pill too, so a "changes discarded" notice isn't hidden behind it.
      snackbarHost = {
        Box(modifier = Modifier.padding(bottom = if (edgeToEdgeBottom) contentBottomClearance else 0.dp)) {
          SnackbarHost(snackbarHostState)
        }
      },
      topBar = {
        if (showTopBar) {
          // The bar shares the content column's width cap so the title and actions line up with the
          // content below on wide (LARGE) panes.
          ConstrainedTopBar(ContentWidth.Pane) {
            TopAppBar(
              title = {
                ActionBarTitle(state)
              },
              navigationIcon = {
                if (onExitSettings != null && state.section == ShellSection.SETTINGS) {
                  IconButton(onClick = onExitSettings) {
                    Icon(
                      Icons.AutoMirrored.Filled.ArrowBack,
                      contentDescription = stringResource(UiRes.string.back),
                    )
                  }
                }
              },
              actions = {
                if (showTopBarSwitcher && state.section != ShellSection.SETTINGS) {
                  TopBarSwitcher(
                    state = state,
                    onSelectThing = onSelectThing,
                    onAddThing = onAddThing,
                    onEnterInviteCode = onEnterInviteCode,
                  )
                }
                if (onOpenSettings != null && state.section != ShellSection.SETTINGS) {
                  IconButton(onClick = onOpenSettings) {
                    AvatarIcon(
                      displayName = state.accountName,
                      photoUri = state.accountPhotoUrl,
                      size = Spacing.huge,
                      contentDescription = stringResource(UiRes.string.settings),
                    )
                  }
                }
              },
              scrollBehavior = scrollBehavior,
            )
          }
        }
      },
    ) { padding ->
      // Cap the section body at the pane width so content stays readable on very wide windows
      // (github.com/fz172/squawkit/issues/101). Only LARGE panes are wide enough for the cap to bite;
      // narrower tiers keep filling the window as before.
      Box(
        modifier = Modifier.fillMaxSize()
          .padding(padding)
      ) {
        Box(
          // Uncapped while a detail pane is open: list and detail share the whole width, the pane at
          // the window's edge, rather than a column with empty ground either side of it.
          modifier = (if (detailPane.open) Modifier.fillMaxWidth() else Modifier.constrainedContentWidth(
            ContentWidth.Pane
          ))
            .fillMaxHeight()
            .align(Alignment.TopCenter)
        ) {
          // Section lists read this to pad their bottom so their last rows clear the floating pill they
          // now scroll beneath.
          CompositionLocalProvider(LocalNavPillClearance provides contentBottomClearance) {
            content()
          }
        }
        // The floating pill (or nothing, on sidebar tiers) rides above the content, aligning itself.
        bottomOverlay()
      }
    }
  }
}

@Composable
private fun ActionBarTitle(state: AdaptiveShellUiState) = Text(
  state.section.title(),
  maxLines = 1,
  overflow = TextOverflow.Ellipsis,
)
