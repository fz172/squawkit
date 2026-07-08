package dev.fanfly.wingslog.feature.squawk.update.compose

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.update.generated.resources.Res
import wingslog.feature.squawk.update.generated.resources.dismiss_no_work_planned
import wingslog.feature.squawk.update.generated.resources.fixed_option_label

/**
 * A contextual menu displayed when the user clicks "Resolve" button in squawk
 * editing page. The menu asks the user to choose resolution
 * type (dismissed/fixed).
 */
@Composable
fun ResolveOptionsMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  onDismissNoWorkPlanned: () -> Unit,
  onFixedClick: () -> Unit,
) {
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
  ) {
    DropdownMenuItem(
      text = { Text(stringResource(Res.string.dismiss_no_work_planned)) },
      onClick = onDismissNoWorkPlanned,
    )
    DropdownMenuItem(
      text = { Text(stringResource(Res.string.fixed_option_label)) },
      onClick = onFixedClick,
    )
  }
}
