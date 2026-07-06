package dev.fanfly.wingslog.feature.technician.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.model.userinfo.UserInfo
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.fanfly.wingslog.feature.sync.data.SyncPrefs
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

private const val TEST_USER_ID = "test-user-123"
private const val TEST_TECHNICIAN_ID = "tech-789"

class TechnicianManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var syncPreferences: SyncPreferences
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var technicianStore: EntityStore<Technician>
  private lateinit var userInfoStore: EntityStore<UserInfo>
  private lateinit var manager: TechnicianManagerImpl

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    firestore = mockk(relaxed = true)
    syncPreferences = mockk(relaxed = true)
    technicianStore = mockk(relaxed = true)
    userInfoStore = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<Technician>(CollectionKind.Technician) } returns technicianStore

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<UserInfo>(CollectionKind.UserInfo) } returns userInfoStore

    every { syncPreferences.state } returns MutableStateFlow(
      SyncPrefs(
        cloudSyncEnabled = false
      )
    )

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { mockUser.isAnonymous } returns false
    every { mockUser.displayName } returns "Test User"
    every { mockUser.email } returns "test@example.com"
    every { firebaseAuth.currentUser } returns mockUser
    // Use an empty flow for authStateChanged so bootstrap doesn't run in tests
    every { firebaseAuth.authStateChanged } returns flowOf()

    every { userInfoStore.observe(any(), any()) } returns flowOf(null)

    manager = TechnicianManagerImpl(
      firebaseAuth,
      firestore,
      syncPreferences,
      storeFactory
    )
  }

  @Test
  fun observeTechnicians_withoutLoggedInUser_emitsEmptyList() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.observeTechnicians()
      .first()

    assertThat(result).isEmpty()
  }

  @Test
  fun observeTechnicians_loggedIn_delegatesToStoreWithUserRootAndUnwrapsValues() =
    runTest {
      val technician = buildTestTechnician(id = TEST_TECHNICIAN_ID)
      val entity = StorageEntity(
        id = TEST_TECHNICIAN_ID,
        value = technician,
        updatedAt = Instant.DISTANT_PAST
      )
      every { firebaseAuth.authStateChanged } returns flowOf(firebaseAuth.currentUser)
      every { technicianStore.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
        listOf(entity)
      )

      val result = manager.observeTechnicians()
        .first()

      assertThat(result).hasSize(1)
      assertThat(result.first().id).isEqualTo(TEST_TECHNICIAN_ID)
      io.mockk.verify {
        technicianStore.observeAll(
          EntityScope.userRoot(
            TEST_USER_ID
          )
        )
      }
    }

  @Test
  fun loadTechnician_withoutLoggedInUser_emitsNull() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.loadTechnician(TEST_TECHNICIAN_ID)
      .first()

    assertThat(result).isNull()
  }

  @Test
  fun loadTechnician_loggedIn_delegatesToStoreAndUnwrapsValue() = runTest {
    val technician = buildTestTechnician(id = TEST_TECHNICIAN_ID)
    val entity = StorageEntity(
      id = TEST_TECHNICIAN_ID,
      value = technician,
      updatedAt = Instant.DISTANT_PAST
    )
    every { firebaseAuth.authStateChanged } returns flowOf(firebaseAuth.currentUser)
    every {
      technicianStore.observe(
        TEST_TECHNICIAN_ID,
        EntityScope.userRoot(TEST_USER_ID)
      )
    } returns flowOf(entity)

    val result = manager.loadTechnician(TEST_TECHNICIAN_ID)
      .first()

    assertThat(result).isEqualTo(technician)
  }

  @Test
  fun updateTechnician_withEmptyId_generatesIdAndCallsStorePut() = runTest {
    val technician = buildTestTechnician(id = "")

    val result = manager.updateTechnician(technician)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      technicianStore.put(
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
    coVerify {
      technicianStore.put(
        TEST_TECHNICIAN_ID,
        technician,
        EntityScope.userRoot(TEST_USER_ID)
      )
    }
  }

  @Test
  fun updateTechnician_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result =
      manager.updateTechnician(buildTestTechnician(id = TEST_TECHNICIAN_ID))

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun deleteTechnician_loggedIn_callsStoreDeleteAndReturnsSuccess() = runTest {
    val result = manager.deleteTechnician(TEST_TECHNICIAN_ID)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      technicianStore.delete(
        TEST_TECHNICIAN_ID,
        EntityScope.userRoot(TEST_USER_ID)
      )
    }
  }

  @Test
  fun deleteTechnician_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.deleteTechnician(TEST_TECHNICIAN_ID)

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun ensureSelfProfile_withExistingName_keepsNameByDefault() = runTest {
    val userScope = EntityScope.userRoot(TEST_USER_ID)
    val existing =
      buildTestTechnician(id = TEST_TECHNICIAN_ID, name = "Guest Pilot")
    every { userInfoStore.observe("main", userScope) } returns flowOf(
      StorageEntity(
        id = "main",
        value = UserInfo(self_technician_id = TEST_TECHNICIAN_ID),
        updatedAt = Instant.DISTANT_PAST,
      )
    )
    every {
      technicianStore.observe(
        TEST_TECHNICIAN_ID,
        userScope
      )
    } returns flowOf(
      StorageEntity(
        id = TEST_TECHNICIAN_ID,
        value = existing,
        updatedAt = Instant.DISTANT_PAST,
      )
    )

    val result = manager.ensureSelfProfile()

    assertThat(result.isSuccess).isTrue()
    coVerify(exactly = 0) {
      technicianStore.put(TEST_TECHNICIAN_ID, any(), userScope)
    }
  }

  @Test
  fun ensureSelfProfile_whenReplacingName_usesAccountName() = runTest {
    val userScope = EntityScope.userRoot(TEST_USER_ID)
    val existing =
      buildTestTechnician(id = TEST_TECHNICIAN_ID, name = "Guest Pilot")
    every { userInfoStore.observe("main", userScope) } returns flowOf(
      StorageEntity(
        id = "main",
        value = UserInfo(self_technician_id = TEST_TECHNICIAN_ID),
        updatedAt = Instant.DISTANT_PAST,
      )
    )
    every {
      technicianStore.observe(
        TEST_TECHNICIAN_ID,
        userScope
      )
    } returns flowOf(
      StorageEntity(
        id = TEST_TECHNICIAN_ID,
        value = existing,
        updatedAt = Instant.DISTANT_PAST,
      )
    )

    val result = manager.ensureSelfProfile(replaceExistingName = true)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      technicianStore.put(
        TEST_TECHNICIAN_ID,
        existing.copy(name = "Test User"),
        userScope,
      )
    }
  }

  private fun buildTestTechnician(
    id: String = TEST_TECHNICIAN_ID,
    name: String = "Jane Smith",
  ): Technician = Technician(id = id, name = name)
}
