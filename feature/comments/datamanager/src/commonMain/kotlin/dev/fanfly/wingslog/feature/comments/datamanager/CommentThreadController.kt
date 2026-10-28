package dev.fanfly.wingslog.feature.comments.datamanager

import dev.fanfly.wingslog.feature.comments.model.CommentAction
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.comments.model.CommentThreadState
import dev.fanfly.wingslog.id.CommentId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The comments tab's whole state machine, shared by the squawk and task edit forms the same way
 * `AttachmentFormController` is — so neither ViewModel re-implements drafts, the inline editor, or
 * the ⋮ menu.
 *
 * UI-free by design: a failed write surfaces as a [CommentAction] on [errors] and the owning
 * ViewModel maps it to its own user-facing string.
 *
 * [scope] is the owner's `viewModelScope`: the thread collection dies with the form, and there is
 * nothing to clean up afterwards — a posted comment is already persisted.
 */
class CommentThreadController(
  private val commentManager: CommentManager,
  private val target: CommentTarget,
  private val scope: CoroutineScope,
) {

  private val _state = MutableStateFlow(CommentThreadState())
  val state: StateFlow<CommentThreadState> = _state.asStateFlow()

  private val _errors = MutableSharedFlow<CommentAction>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  val errors: Flow<CommentAction> = _errors.asSharedFlow()

  private val _scrollToEnd = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /**
   * Fires when the thread should scroll its end into view — when a post lands or when the
   * composer takes focus so the newest comment stays visible above the keyboard.
   */
  val scrollToEnd: Flow<Unit> = _scrollToEnd.asSharedFlow()

  /** Backwards-compatible alias for [scrollToEnd]. */
  val posted: Flow<Unit> get() = scrollToEnd

  init {
    scope.launch {
      commentManager.observeComments(target)
        .collect { comments ->
          _state.update { prev ->
            // A comment can stop being editable under an open menu or an open editor — the author
            // deleted it on their other device and the tombstone synced down. Drop the transient
            // state rather than leaving an editor bound to a comment that can no longer be saved.
            val ids = comments.asSequence()
              .filter { !it.isDeleted }
              .mapTo(mutableSetOf()) { it.id }
            val editing = prev.editingId?.takeIf { it in ids }
            prev.copy(
              comments = comments,
              editingId = editing,
              editDraft = if (editing == null) "" else prev.editDraft,
              menuOpenId = prev.menuOpenId?.takeIf { it in ids },
            )
          }
        }
    }
  }

  // ── Composer ─────────────────────────────────────────────────────────────

  fun onDraftChange(value: String) = _state.update { it.copy(draft = value) }

  /**
   * Request scrolling the thread's end into view, e.g. when the composer takes focus.
   */
  fun onComposerFocus() {
    _scrollToEnd.tryEmit(Unit)
  }

  /**
   * Posts the draft, clearing it only once the write lands — a failed post leaves the words the
   * author typed in the box, which is the only place they still exist.
   */
  fun post() {
    val body = _state.value.draft.trim()
    if (body.isEmpty() || _state.value.isPosting) return
    val countBefore = _state.value.comments.size
    _state.update { it.copy(isPosting = true) }
    scope.launch {
      val result = commentManager.addComment(target, body)
      _state.update {
        it.copy(
          isPosting = false,
          draft = if (result.isSuccess) "" else it.draft,
        )
      }
      if (result.isFailure) {
        _errors.emit(CommentAction.POST)
        return@launch
      }
      // The write has landed, but the thread reaches the screen through the store's observer, a
      // hop behind it. Wait for the new row so the host scrolls to a comment that is laid out.
      // Deletes are tombstones, so the list never shrinks under this count.
      withTimeoutOrNull(POSTED_ROW_TIMEOUT) {
        _state.first { it.comments.size > countBefore }
      }
      _scrollToEnd.emit(Unit)
    }
  }

  // ── The ⋮ menu, offered only on your own comments ─────────────────────────

  fun toggleMenu(commentId: CommentId) = _state.update {
    it.copy(menuOpenId = if (it.menuOpenId == commentId) null else commentId)
  }

  fun dismissMenu() = _state.update { it.copy(menuOpenId = null) }

  fun delete(commentId: CommentId) {
    _state.update { it.copy(menuOpenId = null) }
    scope.launch {
      if (commentManager.deleteComment(target, commentId)
          .isFailure
      ) {
        _errors.emit(CommentAction.DELETE)
      }
    }
  }

  // ── Inline editor ────────────────────────────────────────────────────────

  fun startEdit(commentId: CommentId) = _state.update { prev ->
    val text = prev.comments.firstOrNull { it.id == commentId }?.text.orEmpty()
    prev.copy(menuOpenId = null, editingId = commentId, editDraft = text)
  }

  fun onEditDraftChange(value: String) =
    _state.update { it.copy(editDraft = value) }

  fun cancelEdit() = _state.update { it.copy(editingId = null, editDraft = "") }

  /**
   * Saves the edit, closing the editor only once the write lands — the same rule as [post]: a
   * failed save leaves the rewritten text where the author can still see it.
   */
  fun saveEdit() {
    val id = _state.value.editingId ?: return
    val body = _state.value.editDraft.trim()
    if (body.isEmpty()) return
    scope.launch {
      val result = commentManager.updateComment(target, id, body)
      if (result.isSuccess) {
        _state.update {
          if (it.editingId == id) it.copy(
            editingId = null,
            editDraft = ""
          ) else it
        }
      } else {
        _errors.emit(CommentAction.EDIT)
      }
    }
  }

  private companion object {
    val POSTED_ROW_TIMEOUT = 2.seconds
  }
}
