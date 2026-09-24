package dev.fanfly.wingslog.feature.comments.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.datetime.toDisplayDateTime
import dev.fanfly.wingslog.core.ui.avatar.AvatarIcon
import dev.fanfly.wingslog.core.ui.form.FormTextField
import dev.fanfly.wingslog.core.ui.popup.DropdownMenu
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.comments.model.CommentEntry
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.save
import wingslog.core.sharedassets.generated.resources.unknown
import wingslog.feature.comments.sharedassets.generated.resources.Res
import wingslog.feature.comments.sharedassets.generated.resources.comment_actions
import wingslog.feature.comments.sharedassets.generated.resources.comment_edit
import wingslog.feature.comments.sharedassets.generated.resources.comment_edited
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun CommentRow(
  comment: CommentEntry,
  menuOpen: Boolean,
  isEditing: Boolean,
  editDraft: String,
  canSaveEdit: Boolean,
  onToggleMenu: () -> Unit,
  onDismissMenu: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onEditDraftChange: (String) -> Unit,
  onCancelEdit: () -> Unit,
  onSaveEdit: () -> Unit,
) {
  // Flat, like every other row since UI-2: a hairline between comments does the separating a
  // bordered card used to.
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.medium),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      AvatarIcon(
        displayName = comment.authorName,
        photoUri = comment.authorPhotoUrl,
        size = Spacing.huge,
      )
      Column(modifier = Modifier.weight(1f)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = comment.authorName.ifBlank { stringResource(CoreRes.string.unknown) },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (comment.isMine) MineBadge()
        }
        Text(
          text = comment.createdAt.toDisplayDateTime(),
          style = WingslogTypography.dataSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        comment.editedAt?.takeIf { !comment.isDeleted }
          ?.let { editedAt ->
            // Caution, the app's "something changed here" tone — the same amber the due-state
            // language uses. Never a fourth colour invented for this one line.
            val editedTone = MaterialTheme.statusColors.caution.accent
            Row(
              horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = editedTone,
                modifier = Modifier.size(Spacing.medium),
              )
              Text(
                text = stringResource(
                  Res.string.comment_edited,
                  editedAt.toDisplayDateTime()
                ),
                style = WingslogTypography.dataSmall,
                color = editedTone,
              )
            }
          }
      }
      if (comment.isActionable) {
        Box {
          IconButton(onClick = onToggleMenu) {
            Icon(
              Icons.Default.MoreVert,
              contentDescription = stringResource(Res.string.comment_actions),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = onDismissMenu
          ) {
            DropdownMenuItem(
              text = { Text(stringResource(Res.string.comment_edit)) },
              leadingIcon = {
                Icon(
                  Icons.Default.Edit,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              },
              onClick = onEdit,
            )
            DropdownMenuItem(
              text = {
                Text(
                  text = stringResource(CoreRes.string.delete),
                  color = MaterialTheme.colorScheme.error,
                )
              },
              leadingIcon = {
                Icon(
                  Icons.Default.Delete,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
              },
              onClick = onDelete,
            )
          }
        }
      }
    }

    if (comment.isDeleted) {
      DeletedBody(comment.deletedAt)
    } else if (isEditing) {
      FormTextField(
        label = stringResource(Res.string.comment_edit),
        value = editDraft,
        singleLine = false,
        minLines = 3,
        onValueChange = onEditDraftChange,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
          Spacing.small,
          Alignment.End
        ),
      ) {
        TextButton(onClick = onCancelEdit) {
          Text(stringResource(CoreRes.string.cancel))
        }
        TextButton(onClick = onSaveEdit, enabled = canSaveEdit) {
          Text(stringResource(CoreRes.string.save))
        }
      }
    } else {
      Text(
        text = comment.text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

  }
}
