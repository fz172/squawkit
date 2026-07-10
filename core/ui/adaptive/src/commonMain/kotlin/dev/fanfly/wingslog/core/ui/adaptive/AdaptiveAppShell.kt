package dev.fanfly.wingslog.core.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedFloatingAction
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.layoutTierFor
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.add_aircraft
import wingslog.core.sharedassets.generated.resources.aircraft_shared_badge
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.ic_launcher_foreground
import wingslog.core.sharedassets.generated.resources.settings
import wingslog.core.sharedassets.generated.resources.shell_nav_tasks_narrow
import wingslog.core.sharedassets.generated.resources.shell_tab_dashboard
import wingslog.core.sharedassets.generated.resources.shell_tab_logs
import wingslog.core.sharedassets.generated.resources.shell_tab_squawks
import wingslog.core.sharedassets.generated.resources.shell_tab_tasks
import wingslog.core.sharedassets.generated.resources.shell_title_logs
import wingslog.core.sharedassets.generated.resources.shell_title_tasks
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/** Lightweight aircraft projection used by the shell's switcher. */
data class ShellAircraft(
  val id: String,
  val tail: String,
  val name: String,
  /** Shared into this user's fleet by another account; rendered with a "Shared" badge (§6.3). */
  val shared: Boolean = false,
)

/**
 * Top-level sections of the adaptive shell. The first four are per-aircraft; [SETTINGS] is global.
 */
enum class ShellSection(
  /** Short label for the space-constrained bottom bar tier. */
  val label: StringResource,
  val icon: ImageVector,
  /** Full section name: the content top-bar title, also the wide (EXPANDED/LARGE) sidebar label. */
  val title: StringResource = label,
  /** Label for the narrower MEDIUM sidebar; may abbreviate where the full title doesn't fit. */
  val narrowSidebarLabel: StringResource = title,
) {
  DASHBOARD(UiRes.string.shell_tab_dashboard, Icons.Filled.Dashboard),
  SQUAWKS(UiRes.string.shell_tab_squawks, Icons.Filled.Warning),
  TASKS(
    UiRes.string.shell_tab_tasks,
    Icons.Filled.Checklist,
    UiRes.string.shell_title_tasks,
    narrowSidebarLabel = UiRes.string.shell_nav_tasks_narrow,
  ),
  LOGS(
    UiRes.string.shell_tab_logs,
    Icons.Filled.Description,
    UiRes.string.shell_title_logs,
  ),
  SETTINGS(UiRes.string.settings, Icons.Filled.Settings),
}

private val PER_AIRCRAFT_SECTIONS =
  listOf(
    ShellSection.DASHBOARD,
    ShellSection.SQUAWKS,
    ShellSection.TASKS,
    ShellSection.LOGS,
  )

/** Plain UI state for [AdaptiveAppShell]; produced by a host-side ViewModel. */
data class AdaptiveShellUiState(
  val aircraft: List<ShellAircraft> = emptyList(),
  val selectedAircraftId: String? = null,
  val section: ShellSection = ShellSection.DASHBOARD,
  /** Current user's display name + photo, for the sidebar account/settings entry. */
  val accountName: String? = null,
  val accountPhotoUrl: String? = null,
) {
  val selectedAircraft: ShellAircraft?
    get() = aircraft.firstOrNull { it.id == selectedAircraftId }
}

/**
 * The adaptive web/tablet shell.
 *
 * Navigation container by tier:
 * - **EXPANDED / LARGE** — a custom [WingsSidebar] (brand + aircraft switcher + sections + account
 *   footer), matching the design mock (D2: custom sidebar).
 * - **MEDIUM** — `NavigationSuiteScaffold` icon rail, with the switcher in the top bar.
 * - **COMPACT** — the same section shell as rail tiers once an aircraft exists.
 *
 * Section bodies are supplied by the host via [sectionContent] (M3: real per-aircraft content), and
 * the no-aircraft prompt by [emptyFleetContent] — both are host slots because real content lives in
 * feature modules that `core:ui` cannot depend on. Tier is derived from the measured
 * [BoxWithConstraints] width (not `LocalWindowInfo`, which is unreliable on Kotlin/JS).
 */
@Composable
fun AdaptiveAppShell(
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectAircraft: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onAddAircraft: () -> Unit,
  sectionContent: @Composable (section: ShellSection, aircraftId: String?) -> Unit,
  emptyFleetContent: @Composable () -> Unit,
  // Per-section floating action button (Add log / task / squawk). A host slot because the add
  // actions navigate into feature screens that `core:ui` cannot depend on. Rendered in the shell's
  // own Scaffold slot so snackbars offset around it automatically.
  sectionFab: @Composable (section: ShellSection, aircraftId: String?) -> Unit = { _, _ -> },
  // Shared across every tier so a caller can drive snackbars (e.g. a cross-screen success message)
  // from a single instance regardless of which shell layout is currently active.
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val tier = layoutTierFor(maxWidth)
    val content: @Composable () -> Unit =
      { sectionContent(state.section, state.selectedAircraftId) }
    val fab: @Composable () -> Unit =
      { sectionFab(state.section, state.selectedAircraftId) }
    CompositionLocalProvider(LocalLayoutTier provides tier) {
      if (state.aircraft.isEmpty()) {
        EmptyFleetShell(
          tier = tier,
          state = state,
          onSelectSection = onSelectSection,
          onOpenSettings = onOpenSettings,
          settingsContent = { sectionContent(ShellSection.SETTINGS, null) },
          emptyFleetContent = emptyFleetContent,
        )
      } else {
        ShellForTier(
          tier = tier,
          state = state,
          onSelectSection = onSelectSection,
          onSelectAircraft = onSelectAircraft,
          onOpenSettings = onOpenSettings,
          onAddAircraft = onAddAircraft,
          content = content,
          fab = fab,
          snackbarHostState = snackbarHostState,
        )
      }
    }
  }
}

/* ---------------------------------------------------------------------------------------------- */
/* Empty fleet — chrome with Settings still reachable                                              */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Shell shown while the fleet is empty. There are no aircraft to drive the per-aircraft sections, so
 * they carry no content of their own. Per design:
 * - **full sidebar** — keep the sidebar but hide the switcher and mute the per-aircraft sections.
 *   They stay tappable, though: tapping any of them returns to the add-aircraft prompt, so a user
 *   who has opened Settings has an obvious way back (they read as "greyed out" but still respond).
 * - **narrower tiers** — drop the nav container entirely and surface Settings via the top-right
 *   account avatar button.
 *
 * The settings entry toggles: tapping it opens Settings, tapping it again returns to the
 * add-aircraft prompt (there is no other section to navigate back through).
 */
@Composable
private fun EmptyFleetShell(
  tier: LayoutTier,
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onOpenSettings: () -> Unit,
  settingsContent: @Composable () -> Unit,
  emptyFleetContent: @Composable () -> Unit,
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
        onSelectAircraft = {},
        onAddAircraft = {},
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
          content = body,
        )
      }
    }
  } else {
    EmptyFleetScaffold(
      state = state,
      showAccountAction = true,
      onToggleSettings = toggleSettings,
      content = body,
    )
  }
}

/**
 * Body wrapper for [EmptyFleetShell]. Shows a top bar only when it carries something: the account
 * avatar action (narrower tiers) or the "Account" title while Settings is open. Otherwise, the
 * add-aircraft prompt renders full-bleed, as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyFleetScaffold(
  state: AdaptiveShellUiState,
  showAccountAction: Boolean,
  onToggleSettings: () -> Unit,
  showSettingsTopBar: Boolean = true,
  content: @Composable () -> Unit,
) {
  val inSettings = state.section == ShellSection.SETTINGS
  Scaffold(
    topBar = {
      if (showAccountAction || (inSettings && showSettingsTopBar)) {
        TopAppBar(
          title = {
            // No aircraft means no real section to name; only Settings gets a title here.
            Text(
              if (inSettings) stringResource(state.section.label) else "",
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

@Composable
private fun ShellForTier(
  tier: LayoutTier,
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectAircraft: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onAddAircraft: () -> Unit,
  content: @Composable () -> Unit,
  fab: @Composable () -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  when {
    tier.hasFullSidebar ->
      SidebarShell(
        state = state,
        onSelectSection = onSelectSection,
        onSelectAircraft = onSelectAircraft,
        onOpenSettings = onOpenSettings,
        onAddAircraft = onAddAircraft,
        content = content,
        fab = fab,
        snackbarHostState = snackbarHostState,
      )

    else ->
      ScaffoldShell(
        state = state,
        onSelectSection = onSelectSection,
        onSelectAircraft = onSelectAircraft,
        onOpenSettings = onOpenSettings,
        onAddAircraft = onAddAircraft,
        content = content,
        fab = fab,
        snackbarHostState = snackbarHostState,
      )
  }
}

/* ---------------------------------------------------------------------------------------------- */
/* EXPANDED / LARGE — custom sidebar                                                              */
/* ---------------------------------------------------------------------------------------------- */

@Composable
private fun SidebarShell(
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectAircraft: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onAddAircraft: () -> Unit,
  content: @Composable () -> Unit,
  fab: @Composable () -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  Row(modifier = Modifier.fillMaxSize()) {
    WingsSidebar(
      state = state,
      onSelectSection = onSelectSection,
      onSelectAircraft = onSelectAircraft,
      onAddAircraft = onAddAircraft,
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
        onSelectAircraft = onSelectAircraft,
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

@Composable
private fun WingsSidebar(
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectAircraft: (String) -> Unit,
  onAddAircraft: () -> Unit,
  onOpenAccount: () -> Unit,
  // When true (empty fleet) the switcher is hidden and the per-aircraft sections are muted — but
  // still tappable, so tapping any of them leaves Settings and returns to the add-aircraft prompt.
  // With no aircraft there's no per-aircraft content, so none of them appears selected.
  sectionsMuted: Boolean = false,
  showSwitcher: Boolean = true,
) {
  Surface(
    modifier = Modifier.fillMaxHeight()
      .width(LocalLayoutTier.current.sidebarWidth),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxHeight()
        .padding(vertical = 12.dp)
    ) {
      // Brand
      Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          painter = painterResource(UiRes.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = Modifier.size(44.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
          stringResource(UiRes.string.app_name),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      }

      if (showSwitcher) {
        SidebarSwitcher(
          state = state,
          onSelectAircraft = onSelectAircraft,
          onAddAircraft = onAddAircraft,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }

      PER_AIRCRAFT_SECTIONS.forEach { section ->
        SidebarItem(
          section,
          selected = !sectionsMuted && state.section == section,
          muted = sectionsMuted,
          onClick = { onSelectSection(section) })
      }

      Spacer(Modifier.weight(1f))
      HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
      // Combined account + settings entry: the user's avatar and name; opens the Settings section.
      NavigationDrawerItem(
        label = {
          AccountLabel(state)
        },
        icon = {
          AvatarIcon(
            displayName = state.accountName,
            photoUri = state.accountPhotoUrl,
            size = 28.dp,
          )
        },
        selected = state.section == ShellSection.SETTINGS,
        onClick = onOpenAccount,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
  }
}

@Composable
private fun SidebarItem(
  section: ShellSection,
  selected: Boolean,
  onClick: () -> Unit,
  // Muted (empty-fleet) items keep the greyed-out look but stay tappable, so they can route back to
  // the add-aircraft prompt from Settings.
  muted: Boolean = false,
) {
  val label =
    if (LocalLayoutTier.current.hasWideSidebar) section.title
    else section.narrowSidebarLabel
  NavigationDrawerItem(
    label = { Text(stringResource(label)) },
    icon = { Icon(section.icon, contentDescription = null) },
    selected = selected,
    onClick = onClick,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
      .then(if (muted) Modifier.alpha(DisabledSectionAlpha) else Modifier),
  )
}

private const val DisabledSectionAlpha = 0.38f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SidebarSwitcher(
  state: AdaptiveShellUiState,
  onSelectAircraft: (String) -> Unit,
  onAddAircraft: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  Box(modifier = modifier) {
    Surface(
      onClick = { open = true },
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            state.selectedAircraft?.tail ?: "Select aircraft",
            style = MaterialTheme.typography.titleSmall,
          )
          state.selectedAircraft?.name?.takeIf { it.isNotBlank() }
            ?.let {
              Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
      }
    }
    AircraftDropdown(
      expanded = open,
      onDismiss = { open = false },
      state = state,
      onSelectAircraft = onSelectAircraft,
      onAddAircraft = onAddAircraft,
    )
  }
}

/* ---------------------------------------------------------------------------------------------- */
/* COMPACT / MEDIUM — NavigationSuiteScaffold                                                     */
/* ---------------------------------------------------------------------------------------------- */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ScaffoldShell(
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectAircraft: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onAddAircraft: () -> Unit,
  content: @Composable () -> Unit,
  fab: @Composable () -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  NavigationSuiteScaffold(
    // Settings has no nav-bar item, so showing the bar while it's open feels disconnected. Drop the
    // nav container entirely there and let Settings run full-screen; the top-bar back arrow (wired
    // via onExitSettings below) is the way out.
    layoutType =
      if (state.section == ShellSection.SETTINGS) NavigationSuiteType.None
      else NavigationSuiteType.NavigationBar,
    navigationSuiteItems = {
      ShellSection.entries.filter { it != ShellSection.SETTINGS }
        .forEach { s ->
          item(
            selected = s == state.section,
            onClick = { onSelectSection(s) },
            icon = {
              Icon(s.icon, contentDescription = null)
            },
            label = {
              Text(
                stringResource(s.label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }
    },
  ) {
    // Settings has no entry in the bottom/rail nav, so without this the only way out is to tap a
    // tab. Remember the last tabbed section so the Settings back button returns where the user was.
    val backTarget = remember { mutableStateOf(ShellSection.DASHBOARD) }
    if (state.section != ShellSection.SETTINGS) backTarget.value = state.section
    // Settings is shell state, not a nav destination, so the system back gesture would otherwise
    // fall through and close the app. Catch it here and return to the last tab. (Settings detail
    // pages open off the root nav controller, where this shell isn't composed, so they still pop
    // back to the Settings root normally.)
    BackHandler(enabled = state.section == ShellSection.SETTINGS) {
      onSelectSection(backTarget.value)
    }
    ShellContent(
      state = state,
      // The switcher lives in the top bar on both the rail (MEDIUM) and the bottom-bar (COMPACT)
      // tiers — neither has a sidebar to host it, and on COMPACT it's the only in-place way to
      // switch aircraft.
      showTopBarSwitcher = true,
      onSelectAircraft = onSelectAircraft,
      onAddAircraft = onAddAircraft,
      onOpenSettings = onOpenSettings,
      onExitSettings = { onSelectSection(backTarget.value) },
      content = content,
      fab = fab,
      snackbarHostState = snackbarHostState,
    )
  }
}

/* ---------------------------------------------------------------------------------------------- */
/* Shared content: top bar + host-provided section body                                           */
/* ---------------------------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellContent(
  state: AdaptiveShellUiState,
  showTopBarSwitcher: Boolean,
  onSelectAircraft: (String) -> Unit,
  onAddAircraft: (() -> Unit)? = null,
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
  snackbarHostState: SnackbarHostState,
) {
  // Full-screen Settings (compact) has no bottom nav bar to occupy the system navigation-bar area,
  // so let its content run edge-to-edge under the transparent system bar instead of stopping above
  // it (which leaves an opaque scrim). The settings list re-adds the bottom inset to its own scroll
  // so the last row still clears the gesture bar.
  val fullScreenSettings =
    onExitSettings != null && state.section == ShellSection.SETTINGS
  Scaffold(
    // Settings is full-screen and has no add action; suppress the FAB there. The FAB rides the
    // trailing edge of the same width-capped frame as the content so they stay aligned on LARGE.
    floatingActionButton = {
      if (state.section != ShellSection.SETTINGS) {
        ConstrainedFloatingAction(ContentWidth.Pane) { fab() }
      }
    },
    contentWindowInsets =
      if (fullScreenSettings) {
        ScaffoldDefaults.contentWindowInsets
          .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
      } else {
        ScaffoldDefaults.contentWindowInsets
      },
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
                  onSelectAircraft = onSelectAircraft,
                  onAddAircraft = onAddAircraft,
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

@Composable
private fun ActionBarTitle(state: AdaptiveShellUiState) = Text(
  stringResource(state.section.title),
  maxLines = 1,
  overflow = TextOverflow.Ellipsis,
)

/** The sidebar account/settings entry label: the signed-in user's name, not the current section. */
@Composable
private fun AccountLabel(state: AdaptiveShellUiState) = Text(
  state.accountName?.takeIf { it.isNotBlank() } ?: "Account",
  maxLines = 1,
  overflow = TextOverflow.Ellipsis,
)

@Composable
private fun TopBarSwitcher(
  state: AdaptiveShellUiState,
  onSelectAircraft: (String) -> Unit,
  onAddAircraft: (() -> Unit)?,
) {
  var open by remember { mutableStateOf(false) }
  Box {
    TextButton(onClick = { open = true }) {
      Text(state.selectedAircraft?.tail ?: "Select aircraft")
      Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
    AircraftDropdown(
      expanded = open,
      onDismiss = { open = false },
      state = state,
      onSelectAircraft = onSelectAircraft,
      onAddAircraft = onAddAircraft,
    )
  }
}

@Composable
private fun AircraftDropdown(
  expanded: Boolean,
  onDismiss: () -> Unit,
  state: AdaptiveShellUiState,
  onSelectAircraft: (String) -> Unit,
  onAddAircraft: (() -> Unit)?,
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    state.aircraft.forEach { ac ->
      DropdownMenuItem(
        text = {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(ac.tail, style = MaterialTheme.typography.titleSmall)
              if (ac.shared) {
                Spacer(Modifier.width(8.dp))
                SharedBadge()
              }
            }
            if (ac.name.isNotBlank()) {
              Text(ac.name, style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        onClick = {
          onSelectAircraft(ac.id)
          onDismiss()
        },
        trailingIcon = {
          if (ac.id == state.selectedAircraftId) {
            Icon(Icons.Filled.Check, contentDescription = null)
          }
        },
      )
    }
    if (onAddAircraft != null) {
      HorizontalDivider()
      DropdownMenuItem(
        text = { Text(stringResource(UiRes.string.add_aircraft)) },
        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
        onClick = {
          onAddAircraft()
          onDismiss()
        },
      )
    }
  }
}

/** Small pill marking an aircraft shared into this fleet by another account (§6.3). */
@Composable
private fun SharedBadge() {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = RoundedCornerShape(4.dp),
  ) {
    Text(
      text = stringResource(UiRes.string.aircraft_shared_badge),
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}
