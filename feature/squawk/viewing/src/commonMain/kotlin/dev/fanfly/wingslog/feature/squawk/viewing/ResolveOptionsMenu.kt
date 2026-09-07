package dev.fanfly.wingslog.feature.squawk.update.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.ResolveBubbleMenu
import dev.fanfly.wingslog.core.ui.common.compose.ResolveMenuAction
import dev.fanfly.wingslog.core.ui.theme.statusColors
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
  ResolveBubbleMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    actions = listOf(
      ResolveMenuAction(
        icon = Icons.Default.Close,
        iconBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        label = stringResource(Res.string.dismiss_no_work_planned),
        onClick = onDismissNoWorkPlanned,
      ),
      ResolveMenuAction(
        icon = Icons.Default.Check,
        iconBackground = MaterialTheme.statusColors.positive.container,
        iconTint = MaterialTheme.statusColors.positive.accent,
        label = stringResource(Res.string.fixed_option_label),
        onClick = onFixedClick,
      ),
    ),
  )
}
