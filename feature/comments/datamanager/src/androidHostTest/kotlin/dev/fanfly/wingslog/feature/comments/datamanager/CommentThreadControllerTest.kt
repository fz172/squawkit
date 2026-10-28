package dev.fanfly.wingslog.feature.comments.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.feature.comments.model.CommentAction
import dev.fanfly.wingslog.feature.comments.model.CommentEntry
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.id.CommentId
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId
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

  private val target = CommentTarget.squawk(ThingId("thing-1"), SquawkId("squawk-1"))

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
  fun post_signalsPostedOnceTheNewRowIsInTheThread() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = FakeCommentManager(echoesPosts = false)
      val controller = controllerOn(manager)
      var posted = 0
      backgroundScope.launch { controller.posted.collect { posted++ } }
      controller.onDraftChange("Parts arrived")

      controller.post()

      // The write landed, but the observer has not delivered the row yet — scrolling now would
      // land on the old end of the thread.
      assertThat(posted).isEqualTo(0)

      manager.emit(listOf(entry("c1", "Parts arrived")))

      assertThat(posted).isEqualTo(1)
    }

  @Test
  fun post_doesNotSignalPostedWhenTheWriteFails() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = FakeCommentManager(failing = true)
      val controller = controllerOn(manager)
      var posted = 0
      backgroundScope.launch { controller.posted.collect { posted++ } }
      controller.onDraftChange("worth keeping")

      controller.post()
      testScheduler.advanceUntilIdle()

      assertThat(posted).isEqualTo(0)
    }

  @Test
  fun onComposerFocus_signalsScrollToEnd() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = FakeCommentManager()
      val controller = controllerOn(manager)
      var scrolls = 0
      backgroundScope.launch { controller.scrollToEnd.collect { scrolls++ } }

      controller.onComposerFocus()

      assertThat(scrolls).isEqualTo(1)
    }

  @Test
  fun startEdit_seedsTheEditorWithTheCommentsCurrentText() = runTest {
    val manager = FakeCommentManager()
    manager.emit(listOf(entry("c1", "as written")))
    val controller = controllerOn(manager)

    controller.startEdit(CommentId("c1"))

    assertThat(controller.state.value.editingId).isEqualTo(CommentId("c1"))
    assertThat(controller.state.value.editDraft).isEqualTo("as written")
  }

  @Test
  fun saveEdit_sendsTheEditAndClosesTheEditor() = runTest {
    val manager = FakeCommentManager()
    manager.emit(listOf(entry("c1", "as written")))
    val controller = controllerOn(manager)
    controller.startEdit(CommentId("c1"))

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
      controller.startEdit(CommentId("c1"))
      controller.onEditDraftChange("rewritten")

      controller.saveEdit()

      // Same rule as post(): the rewrite exists nowhere else, so a failed save must not snap the
      // card back to the old text and lose it.
      assertThat(controller.state.value.editingId).isEqualTo(CommentId("c1"))
      assertThat(controller.state.value.editDraft).isEqualTo("rewritten")
      assertThat(seen).containsExactly(CommentAction.EDIT)
    }

  @Test
  fun aCommentDeletedElsewhereClosesItsOpenEditorAndMenu() = runTest {
    val manager = FakeCommentManager()
    manager.emit(listOf(entry("c1", "as written")))
    val controller = controllerOn(manager)
    controller.startEdit(CommentId("c1"))
    controller.toggleMenu(CommentId("c1"))

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
    id = CommentId(id),
    authorName = "Jordan Reyes",
    text = text,
    createdAt = Instant.fromEpochSeconds(1),
    editedAt = null,
    deletedAt = if (deleted) Instant.fromEpochSeconds(2) else null,
    isMine = true,
  )

  /**
   * [echoesPosts] mirrors the store: a successful add shows up in the observed thread, the way the
   * local entity store's observer delivers it.
   */
  private class FakeCommentManager(
    private val failing: Boolean = false,
    private val echoesPosts: Boolean = true,
  ) : CommentManager {
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
      outcome {
        added += text
        if (echoesPosts) {
          thread.value += CommentEntry(
            id = CommentId("posted-${added.size}"),
            authorName = "Jordan Reyes",
            text = text,
            createdAt = Instant.fromEpochSeconds(1),
            editedAt = null,
            deletedAt = null,
            isMine = true,
          )
        }
      }

    override suspend fun updateComment(
      target: CommentTarget,
      commentId: CommentId,
      text: String,
    ): Result<Unit> = outcome { updated += commentId.value to text }

    override suspend fun deleteComment(
      target: CommentTarget,
      commentId: CommentId,
    ): Result<Unit> = outcome { deleted += commentId.value }

    override suspend fun deleteThread(target: CommentTarget): Result<Unit> =
      Result.success(Unit)

    private fun outcome(record: () -> Unit): Result<Unit> =
      if (failing) Result.failure(IllegalStateException("offline"))
      else Result.success(record())
  }
}
