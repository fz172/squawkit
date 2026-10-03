package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.dashboard.generated.resources.Res
import wingslog.feature.tasks.dashboard.generated.resources.suggest_tasks_action
import wingslog.feature.tasks.dashboard.generated.resources.suggest_tasks_missing_identity
import wingslog.feature.tasks.dashboard.generated.resources.suggest_tasks_sign_in

/**
 * The task list's *Suggest tasks* action (PRD R2), in whichever state [entry] says. Hidden off
 * developer builds; for a guest it opens the account upgrade (R47); with required specs missing it
 * names them and opens the Thing's edit screen (R5). It stays enabled offline: the workflow says
 * "No internet connection" if a call fails (R51).
 */
@Composable
fun SuggestTasksAction(
  entry: SuggestEntry,
  onSuggest: () -> Unit,
  onSignIn: () -> Unit,
  onEditThing: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (entry is SuggestEntry.Hidden) return
  val taskNoun = LocalThingLexicon.current.taskNoun
  val note: String? = when (entry) {
    SuggestEntry.SignInRequired -> stringResource(Res.string.suggest_tasks_sign_in)
    is SuggestEntry.MissingIdentity ->
      stringResource(Res.string.suggest_tasks_missing_identity, entry.fieldLabels.joinToString(", "))
    else -> null
  }
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
    OutlinedButton(
      onClick = when (entry) {
        SuggestEntry.SignInRequired -> onSignIn
        is SuggestEntry.MissingIdentity -> onEditThing
        else -> onSuggest
      },
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = Spacing.small))
        Text(stringResource(Res.string.suggest_tasks_action, LexiconFormatter.plural(taskNoun)))
      }
    }
    if (note != null) {
      Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
