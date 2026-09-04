package dev.fanfly.wingslog.feature.comments.model

import kotlin.time.Instant

/**
 * Which kind of record a thread hangs off.
 *
 * Deliberately not the `CommentParentType` proto enum: `model` stays free of wire types, and the
 * mapping lives in one place in `datamanager` where the stored value is written.
 */
enum class CommentParentKind {
  SQUAWK,
  MAINTENANCE_TASK,
}

/** The one record a thread belongs to: a squawk or a task under a given Thing. */
data class CommentTarget(
  val thingId: String,
  val parentId: String,
  val kind: CommentParentKind,
)

/** One comment as the thread renders it. */
data class CommentEntry(
  val id: String,
  /** Denormalized at post time — see comment.proto. Blank when the author never had a name. */
  val authorName: String,
  val text: String,
  val createdAt: Instant,
  /** Non-null once the author has edited the text. */
  val editedAt: Instant?,
  /**
   * Non-null once the author has deleted it. The entry stays in the thread as a tombstone —
   * [text] is empty and the card says only that a comment was deleted, and when. See comment.proto
   * for why a delete never removes the row.
   */
  val deletedAt: Instant?,
  /** The signed-in account wrote this one — the only person offered Update and Delete. */
  val isMine: Boolean,
) {
  val isDeleted: Boolean get() = deletedAt != null

  /** Only a live comment of your own can be updated or deleted. */
  val isActionable: Boolean get() = isMine && !isDeleted
}

/**
 * Everything the comments tab draws, the in-progress draft and edit included.
 *
 * The draft lives here, in ViewModel-held state, rather than in a composable `remember`, so it
 * survives the tab being swiped away and back (#254).
 */
data class CommentThreadState(
  val comments: List<CommentEntry> = emptyList(),
  val draft: String = "",
  /** The comment currently open in the inline editor, if any. */
  val editingId: String? = null,
  val editDraft: String = "",
  /** The comment whose ⋮ menu is open, if any. */
  val menuOpenId: String? = null,
  /** A post is in flight — the send button is inert until it lands, so a double tap posts once. */
  val isPosting: Boolean = false,
) {
  /**
   * Live comments only. A tombstone still occupies a card in the thread, but it is not something
   * to go and read, and the badge is an at-a-glance "is there anything here".
   */
  val count: Int get() = comments.count { !it.isDeleted }
  val canPost: Boolean get() = draft.isNotBlank() && !isPosting
  val canSaveEdit: Boolean get() = editDraft.isNotBlank()

  /**
   * Typed text that has not been written anywhere yet — an unposted draft or an open editor. The
   * owning form folds this into its unsaved-changes gate, so backing out prompts for it exactly
   * as it would for an edited description.
   */
  val hasUnsavedInput: Boolean get() = draft.isNotBlank() || editingId != null
}

/** Which comment action failed, so the owning screen can say so in its own words. */
enum class CommentAction {
  POST,
  EDIT,
  DELETE,
}
