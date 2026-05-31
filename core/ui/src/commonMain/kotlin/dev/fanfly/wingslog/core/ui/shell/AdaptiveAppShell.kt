package dev.fanfly.wingslog.core.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

/** Maps a [LayoutTier] to the navigation container the shell should show. */
private fun navTypeFor(tier: LayoutTier): NavigationSuiteType = when (tier) {
  LayoutTier.COMPACT -> NavigationSuiteType.NavigationBar
  LayoutTier.MEDIUM -> NavigationSuiteType.NavigationRail
  LayoutTier.EXPANDED, LayoutTier.LARGE -> NavigationSuiteType.NavigationDrawer
}

/**
 * The adaptive web/tablet shell. Built on [NavigationSuiteScaffold], whose container reflows
 * bottom bar -> rail -> permanent drawer; the type is driven by [rememberLayoutTier] so the
 * breakpoints match the design prototype.
 *
 * Navigation model (M2): the selected aircraft is **ambient** app-level state chosen from the
 * switcher above phone — switching swaps content in place, no push/pop. On COMPACT there is no room
 * for a switcher, so the fleet-landing root is shown until an aircraft is opened
 * ([onEnterAircraft]); the back arrow returns to it. Section content is still a placeholder until
 * M3. See `docs/web/web_adaptive_layout_design.html`.
 *
 * The tier is derived from the measured [BoxWithConstraints] width rather than `LocalWindowInfo`,
 * because the latter does not reliably report the window width on Kotlin/JS — which would otherwise
 * collapse the web layout to the COMPACT single-column path on wide windows.
 *
 * Pure UI: takes plain [AdaptiveShellUiState] + callbacks so both hosts (`AppEntry`, `WebApp`) can
 * use it.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // COMPACT root is the fleet landing until an aircraft is opened; above phone the switcher is the
    // fleet overview and sections render directly.
    if (tier == LayoutTier.COMPACT && !state.entered) {
      FleetLanding(
        aircraft = state.aircraft,
        onEnterAircraft = onEnterAircraft,
        onAddAircraft = onAddAircraft,
        onOpenSettings = onOpenSettings,
      )
    } else {
      NavigationSuiteScaffold(
        layoutType = navTypeFor(tier),
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
        Scaffold(
          topBar = {
            TopAppBar(
              title = { Text(state.section.label) },
              navigationIcon = {
                // On phone, offer a way back to the fleet landing from a section.
                if (tier == LayoutTier.COMPACT && state.entered) {
                  IconButton(onClick = onExitToFleet) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to fleet")
                  }
                }
              },
              actions = {
                // The switcher lives in the top bar above phone; COMPACT uses the fleet landing.
                if (tier.hasSideNav) {
                  AircraftSwitcher(state = state, onSelectAircraft = onSelectAircraft)
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
    }
  }
}

@Composable
private fun AircraftSwitcher(
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
