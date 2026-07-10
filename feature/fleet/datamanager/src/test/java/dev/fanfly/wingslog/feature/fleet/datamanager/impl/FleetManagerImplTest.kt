package dev.fanfly.wingslog.feature.fleet.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

private const val TEST_USER_ID = "test-user-123"
private const val TEST_AIRCRAFT_ID = "aircraft-456"
private const val HOST_UID = "host-user-999"

class FleetManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var store: EntityStore<Aircraft>
  private lateinit var refStore: EntityStore<SharedAircraftRef>
  private lateinit var manager: FleetManagerImpl

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    store = mockk(relaxed = true)
    refStore = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<Aircraft>(CollectionKind.Aircraft) } returns store
    @Suppress("UNCHECKED_CAST")
    every {
      storeFactory.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)
    } returns refStore
    // Default: no shared aircraft. Individual tests override.
    every { refStore.observeAll(any()) } returns flowOf(emptyList())

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { firebaseAuth.currentUser } returns mockUser
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

    manager = FleetManagerImpl(firebaseAuth, storeFactory)
  }

  @Test
  fun observeFleetDashboard_withoutLoggedInUser_emitsEmptyList() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.observeFleetDashboard()
      .first()

    assertThat(result).isEmpty()
  }

  @Test
  fun observeFleetDashboard_loggedIn_delegatesToStoreWithUserRootAndUnwrapsValues() =
    runTest {
      val aircraft = buildTestAircraft(id = TEST_AIRCRAFT_ID)
      val entity = StorageEntity(
        id = TEST_AIRCRAFT_ID,
        value = aircraft,
        updatedAt = Instant.DISTANT_PAST
      )
      every { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
        listOf(entity)
      )

      val result = manager.observeFleetDashboard()
        .first()

      assertThat(result).hasSize(1)
      assertThat(result.first().aircraft.id).isEqualTo(TEST_AIRCRAFT_ID)
      assertThat(result.first().shared).isFalse()
      assertThat(result.first().role).isEqualTo(ShareRole.SHARE_ROLE_OWNER)
      io.mockk.verify { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) }
    }

  @Test
  fun observeFleetDashboard_withSharedRef_includesHostAircraftTaggedShared() = runTest {
    val own = buildTestAircraft(id = "own-1")
    val shared = buildTestAircraft(id = "shared-1", make = "Piper", model = "PA-28")
    every { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
      listOf(StorageEntity("own-1", own, Instant.DISTANT_PAST))
    )
    // A ref pointing at the host's aircraft, plus the live doc under the host's root.
    val ref = SharedAircraftRef(
      aircraft_id = "shared-1",
      host_uid = HOST_UID,
      role = ShareRole.SHARE_ROLE_TECHNICIAN,
    )
    every { refStore.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
      listOf(StorageEntity("shared-1", ref, Instant.DISTANT_PAST))
    )
    every { store.observe("shared-1", EntityScope.userRoot(HOST_UID)) } returns flowOf(
      StorageEntity("shared-1", shared, Instant.DISTANT_PAST)
    )

    val result = manager.observeFleetDashboard().first()

    assertThat(result).hasSize(2)
    val ownEntry = result.first { !it.shared }
    val sharedEntry = result.first { it.shared }
    assertThat(ownEntry.aircraft.id).isEqualTo("own-1")
    assertThat(ownEntry.role).isEqualTo(ShareRole.SHARE_ROLE_OWNER)
    assertThat(sharedEntry.aircraft.id).isEqualTo("shared-1")
    assertThat(sharedEntry.role).isEqualTo(ShareRole.SHARE_ROLE_TECHNICIAN)
  }

  @Test
  fun observeFleetDashboard_sharedRefWithUnsyncedDoc_isSkipped() = runTest {
    every { store.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(emptyList())
    val ref = SharedAircraftRef(aircraft_id = "shared-1", host_uid = HOST_UID)
    every { refStore.observeAll(EntityScope.userRoot(TEST_USER_ID)) } returns flowOf(
      listOf(StorageEntity("shared-1", ref, Instant.DISTANT_PAST))
    )
    // Aircraft doc not synced yet → null.
    every { store.observe("shared-1", EntityScope.userRoot(HOST_UID)) } returns flowOf(null)

    val result = manager.observeFleetDashboard().first()

    assertThat(result).isEmpty()
  }

  @Test
  fun loadAircraft_withoutLoggedInUser_emitsNull() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val result = manager.loadAircraft(TEST_AIRCRAFT_ID)
      .first()

    assertThat(result).isNull()
  }

  @Test
  fun loadAircraft_loggedIn_delegatesToStoreAndUnwrapsValue() = runTest {
    val aircraft = buildTestAircraft(id = TEST_AIRCRAFT_ID)
    val entity = StorageEntity(
      id = TEST_AIRCRAFT_ID,
      value = aircraft,
      updatedAt = Instant.DISTANT_PAST
    )
    every {
      store.observe(
        TEST_AIRCRAFT_ID,
        EntityScope.userRoot(TEST_USER_ID)
      )
    } returns flowOf(entity)

    val result = manager.loadAircraft(TEST_AIRCRAFT_ID)
      .first()

    assertThat(result).isEqualTo(aircraft)
  }

  @Test
  fun updateAircraft_withEmptyId_generatesIdAndCallsStorePut() = runTest {
    val aircraft = buildTestAircraft(id = "")

    val result = manager.updateAircraft(aircraft)

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
  fun updateAircraft_withExistingId_preservesIdAndCallsStorePut() = runTest {
    val aircraft = buildTestAircraft(id = TEST_AIRCRAFT_ID)

    val result = manager.updateAircraft(aircraft)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        TEST_AIRCRAFT_ID,
        aircraft,
        EntityScope.userRoot(TEST_USER_ID)
      )
    }
  }

  @Test
  fun updateAircraft_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result =
      manager.updateAircraft(buildTestAircraft(id = TEST_AIRCRAFT_ID))

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun deleteAircraft_loggedIn_callsStoreDeleteAndReturnsSuccess() = runTest {
    val result = manager.deleteAircraft(TEST_AIRCRAFT_ID)

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.delete(
        TEST_AIRCRAFT_ID,
        EntityScope.userRoot(TEST_USER_ID)
      )
    }
  }

  @Test
  fun deleteAircraft_withoutLoggedInUser_returnsFailure() = runTest {
    every { firebaseAuth.currentUser } returns null

    val result = manager.deleteAircraft(TEST_AIRCRAFT_ID)

    assertThat(result.isFailure).isTrue()
  }

  private fun buildTestAircraft(
    id: String = TEST_AIRCRAFT_ID,
    make: String = "Cessna",
    model: String = "172",
  ): Aircraft = Aircraft(id = id, make = make, model = model)
}
