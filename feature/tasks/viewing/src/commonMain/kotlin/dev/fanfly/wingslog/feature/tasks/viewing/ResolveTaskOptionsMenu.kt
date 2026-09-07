package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.ui.common.compose.ResolveBubbleMenu
import dev.fanfly.wingslog.core.ui.common.compose.ResolveMenuAction
import dev.fanfly.wingslog.core.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.Res
import wingslog.feature.tasks.sharedassets.generated.resources.create_work_log
import wingslog.feature.tasks.sharedassets.generated.resources.create_work_log_subtitle
import wingslog.feature.tasks.sharedassets.generated.resources.skip_this_cycle_option
import wingslog.feature.tasks.sharedassets.generated.resources.skip_this_cycle_option_subtitle

/**
 * The Resolve bubble for a task (Create work log / Skip this cycle) — the task counterpart to
 * squawk's ResolveOptionsMenu, sharing the same bubble presentation.
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
        label = stringResource(
          Res.string.create_work_log,
          LexiconFormatter.titleCase(LocalThingLexicon.current.logNoun),
        ),
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
