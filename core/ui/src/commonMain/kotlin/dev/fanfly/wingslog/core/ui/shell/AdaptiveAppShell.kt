package dev.fanfly.wingslog.core.ui.shell

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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.compose.AvatarIcon
import dev.fanfly.wingslog.core.ui.common.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.common.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.common.compose.layoutTierFor
import org.jetbrains.compose.resources.painterResource
import wingslog.core.ui.generated.resources.ic_launcher_foreground
import wingslog.core.ui.generated.resources.Res as UiRes

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
  DASHBOARD("Dashboard", Icons.Filled.Dashboard),
  TASKS("Tasks", Icons.Filled.Checklist),
  LOGS("Logs", Icons.Filled.Description),
  SQUAWKS("Squawks", Icons.Filled.Warning),
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
  /**
   * Whether an aircraft has been opened from the fleet landing. Only meaningful on COMPACT, where
   * the landing page is the root; above phone the switcher selects in place and sections are always
   * shown. See `docs/web/web_adaptive_layout_design.html` §6.
   */
  val entered: Boolean = false,
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
 * - **COMPACT** — [fleetLanding] root until an aircraft is opened ([onEnterAircraft]); then sections
 *   with a bottom bar and a back arrow to the fleet.
 *
 * Section bodies are supplied by the host via [sectionContent] (M3: real per-aircraft content), and
 * the phone root by [fleetLanding] (the real fleet list) — both are host slots because real content
 * lives in feature modules that `core:ui` cannot depend on. Tier is derived from the measured
 * [BoxWithConstraints] width (not `LocalWindowInfo`, which is unreliable on Kotlin/JS).
 */
@Composable
fun AdaptiveAppShell(
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectAircraft: (String) -> Unit,
  onEnterAircraft: (String) -> Unit,
  onExitToFleet: () -> Unit,
  onOpenSettings: () -> Unit,
  onAddAircraft: () -> Unit,
  sectionContent: @Composable (section: ShellSection, aircraftId: String?) -> Unit,
  fleetLanding: @Composable (onAircraftClick: (String) -> Unit) -> Unit,
  onEditAircraft: (() -> Unit)? = null,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val tier = layoutTierFor(maxWidth)
    val content: @Composable () -> Unit =
      { sectionContent(state.section, state.selectedAircraftId) }
    CompositionLocalProvider(LocalLayoutTier provides tier) {
      ShellForTier(
        tier = tier,
        state = state,
        onSelectSection = onSelectSection,
        onSelectAircraft = onSelectAircraft,
        onEnterAircraft = onEnterAircraft,
        onExitToFleet = onExitToFleet,
        onOpenSettings = onOpenSettings,
        onAddAircraft = onAddAircraft,
        onEditAircraft = onEditAircraft,
        fleetLanding = fleetLanding,
        content = content,
      )
    }
  }
}

@Composable
private fun ShellForTier(
  tier: LayoutTier,
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectAircraft: (String) -> Unit,
  onEnterAircraft: (String) -> Unit,
  onExitToFleet: () -> Unit,
  onOpenSettings: () -> Unit,
  onAddAircraft: () -> Unit,
  onEditAircraft: (() -> Unit)?,
  fleetLanding: @Composable (onAircraftClick: (String) -> Unit) -> Unit,
  content: @Composable () -> Unit,
) {
  when {
    tier == LayoutTier.COMPACT && !state.entered ->
      fleetLanding(onEnterAircraft)

    tier.hasFullSidebar ->
      SidebarShell(
        state = state,
        onSelectSection = onSelectSection,
        onSelectAircraft = onSelectAircraft,
        onOpenSettings = onOpenSettings,
        onAddAircraft = onAddAircraft,
        onEditAircraft = onEditAircraft,
        content = content,
      )

    else ->
      ScaffoldShell(
        tier = tier,
        state = state,
        onSelectSection = onSelectSection,
        onSelectAircraft = onSelectAircraft,
        onOpenSettings = onOpenSettings,
        onExitToFleet = onExitToFleet,
        onEditAircraft = onEditAircraft,
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
  onEditAircraft: (() -> Unit)?,
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
        showBack = false,
        onExitToFleet = {},
        showTopBarSwitcher = false,
        onSelectAircraft = onSelectAircraft,
        onEditAircraft = onEditAircraft,
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
) {
  Surface(
    modifier = Modifier.fillMaxHeight()
      .width(264.dp),
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
          "Hopply",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      }

      SidebarSwitcher(
        state = state,
        onSelectAircraft = onSelectAircraft,
        onAddAircraft = onAddAircraft,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      )

      SidebarLabel("This aircraft")
      PER_AIRCRAFT_SECTIONS.forEach { section ->
        SidebarItem(
          section,
          selected = state.section == section,
          onClick = { onSelectSection(section) })
      }

      Spacer(Modifier.weight(1f))
      HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
      // Combined account + settings entry: the user's avatar and name; opens the Settings section.
      NavigationDrawerItem(
        label = {
          Text(
            accountLabel(state),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
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
private fun SidebarLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp),
  )
}

@Composable
private fun SidebarItem(
  section: ShellSection,
  selected: Boolean,
  onClick: () -> Unit
) {
  NavigationDrawerItem(
    label = { Text(section.label) },
    icon = { Icon(section.icon, contentDescription = null) },
    selected = selected,
    onClick = onClick,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
  )
}

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
/* MEDIUM (rail) + COMPACT entered (bottom bar) — NavigationSuiteScaffold                          */
/* ---------------------------------------------------------------------------------------------- */

@Composable
private fun ScaffoldShell(
  tier: LayoutTier,
  state: AdaptiveShellUiState,
  onSelectSection: (ShellSection) -> Unit,
  onSelectAircraft: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onExitToFleet: () -> Unit,
  onEditAircraft: (() -> Unit)?,
  content: @Composable () -> Unit,
) {
  val navType =
    if (tier == LayoutTier.COMPACT) NavigationSuiteType.NavigationBar else NavigationSuiteType.NavigationRail
  NavigationSuiteScaffold(
    layoutType = navType,
    navigationSuiteItems = {
      ShellSection.entries.forEach { s ->
        val isAccount = s == ShellSection.SETTINGS
        item(
          selected = s == state.section,
          onClick = if (isAccount) onOpenSettings else {
            { onSelectSection(s) }
          },
          icon = {
            if (isAccount) {
              AvatarIcon(
                displayName = state.accountName,
                photoUri = state.accountPhotoUrl,
                size = 28.dp,
              )
            } else {
              Icon(s.icon, contentDescription = null)
            }
          },
          label = {
            Text(
              if (isAccount) accountLabel(state) else s.label,
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
      showBack = tier == LayoutTier.COMPACT && state.entered,
      onExitToFleet = onExitToFleet,
      // The switcher lives in the top bar on both the rail (MEDIUM) and the bottom-bar (COMPACT)
      // tiers — neither has a sidebar to host it, and on COMPACT it's the only in-place way to
      // switch aircraft without returning to the fleet landing.
      showTopBarSwitcher = true,
      onSelectAircraft = onSelectAircraft,
      onEditAircraft = onEditAircraft,
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
  showBack: Boolean,
  onExitToFleet: () -> Unit,
  showTopBarSwitcher: Boolean,
  onSelectAircraft: (String) -> Unit,
  onEditAircraft: (() -> Unit)?,
  content: @Composable () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            if (state.section == ShellSection.SETTINGS) accountLabel(state) else state.section.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          if (showBack) {
            IconButton(onClick = onExitToFleet) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to fleet"
              )
            }
          }
        },
        actions = {
          // Edit the current aircraft (per-aircraft sections only); Settings is global.
          if (onEditAircraft != null && state.section != ShellSection.SETTINGS) {
            IconButton(onClick = onEditAircraft) {
              Icon(Icons.Filled.Edit, contentDescription = "Edit aircraft")
            }
          }
          if (showTopBarSwitcher) {
            TopBarSwitcher(state = state, onSelectAircraft = onSelectAircraft)
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

private fun accountLabel(state: AdaptiveShellUiState): String =
  state.accountName?.takeIf { it.isNotBlank() } ?: "Account"

@Composable
private fun TopBarSwitcher(
  state: AdaptiveShellUiState,
  onSelectAircraft: (String) -> Unit,
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
      onAddAircraft = null,
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
