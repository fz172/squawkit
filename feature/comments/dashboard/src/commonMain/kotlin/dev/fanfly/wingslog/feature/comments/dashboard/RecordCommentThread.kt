package dev.fanfly.wingslog.feature.comments.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.ui.sheet.LocalDetailSheetScrollState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.comments.datamanager.CommentThreadController
import dev.fanfly.wingslog.feature.comments.viewing.CommentThreadSection
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.comments.sharedassets.generated.resources.Res
import wingslog.feature.comments.sharedassets.generated.resources.comments_heading
import kotlin.time.Duration.Companion.milliseconds

/**
 * A detail sheet's Comments section. No count beside the heading: a total reads as "unread", and
 * the app has no read watermark to make that true (comments_design.md §2).
 *
 * After a post or when the composer takes focus, the enclosing scroll brings the end of the thread
 * into view so the newest comments are on screen above the composer/keyboard rather than below the fold.
 */
@Composable
fun RecordCommentThread(thread: CommentThreadController) {
  val state by thread.state.collectAsStateWithLifecycle()
  val threadEnd = remember { BringIntoViewRequester() }
  val scrollState = LocalDetailSheetScrollState.current
  LaunchedEffect(thread, scrollState) {
    thread.scrollToEnd.collectLatest {
      if (scrollState != null) {
        // While the keyboard opens or the list is laid out, maxValue increases.
        // Keep the scroll pinned to maxValue during the transition window (500ms).
        withTimeoutOrNull(500.milliseconds) {
          snapshotFlow { scrollState.maxValue }
            .collect { max ->
              scrollState.scrollTo(max)
            }
        }
      } else {
        withFrameNanos { }
        threadEnd.bringIntoView()
      }
    }
  }
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
    Spacer(Modifier.bringIntoViewRequester(threadEnd))
  }
}
