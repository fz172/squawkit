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

    manager = CommentManagerImpl(
      scopeResolver = FixedScopeResolver(scope),
      currentUid = CurrentUidProvider { ME },
      technicianManager = technicianManager,
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
  fun deleteComment_removesYourOwn() = runTest {
    every { store.observe("c1", scope) } returns
      flowOf(row(comment("c1", authorUid = ME)))

    assertThat(manager.deleteComment(target, "c1").isSuccess).isTrue()

    coVerify { store.delete("c1", scope) }
  }

  @Test
  fun deleteComment_refusesToRemoveSomeoneElsesComment() = runTest {
    every { store.observe("c1", scope) } returns
      flowOf(row(comment("c1", authorUid = THEM)))

    // The ⋮ menu is only offered on your own comments, so this is a bug if it is ever reached —
    // but a thread other people can write into is exactly where "the UI checks" is not enough.
    val result = manager.deleteComment(target, "c1")

    assertThat(result.isFailure).isTrue()
    coVerify(exactly = 0) { store.delete(any(), any()) }
  }

  // ---- helpers ----

  private fun comment(
    id: String,
    parentId: String = SQUAWK_ID,
    parentType: CommentParentType = CommentParentType.COMMENT_PARENT_TYPE_SQUAWK,
    authorUid: String = ME,
    text: String = "body",
    createdAtSeconds: Long = 1L,
  ) = Comment(
    id = id,
    parent_id = parentId,
    parent_type = parentType,
    text = text,
    author_uid = authorUid,
    author_name = "Someone",
    created_at = Instant.fromEpochSeconds(createdAtSeconds)
      .toWireInstant(),
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
