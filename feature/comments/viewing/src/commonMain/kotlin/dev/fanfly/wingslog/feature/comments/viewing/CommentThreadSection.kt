package dev.fanfly.wingslog.feature.comments.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayDateTime
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.DropdownMenu
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.comments.model.CommentEntry
import dev.fanfly.wingslog.feature.comments.model.CommentThreadState
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.save
import wingslog.core.sharedassets.generated.resources.unknown
import wingslog.feature.comments.sharedassets.generated.resources.Res
import wingslog.feature.comments.sharedassets.generated.resources.comment_actions
import wingslog.feature.comments.sharedassets.generated.resources.comment_delete_message
import wingslog.feature.comments.sharedassets.generated.resources.comment_delete_title
import wingslog.feature.comments.sharedassets.generated.resources.comment_deleted
import wingslog.feature.comments.sharedassets.generated.resources.comment_edit
import wingslog.feature.comments.sharedassets.generated.resources.comment_edited
import wingslog.feature.comments.sharedassets.generated.resources.comment_new_label
import wingslog.feature.comments.sharedassets.generated.resources.comment_placeholder
import wingslog.feature.comments.sharedassets.generated.resources.comment_post
import wingslog.feature.comments.sharedassets.generated.resources.comment_you
import wingslog.feature.comments.sharedassets.generated.resources.comments_empty
import wingslog.feature.comments.sharedassets.generated.resources.sign_in_to_add_comments
import kotlin.time.Instant
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The Comments tab: the thread, oldest first, followed by the composer.
 *
 * Stateless. Every mutation goes back out through a callback to
 * `CommentThreadController`, which is what lets the squawk form and the task form render the same
 * thread without either of them owning any of this.
 */
@Composable
fun CommentThreadSection(
  state: CommentThreadState,
  /**
   * A guest account is fully offline and its uid does not survive a merge into an existing
   * account (the migrator rewrites scope paths, not payloads), so a comment it posted would be
   * nobody's afterwards — no menu, no edit, no delete. The thread stays readable; the composer
   * is replaced by a sign-in line, the same as attachments.
   */
  isAnonymous: Boolean,
  onDraftChange: (String) -> Unit,
  onPost: () -> Unit,
  onToggleMenu: (String) -> Unit,
  onDismissMenu: () -> Unit,
  onEdit: (String) -> Unit,
  onDelete: (String) -> Unit,
  onEditDraftChange: (String) -> Unit,
  onCancelEdit: () -> Unit,
  onSaveEdit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Which comment the Delete item is asking about. Transient — the dialog is either on screen
  // or it is not — so a composable remember is the right home, as it is for the other confirm
  // dialogs in the app.
  var pendingDeleteId by remember { mutableStateOf<String?>(null) }
  pendingDeleteId?.let { id ->
    DeleteCommentConfirmDialog(
      onConfirm = { pendingDeleteId = null; onDelete(id) },
      onDismiss = { pendingDeleteId = null },
    )
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    if (state.comments.isEmpty()) {
      Text(
        text = stringResource(Res.string.comments_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      state.comments.forEach { comment ->
        CommentCard(
          comment = comment,
          menuOpen = state.menuOpenId == comment.id,
          isEditing = state.editingId == comment.id,
          editDraft = state.editDraft,
          canSaveEdit = state.canSaveEdit,
          onToggleMenu = { onToggleMenu(comment.id) },
          onDismissMenu = onDismissMenu,
          onEdit = { onEdit(comment.id) },
          onDelete = { onDismissMenu(); pendingDeleteId = comment.id },
          onEditDraftChange = onEditDraftChange,
          onCancelEdit = onCancelEdit,
          onSaveEdit = onSaveEdit,
        )
      }
    }

    if (isAnonymous) {
      Text(
        text = stringResource(Res.string.sign_in_to_add_comments),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FormTextField(
          label = stringResource(Res.string.comment_new_label),
          value = state.draft,
          placeholder = stringResource(Res.string.comment_placeholder),
          singleLine = false,
          minLines = 2,
          onValueChange = onDraftChange,
          modifier = Modifier.weight(1f),
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
  }
}

/**
 * Deleting is the one comment action that cannot be undone — the tombstone is final by design —
 * and the item sits one row under "Update comment". Every other destructive action in the app
 * confirms; this one has more reason to than most.
 */
@Composable
private fun DeleteCommentConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.comment_delete_title)) },
    text = { Text(stringResource(Res.string.comment_delete_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(
          text = stringResource(CoreRes.string.delete),
          color = MaterialTheme.colorScheme.error,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) }
    },
  )
}

@Composable
private fun CommentCard(
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
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        AuthorAvatar(comment.authorName)
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
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
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
}

/**
 * What a deleted comment says instead of its text. Struck through and muted, so it reads as a gap
 * in the conversation rather than as something to go and read — but it stays in place, because the
 * record that a comment was made and withdrawn is the point. See comment.proto.
 */
@Composable
private fun DeletedBody(deletedAt: Instant?) {
  Text(
    text = stringResource(
      Res.string.comment_deleted,
      deletedAt?.toDisplayDateTime().orEmpty(),
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textDecoration = TextDecoration.LineThrough,
    fontStyle = FontStyle.Italic,
  )
}

@Composable
private fun AuthorAvatar(authorName: String) {
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier.size(Spacing.huge),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = authorName.initials(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

@Composable
private fun MineBadge() {
  Surface(
    shape = RoundedCornerShape(Spacing.badgeCornerRadius),
    color = MaterialTheme.colorScheme.primaryContainer,
  ) {
    Text(
      text = stringResource(Res.string.comment_you).uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier = Modifier.padding(
        horizontal = Spacing.small,
        vertical = 1.dp,
      ),
    )
  }
}

/**
 * Up to two initials from a display name. Falls back to `?` rather than an empty circle, so an
 * unnamed author still reads as a person rather than as a rendering fault.
 */
internal fun String.initials(): String =
  split(' ')
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString("")
    .ifEmpty { "?" }

@Preview(showBackground = true)
@Composable
private fun CommentThreadSectionPreview() {
  WingslogTheme {
    CommentThreadSection(
      state = CommentThreadState(
        comments = listOf(
          CommentEntry(
            id = "c1",
            authorName = "Maria Delgado",
            text = "Torque values for the fairing screws are in SB 2X-57-01, not the AMM — " +
              "double-check before install.",
            createdAt = Instant.fromEpochSeconds(1_787_000_000),
            editedAt = null,
            deletedAt = null,
            isMine = false,
          ),
          CommentEntry(
            id = "c2",
            authorName = "Fan Zhang",
            text = "Holding this until the 100-hr next week so we only pull the panel once.",
            createdAt = Instant.fromEpochSeconds(1_787_100_000),
            editedAt = Instant.fromEpochSeconds(1_787_100_600),
            deletedAt = null,
            isMine = true,
          ),
          CommentEntry(
            id = "c3",
            authorName = "Tom Okafor",
            text = "",
            createdAt = Instant.fromEpochSeconds(1_787_200_000),
            editedAt = null,
            deletedAt = Instant.fromEpochSeconds(1_787_260_000),
            isMine = false,
          ),
        ),
      ),
      isAnonymous = false,
      onDraftChange = {},
      onPost = {},
      onToggleMenu = {},
      onDismissMenu = {},
      onEdit = {},
      onDelete = {},
      onEditDraftChange = {},
      onCancelEdit = {},
      onSaveEdit = {},
      modifier = Modifier.padding(Spacing.screenPadding),
    )
  }
}
