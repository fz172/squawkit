package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.ResolveBubbleMenu
import dev.fanfly.wingslog.core.ui.common.compose.ResolveMenuAction
import dev.fanfly.wingslog.core.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.create_work_log
import wingslog.feature.tasks.update.generated.resources.create_work_log_subtitle
import wingslog.feature.tasks.update.generated.resources.skip_this_cycle_option
import wingslog.feature.tasks.update.generated.resources.skip_this_cycle_option_subtitle

/**
 * A contextual menu displayed when the user taps "Resolve" on the task editing screen — the task
 * counterpart to squawk's ResolveOptionsMenu, sharing the same bubble presentation.
 */
@Composable
fun ResolveTaskOptionsMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  onCreateWorkLog: () -> Unit,
  onSkipThisCycle: () -> Unit,
) {
  ResolveBubbleMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    actions = listOf(
      ResolveMenuAction(
        icon = Icons.AutoMirrored.Filled.NoteAdd,
        iconBackground = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.primary,
        label = stringResource(Res.string.create_work_log),
        subtitle = stringResource(Res.string.create_work_log_subtitle),
        onClick = onCreateWorkLog,
      ),
      ResolveMenuAction(
        icon = Icons.Default.FastForward,
        iconBackground = MaterialTheme.statusColors.caution.container,
        iconTint = MaterialTheme.statusColors.caution.accent,
        label = stringResource(Res.string.skip_this_cycle_option),
        subtitle = stringResource(Res.string.skip_this_cycle_option_subtitle),
        onClick = onSkipThisCycle,
      ),
    ),
  )
}
