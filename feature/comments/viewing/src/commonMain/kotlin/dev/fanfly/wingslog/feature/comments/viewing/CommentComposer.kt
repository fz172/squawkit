package dev.fanfly.wingslog.feature.comments.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.form.FormKeyboard
import dev.fanfly.wingslog.core.ui.form.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.comments.model.CommentThreadState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.comments.sharedassets.generated.resources.Res
import wingslog.feature.comments.sharedassets.generated.resources.comment_new_label
import wingslog.feature.comments.sharedassets.generated.resources.comment_placeholder
import wingslog.feature.comments.sharedassets.generated.resources.comment_post
import wingslog.feature.comments.sharedassets.generated.resources.sign_in_to_add_comments

/**
 * Where a comment is written. Separate from [CommentThreadSection] so a host can pin it under a
 * scrolling thread — it has to stay reachable however long the thread gets.
 *
 * A guest account is fully offline and its uid does not survive a merge into an existing account
 * (the migrator rewrites scope paths, not payloads), so a comment it posted would be nobody's
 * afterwards — no menu, no edit, no delete. The thread stays readable; the box is replaced by a
 * sign-in line, the same as attachments.
 */
@Composable
fun CommentComposer(
  state: CommentThreadState,
  isAnonymous: Boolean,
  onDraftChange: (String) -> Unit,
  onPost: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (isAnonymous) {
    Text(
      text = stringResource(Res.string.sign_in_to_add_comments),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = modifier,
    )
    return
  }
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FormTextField(
      label = stringResource(Res.string.comment_new_label),
      value = state.draft,
      placeholder = stringResource(Res.string.comment_placeholder),
      singleLine = false,
      maxLines = 4,
      onValueChange = onDraftChange,
      modifier = Modifier.weight(1f),
      keyboardOptions = FormKeyboard.Sentences,
    )
    FilledIconButton(
      onClick = onPost,
      enabled = state.canPost,
      modifier = Modifier.size(Spacing.buttonHeight),
      shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    ) {
      Icon(
        Icons.AutoMirrored.Filled.Send,
        contentDescription = stringResource(Res.string.comment_post),
        modifier = Modifier.size(Spacing.large),
      )
    }
  }
}
