package dev.fanfly.wingslog.feature.comments.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.CurrentUidProvider
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.comments.datamanager.impl.CommentManagerImpl
import dev.fanfly.wingslog.feature.comments.model.CommentParentKind
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.thing.Comment
import dev.fanfly.wingslog.thing.CommentParentType
import dev.fanfly.wingslog.thing.Technician
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

private const val HOST_UID = "host-1"
private const val THING_ID = "thing-1"
private const val SQUAWK_ID = "squawk-1"
private const val ME = "me-uid"
private const val THEM = "them-uid"

class CommentManagerImplTest {

  private lateinit var store: EntityStore<Comment>
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var technicianManager: TechnicianManager
  private lateinit var auth: FirebaseAuth
  private lateinit var manager: CommentManagerImpl

  private val scope = EntityScope.thingChildUnsafe(HOST_UID, THING_ID)
  private val target =
    CommentTarget(THING_ID, SQUAWK_ID, CommentParentKind.SQUAWK)

  @Before
  fun setUp() {
    store = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)
    every { storeFactory.create<Comment>(CollectionKind.Comment) } returns store

    technicianManager = mockk(relaxed = true)
    every { technicianManager.observeSelf() } returns
      flowOf(Technician(id = "t1", name = "Fan Zhang"))

    auth = mockk(relaxed = true)
    every { auth.currentUser } returns null

    manager = CommentManagerImpl(
      scopeResolver = FixedScopeResolver(scope),
      currentUid = CurrentUidProvider { ME },
      technicianManager = technicianManager,
      auth = auth,
      storeFactory = storeFactory,
    )
  }

  // ---- observeComments ----

  @Test
  fun observeComments_returnsOnlyThisParentsThread() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        row(comment("c1", parentId = SQUAWK_ID, createdAtSeconds = 100)),
        row(comment("c2", parentId = "other-squawk", createdAtSeconds = 200)),
        row(
          comment(
            "c3",
            parentId = SQUAWK_ID,
            parentType = CommentParentType.COMMENT_PARENT_TYPE_MAINTENANCE_TASK,
            createdAtSeconds = 300,
          )
        ),
      )
    )

    val thread = manager.observeComments(target)
      .first()

    // c2 belongs to a different squawk; c3 shares the id but is a task's thread — the pair is
    // what makes (parent_type, parent_id) the selector rather than parent_id alone.
    assertThat(thread.map { it.id }).containsExactly("c1")
  }

  @Test
  fun observeComments_ordersOldestFirstRegardlessOfStoreOrder() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        row(comment("newer", createdAtSeconds = 300)),
        row(comment("older", createdAtSeconds = 100)),
        row(comment("middle", createdAtSeconds = 200)),
      )
    )

    // The store orders by local wall clock (most recent write first); a conversation has to read
    // in the order it was written.
    assertThat(
      manager.observeComments(target)
        .first()
        .map { it.id }
    )
      .containsExactly("older", "middle", "newer")
      .inOrder()
  }

  @Test
  fun observeComments_marksOnlyTheCallersOwnCommentsAsMine() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        row(comment("mine", authorUid = ME)),
        row(comment("theirs", authorUid = THEM)),
        row(comment("unattributed", authorUid = "")),
      )
    )

    val byId = manager.observeComments(target)
      .first()
      .associateBy { it.id }

    assertThat(byId.getValue("mine").isMine).isTrue()
    assertThat(byId.getValue("theirs").isMine).isFalse()
    // An empty author is "unknown", not "me" — the ⋮ menu must not appear on it.
    assertThat(byId.getValue("unattributed").isMine).isFalse()
  }

  // ---- addComment ----

  @Test
  fun addComment_stampsTheAuthorsUidAndProfileName() = runTest {
    val written = slot<Comment>()

    manager.addComment(target, "  Parts arrived.  ")

    coVerify { store.put(any(), capture(written), scope) }
    with(written.captured) {
      assertThat(text).isEqualTo("Parts arrived.")
      assertThat(author_uid).isEqualTo(ME)
      assertThat(author_name).isEqualTo("Fan Zhang")
      assertThat(parent_id).isEqualTo(SQUAWK_ID)
      assertThat(parent_type)
        .isEqualTo(CommentParentType.COMMENT_PARENT_TYPE_SQUAWK)
      assertThat(edited_at).isNull()
      assertThat(id).isNotEmpty()
    }
  }

  @Test
  fun addComment_fallsBackToTheAccountNameThenEmailWhenTheProfileIsUnnamed() = runTest {
    // The same precedence the share roster uses (SharingManagerImpl.publishTechnicianMirror), so
    // a commenter is bylined the way the roster already shows them — and because the name is
    // denormalized at post time, a blank here would be a permanent "Unknown".
    every { technicianManager.observeSelf() } returns flowOf(null)
    val user = mockk<FirebaseUser>(relaxed = true)
    every { user.displayName } returns ""
    every { user.email } returns "fan@example.com"
    every { auth.currentUser } returns user
    val written = slot<Comment>()

    manager.addComment(target, "hello")

    coVerify { store.put(any(), capture(written), scope) }
    assertThat(written.captured.author_name).isEqualTo("fan@example.com")
  }

  @Test
  fun addComment_writesNothingForABlankBody() = runTest {
    assertThat(manager.addComment(target, "   ").isSuccess).isTrue()

    coVerify(exactly = 0) { store.put(any(), any(), any()) }
  }

  // ---- updateComment ----

  @Test
  fun updateComment_rewritesTheTextAndStampsEditedAt() = runTest {
    every { store.observe("c1", scope) } returns
      flowOf(row(comment("c1", authorUid = ME, text = "before")))
    val written = slot<Comment>()

    manager.updateComment(target, "c1", "after")

    coVerify { store.put("c1", capture(written), scope) }
    assertThat(written.captured.text).isEqualTo("after")
    assertThat(written.captured.edited_at).isNotNull()
  }

  @Test
  fun updateComment_leavesEditedAtUnsetWhenTheTextIsUnchanged() = runTest {
    every { store.observe("c1", scope) } returns
      flowOf(row(comment("c1", authorUid = ME, text = "same")))

    // Opening the editor and saving without typing is not an edit, and must not brand the comment
    // with an "Edited" line that did not happen.
    manager.updateComment(target, "c1", "same")

    coVerify(exactly = 0) { store.put(any(), any(), any()) }
  }

  @Test
  fun updateComment_refusesToRewriteSomeoneElsesComment() = runTest {
    every { store.observe("c1", scope) } returns
      flowOf(row(comment("c1", authorUid = THEM)))

    val result = manager.updateComment(target, "c1", "not yours")

    assertThat(result.isFailure).isTrue()
    coVerify(exactly = 0) { store.put(any(), any(), any()) }
  }

  // ---- deleteComment ----

  @Test
  fun deleteComment_tombstonesRatherThanRemoving() = runTest {
    every { store.observe("c1", scope) } returns
      flowOf(row(comment("c1", authorUid = ME, text = "said too much")))
    val written = slot<Comment>()

    assertThat(manager.deleteComment(target, "c1").isSuccess).isTrue()

    // Never store.delete: an edit shows an "Edited …" line, so a hard delete would let an author
    // launder a rewrite by deleting and re-posting. The row stays, the content goes.
    coVerify(exactly = 0) { store.delete(any(), any()) }
    coVerify { store.put("c1", capture(written), scope) }
    assertThat(written.captured.text).isEmpty()
    assertThat(written.captured.deleted_at).isNotNull()
  }

  @Test
  fun deleteComment_leavesAnAlreadyDeletedCommentAlone() = runTest {
    every { store.observe("c1", scope) } returns
      flowOf(row(comment("c1", authorUid = ME, deletedAtSeconds = 5)))

    // Re-stamping would move the recorded deletion time, which is the one thing the tombstone is
    // there to pin down.
    assertThat(manager.deleteComment(target, "c1").isSuccess).isTrue()

    coVerify(exactly = 0) { store.put(any(), any(), any()) }
  }

  @Test
  fun updateComment_refusesToReviveATombstone() = runTest {
    every { store.observe("c1", scope) } returns
      flowOf(row(comment("c1", authorUid = ME, deletedAtSeconds = 5)))

    val result = manager.updateComment(target, "c1", "back from the dead")

    assertThat(result.isFailure).isTrue()
    coVerify(exactly = 0) { store.put(any(), any(), any()) }
  }

  @Test
  fun observeComments_keepsTombstonesInTheThread() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        row(comment("live", createdAtSeconds = 100)),
        row(comment("gone", createdAtSeconds = 200, deletedAtSeconds = 300)),
      )
    )

    val thread = manager.observeComments(target)
      .first()

    assertThat(thread.map { it.id })
      .containsExactly("live", "gone")
      .inOrder()
    assertThat(thread.first { it.id == "gone" }.isDeleted).isTrue()
  }

  @Test
  fun deleteComment_refusesToRemoveSomeoneElsesComment() = runTest {
    every { store.observe("c1", scope) } returns
      flowOf(row(comment("c1", authorUid = THEM)))

    // The ⋮ menu is only offered on your own comments, so this is a bug if it is ever reached —
    // but a thread other people can write into is exactly where "the UI checks" is not enough.
    val result = manager.deleteComment(target, "c1")

    assertThat(result.isFailure).isTrue()
    coVerify(exactly = 0) { store.put(any(), any(), any()) }
    coVerify(exactly = 0) { store.delete(any(), any()) }
  }

  // ---- deleteThread ----

  @Test
  fun deleteThread_removesOnlyThisParentsRows() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        row(comment("c1", parentId = SQUAWK_ID)),
        row(comment("c2", parentId = SQUAWK_ID, authorUid = THEM, deletedAtSeconds = 5)),
        row(comment("other", parentId = "other-squawk")),
      )
    )

    // The one path that actually removes comment rows: the parent record is gone, so the thread
    // has nothing to hang off. No authorship gate — c2 is someone else's tombstone and goes too.
    assertThat(manager.deleteThread(target).isSuccess).isTrue()

    coVerify { store.delete("c1", scope) }
    coVerify { store.delete("c2", scope) }
    coVerify(exactly = 0) { store.delete("other", any()) }
  }

  // ---- helpers ----

  private fun comment(
    id: String,
    parentId: String = SQUAWK_ID,
    parentType: CommentParentType = CommentParentType.COMMENT_PARENT_TYPE_SQUAWK,
    authorUid: String = ME,
    text: String = "body",
    createdAtSeconds: Long = 1L,
    deletedAtSeconds: Long? = null,
  ) = Comment(
    id = id,
    parent_id = parentId,
    parent_type = parentType,
    text = text,
    author_uid = authorUid,
    author_name = "Someone",
    created_at = Instant.fromEpochSeconds(createdAtSeconds)
      .toWireInstant(),
    deleted_at = deletedAtSeconds?.let {
      Instant.fromEpochSeconds(it)
        .toWireInstant()
    },
  )

  private fun row(value: Comment) = StorageEntity(
    id = value.id,
    value = value,
    updatedAt = Instant.fromEpochSeconds(0),
  )

  private class FixedScopeResolver(private val scope: EntityScope) :
    ThingScopeResolver {
    override fun resolve(thingId: String): Flow<EntityScope?> = flowOf(scope)
    override suspend fun resolveNow(thingId: String): EntityScope = scope
  }
}
