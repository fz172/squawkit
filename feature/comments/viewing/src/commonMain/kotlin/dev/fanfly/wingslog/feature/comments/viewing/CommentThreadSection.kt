package dev.fanfly.wingslog.feature.comments.viewing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.fanfly.wingslog.core.ui.list.ListRowDivider
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.comments.model.CommentEntry
import dev.fanfly.wingslog.feature.comments.model.CommentThreadState
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.comments.sharedassets.generated.resources.Res
import wingslog.feature.comments.sharedassets.generated.resources.comments_empty

/**
 * A record's comments, oldest first. The box they are written in is [CommentComposer].
 *
 * Stateless. Every mutation goes back out through a callback to
 * `CommentThreadController`, which is what lets the squawk sheet and the task sheet render the same
 * thread without either of them owning any of this.
 */
@Composable
fun CommentThreadSection(
  state: CommentThreadState,
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

  Column(modifier = modifier.fillMaxWidth()) {
    if (state.comments.isEmpty()) {
      Text(
        text = stringResource(Res.string.comments_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      state.comments.forEachIndexed { index, comment ->
        if (index > 0) ListRowDivider()
        CommentRow(
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

  }
}

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
            authorName = "Teo Varga",
            authorPhotoUrl = "https://example.invalid/teo.jpg",
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
