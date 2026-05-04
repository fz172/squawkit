package dev.fanfly.wingslog.feature.technician.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Technician
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val TEST_USER_ID = "test-user-123"
private const val TEST_TECHNICIAN_ID = "tech-789"

class TechnicianManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var store: EntityStore<Technician>
  private lateinit var manager: TechnicianManagerImpl

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    store = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<Technician>(CollectionKind.Technician) } returns store

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { firebaseAuth.currentUser } returns mockUser
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

    manager = TechnicianManagerImpl(firebaseAuth, storeFactory)
  }

  @Test
  fun observeTechnicians_withoutLoggedInUser_emitsEmptyList() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.observeTechnicians().first()

    assertThat(result).isEmpty()
  }

  @Test
  fun observeTechnicians_loggedIn_delegatesToStoreWithUserRootAndUnwrapsValues() = runTest {
    val technician = buildTestTechnician(id = TEST_TECHNICIAN_ID)
    val entity = StorageEntity(id = TEST_TECHNICIAN_ID, value = technician, updatedAt = Instant.DISTANT_PAST)
    every { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(listOf(entity))

    val result = manager.observeTechnicians().first()

    assertThat(result).hasSize(1)
    assertThat(result.first().id).isEqualTo(TEST_TECHNICIAN_ID)
    io.mockk.verify { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) }
  }

  @Test
  fun loadTechnician_withoutLoggedInUser_emitsNull() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.loadTechnician(TEST_TECHNICIAN_ID).first()

    assertThat(result).isNull()
  }

  @Test
  fun loadTechnician_loggedIn_delegatesToStoreAndUnwrapsValue() = runTest {
    val technician = buildTestTechnician(id = TEST_TECHNICIAN_ID)
    val entity = StorageEntity(id = TEST_TECHNICIAN_ID, value = technician, updatedAt = Instant.DISTANT_PAST)
    every { store.observe(TEST_TECHNICIAN_ID, EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(entity)

    val result = manager.loadTechnician(TEST_TECHNICIAN_ID).first()

    assertThat(result).isEqualTo(technician)
  }

  @Test
  fun updateTechnician_withEmptyId_generatesIdAndCallsStorePut() = runTest {
    val technician = buildTestTechnician(id = "")

    val result = manager.updateTechnician(technician)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        match { it.isNotEmpty() },
        match { it.id.isNotEmpty() },
        EntityScope.userRoot(TEST_USER_ID),
      )
    }
  }

  @Test
  fun updateTechnician_withExistingId_preservesIdAndCallsStorePut() = runTest {
    val technician = buildTestTechnician(id = TEST_TECHNICIAN_ID)

    val result = manager.updateTechnician(technician)

    assertThat(result.isSuccess).isTrue()
    coVerify { store.put(TEST_TECHNICIAN_ID, technician, EntityScope.userRoot(TEST_USER_ID)) }
  }

  @Test
  fun updateTechnician_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.updateTechnician(buildTestTechnician(id = TEST_TECHNICIAN_ID))

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun deleteTechnician_loggedIn_callsStoreDeleteAndReturnsSuccess() = runTest {
    val result = manager.deleteTechnician(TEST_TECHNICIAN_ID)

    assertThat(result.isSuccess).isTrue()
    coVerify { store.delete(TEST_TECHNICIAN_ID, EntityScope.userRoot(TEST_USER_ID)) }
  }

  @Test
  fun deleteTechnician_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.deleteTechnician(TEST_TECHNICIAN_ID)

    assertThat(result.isFailure).isTrue()
  }

  private fun buildTestTechnician(
    id: String = TEST_TECHNICIAN_ID,
    name: String = "Jane Smith",
  ): Technician = Technician(id = id, name = name)
}
