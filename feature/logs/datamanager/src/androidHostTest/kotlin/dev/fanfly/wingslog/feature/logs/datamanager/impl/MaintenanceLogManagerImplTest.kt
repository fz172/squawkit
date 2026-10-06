package dev.fanfly.wingslog.feature.logs.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.ManualMeterReading
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.Squawk
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
import kotlin.time.Clock
import kotlin.time.Instant

private const val TEST_USER_ID = "test-user-123"
private const val TEST_THING_ID = "thing-456"
private const val ENGINE = "engine_hours"
private val SCOPE = EntityScope.thingChildUnsafe(TEST_USER_ID, TEST_THING_ID)

class MaintenanceLogManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var logStore: EntityStore<MaintenanceLog>
  private lateinit var overviewStore: EntityStore<MaintenanceOverview>
  private lateinit var manualStore: EntityStore<ManualMeterReading>
  private lateinit var squawkStore: EntityStore<Squawk>
  private lateinit var manager: MaintenanceLogManagerImpl

  // 3 May 2026, 09:00 UTC. A var so a test can move the clock between two saves.
  private var now: Instant = Instant.parse("2026-05-03T09:00:00Z")

  @Before
  fun setUp() {
    firebaseAuth = mockk(relaxed = true)
    logStore = mockk(relaxed = true)
    overviewStore = mockk(relaxed = true)
    manualStore = mockk(relaxed = true)
    squawkStore = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<MaintenanceLog>(CollectionKind.MaintenanceLog) } returns logStore
    every {
      storeFactory.create<MaintenanceOverview>(CollectionKind.MaintenanceOverview)
    } returns overviewStore
    every {
      storeFactory.create<ManualMeterReading>(CollectionKind.ManualReading)
    } returns manualStore
    every { manualStore.observeAll(any()) } returns flowOf(emptyList())
    every { storeFactory.create<Squawk>(CollectionKind.Squawk) } returns squawkStore
    every { logStore.observeAll(any()) } returns flowOf(emptyList())
    every { squawkStore.observeAll(any()) } returns flowOf(emptyList())

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { firebaseAuth.currentUser } returns mockUser
    every { firebaseAuth.authStateChanged } returns flowOf(mockUser)

    manager = MaintenanceLogManagerImpl(
      FakeScopeResolver(firebaseAuth),
      storeFactory,
      clock = object : Clock {
        override fun now(): Instant = now
      },
    )
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
      squawkStore.put(
        "squawk-1",
        Squawk(id = "squawk-1", addressed_by_log_id = ""),
        scope
      )
    }
    coVerify(exactly = 0) { squawkStore.put("squawk-2", any(), any()) }
    coVerify(exactly = 0) { squawkStore.put("squawk-3", any(), any()) }
  }

  @Test
  fun deleteLog_withNoAddressedSquawks_writesNoSquawks() = runTest {
    manager.deleteLog(TEST_THING_ID, "log-1")

    coVerify(exactly = 0) { squawkStore.put(any(), any(), any()) }
  }

  @Test
  fun addLog_stampsWhenItsReadingsWereSaved() = runTest {
    manager.addLog(TEST_THING_ID, engineLog(hours = 1201.5))

    coVerify {
      logStore.put(
        "log-1",
        engineLog(hours = 1201.5).copy(readings_saved_at = now.toWireInstant()),
        SCOPE
      )
    }
  }

  @Test
  fun addLog_withNoReadings_carriesNoStamp() = runTest {
    // Nothing to order, so nothing to stamp.
    manager.addLog(TEST_THING_ID, MaintenanceLog(id = "log-1"))

    coVerify { logStore.put("log-1", MaintenanceLog(id = "log-1"), SCOPE) }
  }

  @Test
  fun updateLog_thatLeavesTheReadingsAlone_keepsTheirStamp() = runTest {
    // Fixing a typo in the description must not make an old reading the newest again. The form
    // rebuilds the log without the stamp, so it is the stored one that has to survive.
    val savedAt = Instant.parse("2026-04-01T12:00:00Z").toWireInstant()
    storedLog(engineLog(hours = 1201.5).copy(readings_saved_at = savedAt))

    manager.updateLog(
      TEST_THING_ID,
      engineLog(hours = 1201.5).copy(work_description = "Oil change")
    )

    coVerify {
      logStore.put(
        "log-1",
        engineLog(hours = 1201.5).copy(
          work_description = "Oil change",
          readings_saved_at = savedAt
        ),
        SCOPE
      )
    }
  }

  @Test
  fun updateLog_thatChangesAReading_stampsItAgain() = runTest {
    storedLog(
      engineLog(hours = 12015.0).copy(
        readings_saved_at = Instant.parse("2026-04-01T12:00:00Z").toWireInstant()
      )
    )

    manager.updateLog(TEST_THING_ID, engineLog(hours = 1201.5))

    coVerify {
      logStore.put(
        "log-1",
        engineLog(hours = 1201.5).copy(readings_saved_at = now.toWireInstant()),
        SCOPE
      )
    }
  }

  @Test
  fun setManualReading_storesItUnderTheMetersKeyWithTheTime() = runTest {
    manager.setManualReading(TEST_THING_ID, ENGINE, 1200.0)

    coVerify {
      manualStore.put(
        ENGINE,
        ManualMeterReading(
          reading = MeterReading(ENGINE, value_ = 1200.0),
          set_at = now.toWireInstant(),
        ),
        SCOPE
      )
    }
  }

  @Test
  fun setManualReading_rebuildsTheOverviewWithIt() = runTest {
    // An older log reads higher; the reading set today is the current one all the same.
    every { logStore.observeAll(SCOPE) } returns flowOf(
      listOf(
        row(
          engineLog(hours = 1300.0).copy(
            timestamp = Instant.parse("2026-04-01T00:00:00Z").toWireInstant()
          )
        )
      )
    )
    every { manualStore.observeAll(SCOPE) } returns flowOf(
      listOf(
        row(
          ManualMeterReading(
            reading = MeterReading(ENGINE, value_ = 1200.0),
            set_at = now.toWireInstant(),
          ),
          id = ENGINE,
        )
      )
    )

    manager.setManualReading(TEST_THING_ID, ENGINE, 1200.0)

    coVerify {
      overviewStore.put(
        "main",
        match { it.current == listOf(MeterReading(ENGINE, value_ = 1200.0)) },
        SCOPE
      )
    }
  }

  @Test
  fun observeCurrentReadings_combinesLogsAndManualReadings() = runTest {
    every { logStore.observeAll(SCOPE) } returns flowOf(
      listOf(
        row(
          MaintenanceLog(
            id = "log-1",
            timestamp = Instant.parse("2026-04-01T00:00:00Z").toWireInstant(),
            readings = listOf(
              MeterReading(ENGINE, value_ = 1300.0),
              MeterReading("airframe_hours", value_ = 2100.0),
            ),
          )
        )
      )
    )
    every { manualStore.observeAll(SCOPE) } returns flowOf(
      listOf(
        row(
          ManualMeterReading(
            reading = MeterReading(ENGINE, value_ = 1200.0),
            set_at = now.toWireInstant(),
          ),
          id = ENGINE,
        )
      )
    )

    val current = manager.observeCurrentReadings(TEST_THING_ID)
      .first()

    assertThat(current.map { it.meterKey to it.value }).containsExactly(
      "airframe_hours" to 2100.0,
      ENGINE to 1200.0,
    )
    assertThat(current.first { it.meterKey == ENGINE }.isManual).isTrue()
    assertThat(current.first { it.meterKey == "airframe_hours" }.isManual).isFalse()
  }

  private fun engineLog(hours: Double) = MaintenanceLog(
    id = "log-1",
    readings = listOf(MeterReading(ENGINE, value_ = hours)),
  )

  private fun storedLog(log: MaintenanceLog) {
    every { logStore.observe(log.id, SCOPE) } returns flowOf(row(log))
  }

  private fun <T : Any> row(value: T, id: String = "log-1"): StorageEntity<T> =
    StorageEntity(
      id = id,
      value = value,
      updatedAt = Instant.fromEpochSeconds(0),
    )

  private fun squawkRow(
    id: String,
    addressedBy: String
  ): StorageEntity<Squawk> =
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
