package dev.fanfly.wingslog.feature.comments.datamanager

import dev.fanfly.wingslog.feature.comments.model.CommentEntry
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import kotlinx.coroutines.flow.Flow

/**
 * Comments on one record — a squawk or a maintenance task — under a Thing.
 *
 * Reads and writes the local `comment` [dev.fanfly.wingslog.core.storage.EntityStore]; the sync
 * engine carries the rows to and from the host's tree. Everything a member with access can see is
 * therefore already on disk before this is asked for it, so the thread renders offline.
 */
interface CommentManager {
  /** Oldest first — the reading order of a conversation. */
  fun observeComments(target: CommentTarget): Flow<List<CommentEntry>>

  /**
   * Posts [text] as the signed-in account, stamping the author's uid and display name.
   *
   * Blank text is a no-op success — the composer already refuses to send it, and a caller
   * double-tapping Post should not produce an empty row.
   */
  suspend fun addComment(target: CommentTarget, text: String): Result<Unit>

  /**
   * Rewrites [commentId]'s text and stamps `edited_at`.
   *
   * Fails when the caller did not write the comment: the ⋮ menu is only offered to the author, and
   * this is the check behind it rather than beside it.
   */
  suspend fun updateComment(
    target: CommentTarget,
    commentId: String,
    text: String,
  ): Result<Unit>

  /**
   * Tombstones [commentId] — clears the text, stamps `deleted_at`, keeps the row. Same authorship
   * gate as [updateComment]. See comment.proto for why this never removes the row.
   */
  suspend fun deleteComment(
    target: CommentTarget,
    commentId: String,
  ): Result<Unit>

  /**
   * Removes every comment under [target] — the one place a comment row actually goes away.
   *
   * Called by the squawk and task managers when the parent record is deleted: a thread with no
   * record to hang off would otherwise sit in every member's store and in Firestore forever,
   * re-hydrated on each sign-in, and a form still open on the deleted record would keep posting
   * into it. No authorship gate — whoever may delete the parent may take its thread with it.
   */
  suspend fun deleteThread(target: CommentTarget): Result<Unit>
}
