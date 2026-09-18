package dev.fanfly.wingslog.feature.logs.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Squawk
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

class MaintenanceLogManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var logStore: EntityStore<MaintenanceLog>
  private lateinit var overviewStore: EntityStore<*>
  private lateinit var squawkStore: EntityStore<Squawk>
  private lateinit var manager: MaintenanceLogManagerImpl

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    logStore = mockk(relaxed = true)
    overviewStore = mockk(relaxed = true)
    squawkStore = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<MaintenanceLog>(CollectionKind.MaintenanceLog) } returns logStore
    every { storeFactory.create<Any>(CollectionKind.MaintenanceOverview) } returns
      overviewStore as EntityStore<Any>
    every { storeFactory.create<Squawk>(CollectionKind.Squawk) } returns squawkStore
    every { logStore.observeAll(any()) } returns flowOf(emptyList())
    every { squawkStore.observeAll(any()) } returns flowOf(emptyList())

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { firebaseAuth.currentUser } returns mockUser
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

    manager =
      MaintenanceLogManagerImpl(FakeScopeResolver(firebaseAuth), storeFactory)
  }

  @Test
  fun observeLogs_withoutLoggedInUser_emitsEmptyList() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    var emittedList: List<MaintenanceLog>? = null
    manager.observeLogs(TEST_THING_ID)
      .collect {
        emittedList = it
      }

    assertThat(emittedList).isEmpty()
  }

  @Test
  fun observeLogs_loggedIn_delegatesToStoreWithThingScope() = runTest {
    every {
      logStore.observeAll(
        EntityScope.thingChildUnsafe(
          TEST_USER_ID,
          TEST_THING_ID
        )
      )
    } returns flowOf(emptyList())

    val result = mutableListOf<List<MaintenanceLog>>()
    manager.observeLogs(TEST_THING_ID)
      .collect { result += it }

    assertThat(result).hasSize(1)
    assertThat(result.first()).isEmpty()
    io.mockk.verify {
      logStore.observeAll(
        EntityScope.thingChildUnsafe(
          TEST_USER_ID,
          TEST_THING_ID
        )
      )
    }
  }

  @Test
  fun deleteLog_reopensTheSquawksItAddressed() = runTest {
    val scope = EntityScope.thingChildUnsafe(TEST_USER_ID, TEST_THING_ID)
    every { squawkStore.observeAll(scope) } returns flowOf(
      listOf(
        squawkRow("squawk-1", addressedBy = "log-1"),
        squawkRow("squawk-2", addressedBy = "log-2"),
        squawkRow("squawk-3", addressedBy = ""),
      )
    )

    manager.deleteLog(TEST_THING_ID, "log-1")

    coVerify(exactly = 1) {
      squawkStore.put("squawk-1", Squawk(id = "squawk-1", addressed_by_log_id = ""), scope)
    }
    coVerify(exactly = 0) { squawkStore.put("squawk-2", any(), any()) }
    coVerify(exactly = 0) { squawkStore.put("squawk-3", any(), any()) }
  }

  @Test
  fun deleteLog_withNoAddressedSquawks_writesNoSquawks() = runTest {
    manager.deleteLog(TEST_THING_ID, "log-1")

    coVerify(exactly = 0) { squawkStore.put(any(), any(), any()) }
  }

  private fun squawkRow(id: String, addressedBy: String): StorageEntity<Squawk> =
    StorageEntity(
      id = id,
      value = Squawk(id = id, addressed_by_log_id = addressedBy),
      updatedAt = Instant.fromEpochSeconds(0),
    )
}

/**
 * Own-thing resolver driven by the same mocked auth the tests already set up: signed in →
 * `thingChildUnsafe(uid, id)`, signed out → null / throw. Keeps these unit tests focused on the manager
 * (the own-vs-shared logic is covered by ThingScopeResolverImplTest).
 */
private class FakeScopeResolver(private val auth: FirebaseAuth) :
  ThingScopeResolver {
  override fun resolve(thingId: String): Flow<EntityScope?> =
    auth.authStateChanged.map { user ->
      user?.uid?.let { EntityScope.thingChildUnsafe(it, thingId) }
    }

  override suspend fun resolveNow(thingId: String): EntityScope {
    val uid = auth.currentUser?.uid ?: error("Not signed in")
    return EntityScope.thingChildUnsafe(uid, thingId)
  }
}
