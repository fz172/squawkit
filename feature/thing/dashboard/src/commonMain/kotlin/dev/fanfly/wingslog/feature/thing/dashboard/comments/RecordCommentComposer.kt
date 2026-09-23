package dev.fanfly.wingslog.feature.thing.dashboard.comments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.feature.comments.datamanager.CommentThreadController
import dev.fanfly.wingslog.feature.comments.viewing.CommentComposer

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
