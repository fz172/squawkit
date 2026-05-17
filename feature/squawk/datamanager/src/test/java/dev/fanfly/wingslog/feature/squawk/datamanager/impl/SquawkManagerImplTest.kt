package dev.fanfly.wingslog.feature.squawk.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val TEST_USER_ID = "test-user-123"
private const val TEST_AIRCRAFT_ID = "aircraft-456"
private const val TEST_SQUAWK_ID = "squawk-789"

class SquawkManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var store: EntityStore<Squawk>
  private lateinit var manager: SquawkManagerImpl

  private val testScope = EntityScope.aircraftChild(TEST_USER_ID, TEST_AIRCRAFT_ID)

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

    manager = SquawkManagerImpl(firebaseAuth, storeFactory)
  }

  // ---- dismissSquawk — happy path ----

  @Test
  fun dismissSquawk_setsGivenDismissReasonOnExistingSquawk() = runTest {
    val existingSquawk = buildTestSquawk(
      id = TEST_SQUAWK_ID,
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
    )
    every { store.observeAll(testScope) } returns flowOf(
      listOf(StorageEntity(TEST_SQUAWK_ID, existingSquawk, Instant.DISTANT_PAST))
    )

    val result = manager.dismissSquawk(
      TEST_AIRCRAFT_ID,
      TEST_SQUAWK_ID,
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
      listOf(StorageEntity(TEST_SQUAWK_ID, existingSquawk, Instant.DISTANT_PAST))
    )

    val result = manager.dismissSquawk(
      TEST_AIRCRAFT_ID,
      TEST_SQUAWK_ID,
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
      TEST_AIRCRAFT_ID,
      TEST_SQUAWK_ID,
      SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
    )

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun dismissSquawk_squawkIdMismatch_returnsFailure() = runTest {
    val otherSquawk = buildTestSquawk(id = "different-squawk-id")
    every { store.observeAll(testScope) } returns flowOf(
      listOf(StorageEntity("different-squawk-id", otherSquawk, Instant.DISTANT_PAST))
    )

    val result = manager.dismissSquawk(
      TEST_AIRCRAFT_ID,
      TEST_SQUAWK_ID,
      SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )

    assertThat(result.isFailure).isTrue()
  }

  // ---- dismissSquawk — not logged in ----

  @Test
  fun dismissSquawk_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.dismissSquawk(
      TEST_AIRCRAFT_ID,
      TEST_SQUAWK_ID,
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
      listOf(StorageEntity(TEST_SQUAWK_ID, dismissedSquawk, Instant.DISTANT_PAST))
    )

    val result = manager.reopenSquawk(TEST_AIRCRAFT_ID, TEST_SQUAWK_ID)

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
      listOf(StorageEntity(TEST_SQUAWK_ID, dismissedSquawk, Instant.DISTANT_PAST))
    )

    val result = manager.reopenSquawk(TEST_AIRCRAFT_ID, TEST_SQUAWK_ID)

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

    val result = manager.reopenSquawk(TEST_AIRCRAFT_ID, TEST_SQUAWK_ID)

    assertThat(result.isFailure).isTrue()
  }

  // ---- reopenSquawk — not logged in ----

  @Test
  fun reopenSquawk_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.reopenSquawk(TEST_AIRCRAFT_ID, TEST_SQUAWK_ID)

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
}
