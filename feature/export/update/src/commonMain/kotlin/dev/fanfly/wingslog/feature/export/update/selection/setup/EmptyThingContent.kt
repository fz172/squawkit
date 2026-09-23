package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.feature.export.update.selection.result.ResultSecondaryButton
import dev.fanfly.wingslog.feature.export.update.selection.result.ResultShell
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.empty_add_thing
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_history_action
import wingslog.feature.export.sharedassets.generated.resources.export_no_thing_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun EmptyThingContent(
  modifier: Modifier,
  onNavigateToHistory: () -> Unit,
) {
  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.FileDownload,
    heroColor = MaterialTheme.colorScheme.primary,
    heroContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
    title = stringResource(Res.string.export_no_thing_title),
    subtitle = stringResource(
      CoreRes.string.empty_add_thing,
      LexiconFormatter.withArticle(LocalThingLexicon.current.thingNoun),
    ),
    body = {},
    actions = {
      ResultSecondaryButton(
        label = stringResource(Res.string.export_history_action),
        icon = Icons.Default.History,
        onClick = onNavigateToHistory,
      )
    },
  )
}
