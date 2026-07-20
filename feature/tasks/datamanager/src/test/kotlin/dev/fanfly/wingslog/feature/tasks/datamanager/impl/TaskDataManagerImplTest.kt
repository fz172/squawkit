package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

private const val TEST_USER_ID = "test-user-123"
private const val TEST_AIRCRAFT_ID = "aircraft-456"
private const val TEST_TASK_ID = "task-789"

class TaskDataManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var store: EntityStore<MaintenanceTask>
  private lateinit var manager: TaskDataManagerImpl

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    store = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<MaintenanceTask>(CollectionKind.MaintenanceTask) } returns store

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { firebaseAuth.currentUser } returns mockUser
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

    manager = TaskDataManagerImpl(FakeScopeResolver(firebaseAuth), storeFactory)
  }

  @Test
  fun observeTasks_withoutLoggedInUser_emitsEmptyList() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.observeTasks(TEST_AIRCRAFT_ID)
      .first()

    assertThat(result).isEmpty()
  }

  @Test
  fun observeTasks_loggedIn_delegatesToStoreWithAircraftChildScopeAndUnwrapsValues() =
    runTest {
      val task = buildTestTask(id = TEST_TASK_ID)
      val entity = StorageEntity(
        id = TEST_TASK_ID,
        value = task,
        updatedAt = Instant.DISTANT_PAST
      )
      val scope = EntityScope.aircraftChildUnsafe(TEST_USER_ID, TEST_AIRCRAFT_ID)
      every { store.observeAll(scope) } returns flowOf(listOf(entity))

      val result = manager.observeTasks(TEST_AIRCRAFT_ID)
        .first()

      assertThat(result).hasSize(1)
      assertThat(result.first().id).isEqualTo(TEST_TASK_ID)
      io.mockk.verify { store.observeAll(scope) }
    }

  @Test
  fun addTask_withEmptyId_generatesIdAndCallsStorePut() = runTest {
    val task = buildTestTask(id = "")

    val result = manager.addTask(TEST_AIRCRAFT_ID, task)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        match { it.isNotEmpty() },
        match { it.id.isNotEmpty() },
        EntityScope.aircraftChildUnsafe(TEST_USER_ID, TEST_AIRCRAFT_ID),
      )
    }
  }

  @Test
  fun addTask_withExistingId_preservesIdAndCallsStorePut() = runTest {
    val task = buildTestTask(id = TEST_TASK_ID)

    val result = manager.addTask(TEST_AIRCRAFT_ID, task)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        TEST_TASK_ID,
        task,
        EntityScope.aircraftChildUnsafe(TEST_USER_ID, TEST_AIRCRAFT_ID)
      )
    }
  }

  @Test
  fun addTask_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result =
      manager.addTask(TEST_AIRCRAFT_ID, buildTestTask(id = TEST_TASK_ID))

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun updateTask_loggedIn_callsStorePutAndReturnsSuccess() = runTest {
    val task = buildTestTask(id = TEST_TASK_ID)

    val result = manager.updateTask(TEST_AIRCRAFT_ID, task)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        TEST_TASK_ID,
        task,
        EntityScope.aircraftChildUnsafe(TEST_USER_ID, TEST_AIRCRAFT_ID)
      )
    }
  }

  @Test
  fun updateTask_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result =
      manager.updateTask(TEST_AIRCRAFT_ID, buildTestTask(id = TEST_TASK_ID))

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun deleteTask_loggedIn_callsStoreDeleteAndReturnsSuccess() = runTest {
    val result = manager.deleteTask(TEST_AIRCRAFT_ID, TEST_TASK_ID)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.delete(
        TEST_TASK_ID,
        EntityScope.aircraftChildUnsafe(TEST_USER_ID, TEST_AIRCRAFT_ID)
      )
    }
  }

  @Test
  fun deleteTask_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.deleteTask(TEST_AIRCRAFT_ID, TEST_TASK_ID)

    assertThat(result.isFailure).isTrue()
  }

  private fun buildTestTask(
    id: String = TEST_TASK_ID,
    title: String = "Annual Inspection",
  ): MaintenanceTask = MaintenanceTask(id = id, title = title)
}

/**
 * Own-aircraft resolver driven by the same mocked auth the tests already set up: signed in →
 * `aircraftChildUnsafe(uid, id)`, signed out → null / throw. Keeps these unit tests focused on the manager
 * (the own-vs-shared logic is covered by AircraftScopeResolverImplTest).
 */
private class FakeScopeResolver(private val auth: FirebaseAuth) : AircraftScopeResolver {
  override fun resolve(aircraftId: String): Flow<EntityScope?> =
    auth.authStateChanged.map { user ->
      user?.uid?.let { EntityScope.aircraftChildUnsafe(it, aircraftId) }
    }

  override suspend fun resolveNow(aircraftId: String): EntityScope {
    val uid = auth.currentUser?.uid ?: error("Not signed in")
    return EntityScope.aircraftChildUnsafe(uid, aircraftId)
  }
}
