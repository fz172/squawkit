package dev.fanfly.wingslog.core.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
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
import dev.fanfly.wingslog.core.ui.common.compose.rememberLayoutTier

/** Lightweight aircraft projection used by the shell's switcher. */
data class ShellAircraft(
  val id: String,
  val tail: String,
  val name: String,
)

/** Plain UI state for [AdaptiveAppShell]; produced by a host-side ViewModel. */
data class AdaptiveShellUiState(
  val aircraft: List<ShellAircraft> = emptyList(),
  val selectedAircraftId: String? = null,
) {
  val selectedAircraft: ShellAircraft?
    get() = aircraft.firstOrNull { it.id == selectedAircraftId }
}

/**
 * Top-level sections of the adaptive shell. The first four are per-aircraft; [SETTINGS] is global.
 * Labels are placeholders for M1 and move to string resources when sections are re-hosted (M3).
 */
enum class ShellSection(val label: String, val icon: ImageVector) {
  DASHBOARD("Dashboard", Icons.Filled.Dashboard),
  TASKS("Tasks", Icons.Filled.Checklist),
  LOGS("Logs", Icons.Filled.Description),
  SQUAWKS("Squawks", Icons.Filled.Warning),
  SETTINGS("Settings", Icons.Filled.Settings),
}

/** Maps a [LayoutTier] to the navigation container the shell should show. */
private fun navTypeFor(tier: LayoutTier): NavigationSuiteType = when (tier) {
  LayoutTier.COMPACT -> NavigationSuiteType.NavigationBar
  LayoutTier.MEDIUM -> NavigationSuiteType.NavigationRail
  LayoutTier.EXPANDED, LayoutTier.LARGE -> NavigationSuiteType.NavigationDrawer
}

/**
 * The adaptive web/tablet shell (M1). Built on [NavigationSuiteScaffold], whose container reflows
 * bottom bar -> rail -> permanent drawer; the type is driven by [rememberLayoutTier] so the
 * breakpoints match the design prototype rather than Material's defaults.
 *
 * Pure UI: it takes plain [AdaptiveShellUiState] + callbacks so it can be hosted by both the
 * Android/iOS app (`AppEntry`) and the web app (`WebApp`). M1 renders placeholder section content
 * to verify the reflow and the switcher; real content, aircraft-scoped routing, and the phone
 * fleet-landing root arrive in M2/M3. See `docs/web/web_adaptive_layout_design.html`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveAppShell(
  state: AdaptiveShellUiState,
  onSelectAircraft: (String) -> Unit,
  onOpenSettings: () -> Unit,
) {
  var section by remember { mutableStateOf(ShellSection.DASHBOARD) }
  val tier = rememberLayoutTier()

  NavigationSuiteScaffold(
    layoutType = navTypeFor(tier),
    navigationSuiteItems = {
      ShellSection.entries.forEach { s ->
        item(
          selected = s == section,
          onClick = { section = s },
          icon = { Icon(s.icon, contentDescription = null) },
          label = { Text(s.label) },
        )
      }
    },
  ) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(section.label) },
          actions = {
            // The switcher lives in the top bar above phone; on COMPACT the (future) fleet
            // landing page handles aircraft selection instead.
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
          section = section,
          selected = state.selectedAircraft,
          onOpenSettings = onOpenSettings,
        )
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
      // M1 placeholder: keep the real settings (and Feature Lab toggle) reachable so a dogfooder
      // who enabled the shell can still turn it back off.
      Button(onClick = onOpenSettings) { Text("Open settings") }
    }
  }
}
