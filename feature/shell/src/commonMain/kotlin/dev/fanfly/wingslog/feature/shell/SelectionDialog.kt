package dev.fanfly.wingslog.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.fanfly.wingslog.core.ui.adaptive.compose.TextSelectionLayer

/**
 * A dialog destination that starts its own text-selection scope. The only way to register one:
 * the `no-raw-popups` hook rejects the raw `dialog(...)` builder. Why: see [TextSelectionLayer].
 */
fun NavGraphBuilder.selectionDialog(
  route: String,
  arguments: List<NamedNavArgument> = emptyList(),
  dialogProperties: DialogProperties = DialogProperties(),
  content: @Composable (NavBackStackEntry) -> Unit,
) {
  dialog(route = route, arguments = arguments, dialogProperties = dialogProperties) { entry ->
    TextSelectionLayer { content(entry) }
  }
}
