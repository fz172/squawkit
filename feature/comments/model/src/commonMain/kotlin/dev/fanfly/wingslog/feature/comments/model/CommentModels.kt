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
  /** The signed-in account wrote this one — the only person offered Edit and Delete. */
  val isMine: Boolean,
)

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
  val count: Int get() = comments.size
  val canPost: Boolean get() = draft.isNotBlank() && !isPosting
  val canSaveEdit: Boolean get() = editDraft.isNotBlank()
}

/** Which comment action failed, so the owning screen can say so in its own words. */
enum class CommentAction {
  POST,
  EDIT,
  DELETE,
}
