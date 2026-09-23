package dev.fanfly.wingslog.core.ui.adaptive.shell.switcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.AdaptiveShellUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.switcher_all_things
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/** "All 17 things" — the row that opens the picker for whatever the quick list left out. */
@Composable
internal fun AllThingsRow(
  count: Int,
  state: AdaptiveShellUiState,
  onSelectThing: (String) -> Unit,
  onAddThing: () -> Unit,
  onEnterInviteCode: (() -> Unit)?,
) {
  var open by remember { mutableStateOf(false) }
  Box {
    NavigationDrawerItem(
      label = { Text(stringResource(UiRes.string.switcher_all_things, count)) },
      icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
      selected = false,
      onClick = { open = true },
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
    ThingDropdown(
      expanded = open,
      onDismiss = { open = false },
      state = state,
      onSelectThing = onSelectThing,
      onAddThing = onAddThing,
      onEnterInviteCode = onEnterInviteCode,
    )
  }
}
