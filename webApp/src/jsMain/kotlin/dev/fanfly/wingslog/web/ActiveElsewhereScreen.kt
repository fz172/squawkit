package dev.fanfly.wingslog.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme

/**
 * Shown in a browser tab when the OPFS database is already owned by another tab (see [gateSingleTab]).
 * Renders with the app's theme + [EmptyState] so it matches the product instead of surfacing the raw
 * SQLite/OPFS crash. This tab never starts Koin or opens the database, so strings are hard-coded
 * (no resource environment is warmed here).
 */
@Composable
internal fun ActiveElsewhereScreen(onReload: () -> Unit) {
  WingslogTheme {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        EmptyState(
          title = "SquawkIt is open in another tab",
          description = "To keep your logbook safe, SquawkIt can only run in one tab at a time. " +
            "Please switch back to the tab where it's already open — or, if you've closed it, " +
            "reload this page to continue here.",
          icon = Icons.Outlined.Tab,
          actionText = "Reload",
          onActionClick = onReload,
          modifier = Modifier.widthIn(max = 460.dp),
        )
      }
    }
  }
}
