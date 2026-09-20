package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.comments.datamanager.CommentThreadController
import dev.fanfly.wingslog.feature.comments.viewing.CommentComposer
import dev.fanfly.wingslog.feature.comments.viewing.CommentThreadSection
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.comments.sharedassets.generated.resources.Res
import wingslog.feature.comments.sharedassets.generated.resources.comments_heading

/**
 * A detail sheet's Comments section. No count beside the heading: a total reads as "unread", and
 * the app has no read watermark to make that true (comments_design.md §2).
 */
@Composable
internal fun RecordCommentThread(thread: CommentThreadController) {
  val state by thread.state.collectAsStateWithLifecycle()
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = stringResource(Res.string.comments_heading),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    CommentThreadSection(
      state = state,
      onToggleMenu = thread::toggleMenu,
      onDismissMenu = thread::dismissMenu,
      onEdit = thread::startEdit,
      onDelete = thread::delete,
      onEditDraftChange = thread::onEditDraftChange,
      onCancelEdit = thread::cancelEdit,
      onSaveEdit = thread::saveEdit,
    )
  }
}

/** The box pinned under the sheet. Posting is immediate; nothing here waits for a Save. */
@Composable
internal fun RecordCommentComposer(
  thread: CommentThreadController,
  isAnonymous: Boolean
) {
  val state by thread.state.collectAsStateWithLifecycle()
  CommentComposer(
    state = state,
    isAnonymous = isAnonymous,
    onDraftChange = thread::onDraftChange,
    onPost = thread::post,
  )
}
