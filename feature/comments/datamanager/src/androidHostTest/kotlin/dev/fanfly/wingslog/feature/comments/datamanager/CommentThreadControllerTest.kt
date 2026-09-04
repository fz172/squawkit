package dev.fanfly.wingslog.feature.comments.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.comments.model.CommentAction
import dev.fanfly.wingslog.feature.comments.model.CommentEntry
import dev.fanfly.wingslog.feature.comments.model.CommentParentKind
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CommentThreadControllerTest {

  private val target =
    CommentTarget("thing-1", "squawk-1", CommentParentKind.SQUAWK)

  @Test
  fun post_clearsTheDraftOnlyWhenTheWriteLands() = runTest {
    val manager = FakeCommentManager()
    val controller = controllerOn(manager)

    controller.onDraftChange("Torque values are in SB 2X-57-01")
    controller.post()

    assertThat(manager.added).containsExactly("Torque values are in SB 2X-57-01")
    assertThat(controller.state.value.draft).isEmpty()
  }

  @Test
  fun post_keepsTheDraftAndReportsWhenTheWriteFails() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = FakeCommentManager(failing = true)
      val controller = controllerOn(manager)
      val seen = mutableListOf<CommentAction>()
      backgroundScope.launch { controller.errors.collect { seen += it } }
      controller.onDraftChange("worth keeping")

      controller.post()

      // The words the author typed exist nowhere else — dropping them on failure loses the comment.
      assertThat(controller.state.value.draft).isEqualTo("worth keeping")
      assertThat(seen).containsExactly(CommentAction.POST)
    }

  @Test
  fun post_isInertForABlankDraft() = runTest {
    val manager = FakeCommentManager()
    val controller = controllerOn(manager)

    controller.onDraftChange("   ")
    controller.post()

    assertThat(manager.added).isEmpty()
  }

  @Test
  fun startEdit_seedsTheEditorWithTheCommentsCurrentText() = runTest {
    val manager = FakeCommentManager()
    manager.emit(listOf(entry("c1", "as written")))
    val controller = controllerOn(manager)

    controller.startEdit("c1")

    assertThat(controller.state.value.editingId).isEqualTo("c1")
    assertThat(controller.state.value.editDraft).isEqualTo("as written")
  }

  @Test
  fun saveEdit_sendsTheEditAndClosesTheEditor() = runTest {
    val manager = FakeCommentManager()
    manager.emit(listOf(entry("c1", "as written")))
    val controller = controllerOn(manager)
    controller.startEdit("c1")

    controller.onEditDraftChange("rewritten")
    controller.saveEdit()

    assertThat(manager.updated).containsExactly("c1" to "rewritten")
    assertThat(controller.state.value.editingId).isNull()
  }

  @Test
  fun saveEdit_keepsTheEditorOpenAndReportsWhenTheWriteFails() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = FakeCommentManager(failing = true)
      manager.emit(listOf(entry("c1", "as written")))
      val controller = controllerOn(manager)
      val seen = mutableListOf<CommentAction>()
      backgroundScope.launch { controller.errors.collect { seen += it } }
      controller.startEdit("c1")
      controller.onEditDraftChange("rewritten")

      controller.saveEdit()

      // Same rule as post(): the rewrite exists nowhere else, so a failed save must not snap the
      // card back to the old text and lose it.
      assertThat(controller.state.value.editingId).isEqualTo("c1")
      assertThat(controller.state.value.editDraft).isEqualTo("rewritten")
      assertThat(seen).containsExactly(CommentAction.EDIT)
    }

  @Test
  fun aCommentDeletedElsewhereClosesItsOpenEditorAndMenu() = runTest {
    val manager = FakeCommentManager()
    manager.emit(listOf(entry("c1", "as written")))
    val controller = controllerOn(manager)
    controller.startEdit("c1")
    controller.toggleMenu("c1")

    // The author deleted it on their other device and the tombstone synced down. The row is still
    // there — it is a tombstone, not a removal — but it can no longer be saved.
    manager.emit(listOf(entry("c1", "", deleted = true)))

    assertThat(controller.state.value.editingId).isNull()
    assertThat(controller.state.value.editDraft).isEmpty()
    assertThat(controller.state.value.menuOpenId).isNull()
  }

  private fun TestScope.controllerOn(manager: CommentManager) =
    CommentThreadController(
      commentManager = manager,
      target = target,
      // Unconfined so a post or an edit has already run by the time the assertion reads the state;
      // the controller itself makes no scheduling assumptions.
      scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

  private fun entry(
    id: String,
    text: String = "body",
    deleted: Boolean = false,
  ) = CommentEntry(
    id = id,
    authorName = "Fan Zhang",
    text = text,
    createdAt = Instant.fromEpochSeconds(1),
    editedAt = null,
    deletedAt = if (deleted) Instant.fromEpochSeconds(2) else null,
    isMine = true,
  )

  private class FakeCommentManager(private val failing: Boolean = false) :
    CommentManager {
    private val thread = MutableStateFlow<List<CommentEntry>>(emptyList())
    val added = mutableListOf<String>()
    val updated = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()

    fun emit(comments: List<CommentEntry>) {
      thread.value = comments
    }

    override fun observeComments(target: CommentTarget): Flow<List<CommentEntry>> =
      thread

    override suspend fun addComment(
      target: CommentTarget,
      text: String
    ): Result<Unit> =
      outcome { added += text }

    override suspend fun updateComment(
      target: CommentTarget,
      commentId: String,
      text: String,
    ): Result<Unit> = outcome { updated += commentId to text }

    override suspend fun deleteComment(
      target: CommentTarget,
      commentId: String,
    ): Result<Unit> = outcome { deleted += commentId }

    override suspend fun deleteThread(target: CommentTarget): Result<Unit> =
      Result.success(Unit)

    private fun outcome(record: () -> Unit): Result<Unit> =
      if (failing) Result.failure(IllegalStateException("offline"))
      else Result.success(record())
  }
}
