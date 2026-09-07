package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.ResolveBubbleMenu
import dev.fanfly.wingslog.core.ui.common.compose.ResolveMenuAction
import dev.fanfly.wingslog.core.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.dismiss_no_work_planned
import wingslog.feature.squawk.sharedassets.generated.resources.fixed_option_label

/** The Resolve bubble for a squawk: the user picks a resolution type (dismissed / fixed). */
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
