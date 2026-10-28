package dev.fanfly.wingslog.feature.squawk.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

private const val TEST_USER_ID = "test-user-123"
private const val TEST_THING_ID = "thing-456"
private const val TEST_SQUAWK_ID = "squawk-789"
private val TEST_THING = ThingId(TEST_THING_ID)
private val TEST_SQUAWK = SquawkId(TEST_SQUAWK_ID)

class SquawkManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var commentManager: CommentManager
  private lateinit var store: EntityStore<Squawk>
  private lateinit var manager: SquawkManager

  private val testScope =
    EntityScope.thingChildUnsafe(TEST_USER_ID, TEST_THING)

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    store = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<Squawk>(CollectionKind.Squawk) } returns store

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { firebaseAuth.currentUser } returns mockUser
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

    commentManager = mockk(relaxed = true)
    manager = SquawkManagerImpl(
      FakeScopeResolver(firebaseAuth),
      commentManager,
      storeFactory
    )
  }

  // ---- dismissSquawk — happy path ----

  @Test
  fun dismissSquawk_setsGivenDismissReasonOnExistingSquawk() = runTest {
    val existingSquawk = buildTestSquawk(
      id = TEST_SQUAWK_ID,
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
    )
    every { store.observeAll(testScope) } returns flowOf(
      listOf(
        StorageEntity(
          TEST_SQUAWK_ID,
          existingSquawk,
          Instant.DISTANT_PAST
        )
      )
    )

    val result = manager.dismissSquawk(
      TEST_THING,
      TEST_SQUAWK,
      SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        TEST_SQUAWK_ID,
        match { it.dismiss_reason == SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE },
        testScope,
      )
    }
  }

  @Test
  fun dismissSquawk_setsDismissedAtToNonNull() = runTest {
    val existingSquawk = buildTestSquawk(id = TEST_SQUAWK_ID)
    every { store.observeAll(testScope) } returns flowOf(
      listOf(
        StorageEntity(
          TEST_SQUAWK_ID,
          existingSquawk,
          Instant.DISTANT_PAST
        )
      )
    )

    val result = manager.dismissSquawk(
      TEST_THING,
      TEST_SQUAWK,
      SquawkDismissReason.SQUAWK_DISMISS_REASON_NOT_REPRODUCIBLE,
    )

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        TEST_SQUAWK_ID,
        match { it.dismissed_at != null },
        testScope,
      )
    }
  }

  // ---- dismissSquawk — squawk not found ----

  @Test
  fun dismissSquawk_squawkNotFound_returnsFailure() = runTest {
    // Store returns a list that does not contain the requested squawk ID.
    every { store.observeAll(testScope) } returns flowOf(emptyList())

    val result = manager.dismissSquawk(
      TEST_THING,
      TEST_SQUAWK,
      SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
    )

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun dismissSquawk_squawkIdMismatch_returnsFailure() = runTest {
    val otherSquawk = buildTestSquawk(id = "different-squawk-id")
    every { store.observeAll(testScope) } returns flowOf(
      listOf(
        StorageEntity(
          "different-squawk-id",
          otherSquawk,
          Instant.DISTANT_PAST
        )
      )
    )

    val result = manager.dismissSquawk(
      TEST_THING,
      TEST_SQUAWK,
      SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )

    assertThat(result.isFailure).isTrue()
  }

  // ---- dismissSquawk — not logged in ----

  @Test
  fun dismissSquawk_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.dismissSquawk(
      TEST_THING,
      TEST_SQUAWK,
      SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )

    assertThat(result.isFailure).isTrue()
  }

  // ---- reopenSquawk — happy path ----

  @Test
  fun reopenSquawk_clearsDismissReasonToUnknown() = runTest {
    val dismissedSquawk = buildTestSquawk(
      id = TEST_SQUAWK_ID,
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )
    every { store.observeAll(testScope) } returns flowOf(
      listOf(
        StorageEntity(
          TEST_SQUAWK_ID,
          dismissedSquawk,
          Instant.DISTANT_PAST
        )
      )
    )

    val result = manager.reopenSquawk(TEST_THING, TEST_SQUAWK)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        TEST_SQUAWK_ID,
        match { it.dismiss_reason == SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN },
        testScope,
      )
    }
  }

  @Test
  fun reopenSquawk_clearsDismissedAtToNull() = runTest {
    val dismissedSquawk = buildTestSquawk(
      id = TEST_SQUAWK_ID,
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
    )
    every { store.observeAll(testScope) } returns flowOf(
      listOf(
        StorageEntity(
          TEST_SQUAWK_ID,
          dismissedSquawk,
          Instant.DISTANT_PAST
        )
      )
    )

    val result = manager.reopenSquawk(TEST_THING, TEST_SQUAWK)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        TEST_SQUAWK_ID,
        match { it.dismissed_at == null },
        testScope,
      )
    }
  }

  // ---- reopenSquawk — squawk not found ----

  @Test
  fun reopenSquawk_squawkNotFound_returnsFailure() = runTest {
    every { store.observeAll(testScope) } returns flowOf(emptyList())

    val result = manager.reopenSquawk(TEST_THING, TEST_SQUAWK)

    assertThat(result.isFailure).isTrue()
  }

  // ---- reopenSquawk — not logged in ----

  @Test
  fun reopenSquawk_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.reopenSquawk(TEST_THING, TEST_SQUAWK)

    assertThat(result.isFailure).isTrue()
  }

  // ---- helpers ----

  private fun buildTestSquawk(
    id: String = TEST_SQUAWK_ID,
    title: String = "Oil leak on left engine",
    addressedByLogId: String = "",
    dismissReason: SquawkDismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
  ): Squawk = Squawk(
    id = id,
    title = title,
    addressed_by_log_id = addressedByLogId,
    dismiss_reason = dismissReason,
  )

  @Test
  fun deleteSquawk_takesItsCommentThreadWithIt() = runTest {
    // A thread with no record to hang off would otherwise sit in every member's store forever.
    manager.deleteSquawk(TEST_THING, TEST_SQUAWK)

    coVerify {
      store.delete(TEST_SQUAWK_ID, testScope)
      commentManager.deleteThread(
        CommentTarget.squawk(TEST_THING, TEST_SQUAWK)
      )
    }
  }
}

/**
 * Own-thing resolver driven by the same mocked auth the tests already set up: signed in →
 * `thingChildUnsafe(uid, id)`, signed out → null / throw. Keeps these unit tests focused on the manager
 * (the own-vs-shared logic is covered by ThingScopeResolverImplTest).
 */
private class FakeScopeResolver(private val auth: FirebaseAuth) :
  ThingScopeResolver {
  override fun resolve(thingId: ThingId): Flow<EntityScope?> =
    auth.authStateChanged.map { user ->
      user?.uid?.let { EntityScope.thingChildUnsafe(it, thingId) }
    }

  override suspend fun resolveNow(thingId: ThingId): EntityScope {
    val uid = auth.currentUser?.uid ?: error("Not signed in")
    return EntityScope.thingChildUnsafe(uid, thingId)
  }
}
