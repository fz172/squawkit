package dev.fanfly.wingslog.feature.tasks.update.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.common.compose.FormLockedNote
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.task_locked_reason

@Composable
internal fun IdentitySection(
  header: String,
  description: String,
  modifier: Modifier = Modifier,
  // Non-null when the choice was fixed at creation: the prompt to choose gives way to the reason.
  lockedReason: String? = null,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    FormSectionLabel(header)
    if (lockedReason == null) {
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
      )
    }
    content()
    if (lockedReason != null) FormLockedNote(lockedReason)
  }
}

@Composable
internal fun taskLockedReason(): String = stringResource(
  Res.string.task_locked_reason,
  LocalThingLexicon.current.taskNoun.singular,
)
