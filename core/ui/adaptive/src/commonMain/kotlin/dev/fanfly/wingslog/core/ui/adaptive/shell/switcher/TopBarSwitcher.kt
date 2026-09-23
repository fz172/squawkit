package dev.fanfly.wingslog.core.ui.adaptive.shell.switcher

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.fanfly.wingslog.core.ui.adaptive.shell.AdaptiveShellUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.switcher_select_thing
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/** COMPACT's switcher: the selected thing's name as a top-bar text button that opens the picker. */
@Composable
internal fun TopBarSwitcher(
  state: AdaptiveShellUiState,
  onSelectThing: (String) -> Unit,
  onAddThing: (() -> Unit)?,
  onEnterInviteCode: (() -> Unit)?,
) {
  var open by remember { mutableStateOf(false) }
  Box {
    TextButton(onClick = { open = true }) {
      Text(
        state.selectedThing?.label
          ?: stringResource(UiRes.string.switcher_select_thing)
      )
      Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
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
