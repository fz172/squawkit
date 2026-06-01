package dev.fanfly.wingslog.core.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.layoutTierFor
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.ic_launcher_foreground
import wingslog.core.sharedassets.generated.resources.settings
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/** Lightweight aircraft projection used by the shell's switcher. */
data class ShellAircraft(
  val id: String,
  val tail: String,
  val name: String,
)

/**
 * Top-level sections of the adaptive shell. The first four are per-aircraft; [SETTINGS] is global.
 */
enum class ShellSection(val label: String, val icon: ImageVector) {
  DASHBOARD("Dashboard", Icons.Filled.Dashboard), SQUAWKS(
    "Squawks",
    Icons.Filled.Warning
  ),
  TASKS("Tasks", Icons.Filled.Checklist),
  LOGS("Logs", Icons.Filled.Description),
  SETTINGS("Settings", Icons.Filled.Settings),
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
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val tier = layoutTierFor(maxWidth)
    val content: @Composable () -> Unit =
      { sectionContent(state.section, state.selectedAircraftId) }
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
 * the only live navigation is Settings (sign-out, sync, account upgrade). Per design:
 * - **full sidebar** — keep the sidebar but disable the switcher and the per-aircraft sections,
 *   leaving only the account/settings entry interactive.
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
        sectionsEnabled = false,
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
  content: @Composable () -> Unit,
) {
  val inSettings = state.section == ShellSection.SETTINGS
  Scaffold(
    topBar = {
      if (showAccountAction || inSettings) {
        TopAppBar(
          title = {
            ActionBarTitle(state)
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
    Box(
      modifier = Modifier.fillMaxSize()
        .padding(padding)
    ) {
      content()
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
      )

    else ->
      ScaffoldShell(
        state = state,
        onSelectSection = onSelectSection,
        onSelectAircraft = onSelectAircraft,
        onOpenSettings = onOpenSettings,
        onAddAircraft = onAddAircraft,
        content = content,
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
        content = content,
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
  // When false (empty fleet) the switcher is hidden and the per-aircraft sections are disabled,
  // leaving only the account/settings entry interactive.
  sectionsEnabled: Boolean = true,
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
          selected = sectionsEnabled && state.section == section,
          enabled = sectionsEnabled,
          onClick = { onSelectSection(section) })
      }

      Spacer(Modifier.weight(1f))
      HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
      // Combined account + settings entry: the user's avatar and name; opens the Settings section.
      NavigationDrawerItem(
        label = {
          ActionBarTitle(state)
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
  enabled: Boolean = true,
) {
  NavigationDrawerItem(
    label = { Text(section.label) },
    icon = { Icon(section.icon, contentDescription = null) },
    selected = selected,
    // NavigationDrawerItem has no `enabled` flag; when disabled we mute it and swallow the tap.
    onClick = if (enabled) onClick else ({}),
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
      .then(if (enabled) Modifier else Modifier.alpha(DisabledSectionAlpha)),
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

@Composable
private fun ScaffoldShell(
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectAircraft: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onAddAircraft: () -> Unit,
  content: @Composable () -> Unit,
) {
  NavigationSuiteScaffold(
    layoutType = NavigationSuiteType.NavigationBar,
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
                s.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }
    },
  ) {
    ShellContent(
      state = state,
      // The switcher lives in the top bar on both the rail (MEDIUM) and the bottom-bar (COMPACT)
      // tiers — neither has a sidebar to host it, and on COMPACT it's the only in-place way to
      // switch aircraft.
      showTopBarSwitcher = true,
      onSelectAircraft = onSelectAircraft,
      onAddAircraft = onAddAircraft,
      onOpenSettings = onOpenSettings,
      content = content,
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
  content: @Composable () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          ActionBarTitle(state)
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
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize()
        .padding(padding)
    ) {
      content()
    }
  }
}

@Composable
private fun ActionBarTitle(state: AdaptiveShellUiState) = Text(
  state.section.label,
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
            Text(ac.tail, style = MaterialTheme.typography.titleSmall)
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
        text = { Text("Add aircraft") },
        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
        onClick = {
          onAddAircraft()
          onDismiss()
        },
      )
    }
  }
}
