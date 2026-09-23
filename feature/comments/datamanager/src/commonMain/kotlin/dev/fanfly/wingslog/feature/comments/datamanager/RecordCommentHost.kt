package dev.fanfly.wingslog.feature.comments.datamanager

import dev.fanfly.wingslog.feature.comments.model.CommentAction
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.job

/**
 * The comment thread of whichever record's detail sheet is open. One [CommentThreadController] at
 * a time, in a scope of its own so closing the sheet stops its collection.
 *
 * A sheet closes on a stray tap outside it, so an unposted draft is held per record and handed
 * back when that record is opened again — the words exist nowhere else. They live as long as the
 * owning ViewModel does and no longer: a draft is not worth a table.
 */
class RecordCommentHost(
  private val commentManager: CommentManager,
  private val scope: CoroutineScope,
) {
  private val _thread = MutableStateFlow<CommentThreadController?>(null)
  val thread: StateFlow<CommentThreadController?> = _thread.asStateFlow()

  /** Failed writes of the open thread, for the owner to put into words. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val errors: Flow<CommentAction> =
    _thread.flatMapLatest { it?.errors ?: emptyFlow() }

  private var open: CommentTarget? = null
  private var threadScope: CoroutineScope? = null
  private var drafts = listOf<CommentDraft>()

  /** Null closes the thread. The same target twice is a no-op, so a refreshed record keeps its thread. */
  fun show(target: CommentTarget?) {
    if (target == open) return
    close()
    if (target == null) return
    val child =
      CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext.job))
    val controller = CommentThreadController(commentManager, target, child)
    drafts.firstOrNull { it.target == target }
      ?.let { controller.onDraftChange(it.text) }
    open = target
    threadScope = child
    _thread.value = controller
  }

  private fun close() {
    val target = open ?: return
    val text = _thread.value?.state?.value?.draft.orEmpty()
    drafts = drafts.filterNot { it.target == target } +
      listOfNotNull(CommentDraft(target, text).takeIf { text.isNotBlank() })
    threadScope?.cancel()
    threadScope = null
    open = null
    _thread.value = null
  }
}
