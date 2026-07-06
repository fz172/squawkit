package dev.fanfly.wingslog.feature.logs.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val TEST_USER_ID = "test-user-123"
private const val TEST_AIRCRAFT_ID = "aircraft-456"

class MaintenanceLogManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var logStore: EntityStore<MaintenanceLog>
  private lateinit var overviewStore: EntityStore<*>
  private lateinit var manager: MaintenanceLogManagerImpl

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    logStore = mockk(relaxed = true)
    overviewStore = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<MaintenanceLog>(CollectionKind.MaintenanceLog) } returns logStore
    every { storeFactory.create<Any>(CollectionKind.MaintenanceOverview) } returns
      overviewStore as EntityStore<Any>

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { firebaseAuth.currentUser } returns mockUser
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

    manager = MaintenanceLogManagerImpl(firebaseAuth, storeFactory)
  }

  @Test
  fun observeLogs_withoutLoggedInUser_emitsEmptyList() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    var emittedList: List<MaintenanceLog>? = null
    manager.observeLogs(TEST_AIRCRAFT_ID)
      .collect {
        emittedList = it
      }

    assertThat(emittedList).isEmpty()
  }

  @Test
  fun observeLogs_loggedIn_delegatesToStoreWithAircraftScope() = runTest {
    every {
      logStore.observeAll(
        EntityScope.aircraftChild(
          TEST_USER_ID,
          TEST_AIRCRAFT_ID
        )
      )
    } returns flowOf(emptyList())

    val result = mutableListOf<List<MaintenanceLog>>()
    manager.observeLogs(TEST_AIRCRAFT_ID)
      .collect { result += it }

    assertThat(result).hasSize(1)
    assertThat(result.first()).isEmpty()
    io.mockk.verify {
      logStore.observeAll(
        EntityScope.aircraftChild(
          TEST_USER_ID,
          TEST_AIRCRAFT_ID
        )
      )
    }
  }
}
