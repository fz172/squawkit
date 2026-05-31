package dev.fanfly.wingslog.core.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.common.compose.layoutTierFor

/** Lightweight aircraft projection used by the shell's switcher and fleet landing. */
data class ShellAircraft(
  val id: String,
  val tail: String,
  val name: String,
)

/**
 * Top-level sections of the adaptive shell. The first four are per-aircraft; [SETTINGS] is global.
 * Labels are placeholders for M1/M2 and move to string resources when sections are re-hosted (M3).
 */
enum class ShellSection(val label: String, val icon: ImageVector) {
  DASHBOARD("Dashboard", Icons.Filled.Dashboard),
  TASKS("Tasks", Icons.Filled.Checklist),
  LOGS("Logs", Icons.Filled.Description),
  SQUAWKS("Squawks", Icons.Filled.Warning),
  SETTINGS("Settings", Icons.Filled.Settings),
}

private val PER_AIRCRAFT_SECTIONS =
  listOf(ShellSection.DASHBOARD, ShellSection.TASKS, ShellSection.LOGS, ShellSection.SQUAWKS)

/** Plain UI state for [AdaptiveAppShell]; produced by a host-side ViewModel. */
data class AdaptiveShellUiState(
  val aircraft: List<ShellAircraft> = emptyList(),
  val selectedAircraftId: String? = null,
  val section: ShellSection = ShellSection.DASHBOARD,
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
 * - **COMPACT** — fleet-landing root until an aircraft is opened ([onEnterAircraft]); then sections
 *   with a bottom bar and a back arrow to the fleet.
 *
 * Tier is derived from the measured [BoxWithConstraints] width (not `LocalWindowInfo`, which is
 * unreliable on Kotlin/JS). Section content is a placeholder until M3. Pure UI: plain state +
 * callbacks so both hosts (`AppEntry`, `WebApp`) can use it.
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
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val tier = layoutTierFor(maxWidth)
    when {
      tier == LayoutTier.COMPACT && !state.entered ->
        FleetLanding(
          aircraft = state.aircraft,
          onEnterAircraft = onEnterAircraft,
          onAddAircraft = onAddAircraft,
          onOpenSettings = onOpenSettings,
        )

      tier.hasFullSidebar ->
        SidebarShell(
          state = state,
          onSelectSection = onSelectSection,
          onSelectAircraft = onSelectAircraft,
          onOpenSettings = onOpenSettings,
          onAddAircraft = onAddAircraft,
        )

      else ->
        ScaffoldShell(
          tier = tier,
          state = state,
          onSelectSection = onSelectSection,
          onSelectAircraft = onSelectAircraft,
          onExitToFleet = onExitToFleet,
          onOpenSettings = onOpenSettings,
        )
    }
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
    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
      ShellContent(
        state = state,
        showBack = false,
        onExitToFleet = {},
        showTopBarSwitcher = false,
        onSelectAircraft = onSelectAircraft,
        onOpenSettings = onOpenSettings,
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
    modifier = Modifier.fillMaxHeight().width(264.dp),
    color = MaterialTheme.colorScheme.surface,
  ) {
    Column(modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp)) {
      // Brand
      Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Filled.Flight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
          "Hopply",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
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
        SidebarItem(section, selected = state.section == section, onClick = { onSelectSection(section) })
      }

      Spacer(Modifier.height(8.dp))
      SidebarLabel("Workspace")
      SidebarItem(
        ShellSection.SETTINGS,
        selected = state.section == ShellSection.SETTINGS,
        onClick = { onSelectSection(ShellSection.SETTINGS) },
      )

      Spacer(Modifier.weight(1f))
      HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
      NavigationDrawerItem(
        label = { Text("Account") },
        icon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
        selected = false,
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
private fun SidebarItem(section: ShellSection, selected: Boolean, onClick: () -> Unit) {
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
          state.selectedAircraft?.name?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
      }
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
            open = false
          },
          trailingIcon = {
            if (ac.id == state.selectedAircraftId) {
              Icon(Icons.Filled.Check, contentDescription = null)
            }
          },
        )
      }
      HorizontalDivider()
      DropdownMenuItem(
        text = { Text("Add aircraft") },
        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
        onClick = {
          onAddAircraft()
          open = false
        },
      )
    }
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
  onExitToFleet: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val navType =
    if (tier == LayoutTier.COMPACT) NavigationSuiteType.NavigationBar else NavigationSuiteType.NavigationRail
  NavigationSuiteScaffold(
    layoutType = navType,
    navigationSuiteItems = {
      ShellSection.entries.forEach { s ->
        item(
          selected = s == state.section,
          onClick = { onSelectSection(s) },
          icon = { Icon(s.icon, contentDescription = null) },
          label = { Text(s.label) },
        )
      }
    },
  ) {
    ShellContent(
      state = state,
      showBack = tier == LayoutTier.COMPACT && state.entered,
      onExitToFleet = onExitToFleet,
      showTopBarSwitcher = tier == LayoutTier.MEDIUM,
      onSelectAircraft = onSelectAircraft,
      onOpenSettings = onOpenSettings,
    )
  }
}

/* ---------------------------------------------------------------------------------------------- */
/* Shared content: top bar + section placeholder                                                  */
/* ---------------------------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellContent(
  state: AdaptiveShellUiState,
  showBack: Boolean,
  onExitToFleet: () -> Unit,
  showTopBarSwitcher: Boolean,
  onSelectAircraft: (String) -> Unit,
  onOpenSettings: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(state.section.label) },
        navigationIcon = {
          if (showBack) {
            IconButton(onClick = onExitToFleet) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to fleet")
            }
          }
        },
        actions = {
          if (showTopBarSwitcher) {
            TopBarSwitcher(state = state, onSelectAircraft = onSelectAircraft)
          }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.Center,
    ) {
      SectionPlaceholder(
        section = state.section,
        selected = state.selectedAircraft,
        onOpenSettings = onOpenSettings,
      )
    }
  }
}

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
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
            open = false
          },
          trailingIcon = {
            if (ac.id == state.selectedAircraftId) {
              Icon(Icons.Filled.Check, contentDescription = null)
            }
          },
        )
      }
    }
  }
}

/* ---------------------------------------------------------------------------------------------- */
/* COMPACT root: fleet landing                                                                     */
/* ---------------------------------------------------------------------------------------------- */

/**
 * Phone-only root: the fleet list. M2 placeholder built from [AdaptiveShellUiState.aircraft]; M3
 * swaps in the real `DashboardScreen` via a host slot (design D1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FleetLanding(
  aircraft: List<ShellAircraft>,
  onEnterAircraft: (String) -> Unit,
  onAddAircraft: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Fleet") },
        actions = {
          IconButton(onClick = onAddAircraft) {
            Icon(Icons.Filled.Add, contentDescription = "Add aircraft")
          }
          IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
          }
        },
      )
    },
  ) { padding ->
    if (aircraft.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
      ) {
        Text("No aircraft yet", style = MaterialTheme.typography.bodyMedium)
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        items(aircraft, key = { it.id }) { ac ->
          ListItem(
            headlineContent = { Text(ac.tail) },
            supportingContent = { if (ac.name.isNotBlank()) Text(ac.name) },
            modifier = Modifier.clickable { onEnterAircraft(ac.id) },
          )
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
private fun SectionPlaceholder(
  section: ShellSection,
  selected: ShellAircraft?,
  onOpenSettings: () -> Unit,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(section.icon, contentDescription = null)
    Text(section.label, style = MaterialTheme.typography.headlineSmall)
    val subtitle = when (section) {
      ShellSection.SETTINGS -> "Global workspace settings"
      else -> selected?.let { "${it.tail} · ${it.name}" } ?: "No aircraft selected"
    }
    Text(
      subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (section == ShellSection.SETTINGS) {
      // M1/M2 placeholder: keep the real settings (and Feature Lab toggle) reachable so a dogfooder
      // who enabled the shell can still turn it back off.
      Button(onClick = onOpenSettings) { Text("Open settings") }
    }
  }
}
