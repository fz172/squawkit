package dev.fanfly.wingslog.feature.notifications.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.sync.data.HydrationState
import dev.fanfly.wingslog.feature.sync.data.SyncCursor
import dev.fanfly.wingslog.feature.sync.data.SyncCursorStore
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

private const val TEST_UID = "uid-1"

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPrefsManagerImplTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private var cloudSyncEnabled = true
  private val cloudSyncSetting = CloudSyncSetting { cloudSyncEnabled }
  private lateinit var cursorStore: SyncCursorStore
  private lateinit var syncEngine: SyncEngine
  private val hydrationState =
    MutableStateFlow<HydrationState>(HydrationState.Idle)
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var store: EntityStore<NotificationSettings>
  private lateinit var manager: NotificationPrefsManagerImpl

  @Before
  fun setUp() {
    cloudSyncEnabled = true
    firebaseAuth = mockk(relaxed = true)
    cursorStore = mockk(relaxed = true)
    syncEngine = mockk(relaxed = true)
    every { syncEngine.hydrationState } returns hydrationState
    store = mockk(relaxed = true)
    storeFactory = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    every { storeFactory.create<NotificationSettings>(CollectionKind.NotificationSettings) } returns store

    val user = mockk<FirebaseUser>()
    every { user.uid } returns TEST_UID
    every { firebaseAuth.currentUser } returns user
    every { firebaseAuth.authStateChanged } returns flowOf(user)

    every { store.observe(any(), any()) } returns flowOf(null)
    coEvery { cursorStore.get(any(), any(), any()) } returns null

    manager = NotificationPrefsManagerImpl(
      firebaseAuth = firebaseAuth,
      cloudSyncSetting = cloudSyncSetting,
      cursorStore = cursorStore,
      syncEngine = syncEngine,
      storeFactory = storeFactory,
    )
  }

  @Test
  fun signedOut_resolvesToDefaults_neverUnresolved() = runTest {
    every { firebaseAuth.currentUser } returns null
    every { firebaseAuth.authStateChanged } returns flowOf(null)

    val state = manager.observe()
      .first()

    assertThat(state).isEqualTo(PrefsState.Resolved(NotificationSettings()))
  }

  @Test
  fun cloudSyncOff_noRow_resolvesToDefaults_withoutReadingTheCursor() =
    runTest {
      cloudSyncEnabled = false

      val state = manager.observe()
        .first()

      assertThat(state).isEqualTo(PrefsState.Resolved(NotificationSettings()))
      coVerify(exactly = 0) { cursorStore.get(any(), any(), any()) }
    }

  @Test
  fun cloudSyncOff_rowExists_resolvesToTheRow() = runTest {
    cloudSyncEnabled = false
    val settings = NotificationSettings(aog_disabled = true)
    every { store.observe(any(), any()) } returns flowOf(testEntity(settings))

    val state = manager.observe()
      .first()

    assertThat(state).isEqualTo(PrefsState.Resolved(settings))
  }

  @Test
  fun rowExists_resolvesToTheRow_regardlessOfCursor() = runTest {
    val settings = NotificationSettings(overdue_disabled = true)
    every { store.observe(any(), any()) } returns flowOf(testEntity(settings))

    val state = manager.observe()
      .first()

    assertThat(state).isEqualTo(PrefsState.Resolved(settings))
  }

  @Test
  fun cloudSyncOn_noRow_cursorNotHydrated_emitsUnresolved() = runTest {
    coEvery {
      cursorStore.get(
        TEST_UID,
        CollectionKind.NotificationSettings,
        any()
      )
    } returns
      testCursor(hydrated = false)

    val collected = mutableListOf<PrefsState>()
    val job = launch {
      manager.observe()
        .toList(collected)
    }
    advanceTimeBy(1.seconds)
    job.cancel()

    assertThat(collected).containsExactly(PrefsState.Unresolved)
  }

  @Test
  fun cloudSyncOn_noRow_cursorHydrated_resolvesToDefaults() = runTest {
    coEvery {
      cursorStore.get(
        TEST_UID,
        CollectionKind.NotificationSettings,
        any()
      )
    } returns
      testCursor(hydrated = true)

    val state = manager.observe()
      .first()

    assertThat(state).isEqualTo(PrefsState.Resolved(NotificationSettings()))
  }

  @Test
  fun cloudSyncOn_stillUnresolvedAfterTimeout_resolvesToDefaults() = runTest {
    coEvery {
      cursorStore.get(
        TEST_UID,
        CollectionKind.NotificationSettings,
        any()
      )
    } returns
      testCursor(hydrated = false)

    val collected = mutableListOf<PrefsState>()
    val job = launch {
      manager.observe()
        .toList(collected)
    }
    advanceTimeBy(6.seconds) // past the 5s PREFS_HYDRATION_TIMEOUT
    job.cancel()

    assertThat(collected).containsExactly(
      PrefsState.Unresolved,
      PrefsState.Resolved(NotificationSettings()),
    )
      .inOrder()
  }

  @Test
  fun update_whileUnresolved_fails_andWritesNothing() = runTest {
    coEvery {
      cursorStore.get(
        TEST_UID,
        CollectionKind.NotificationSettings,
        any()
      )
    } returns
      testCursor(hydrated = false)

    val result = manager.update { it.copy(aog_disabled = true) }

    assertThat(result.isFailure).isTrue()
    coVerify(exactly = 0) { store.put(any(), any(), any()) }
  }

  @Test
  fun update_whileResolved_copiesOntoTheResolvedValue() = runTest {
    val existing = NotificationSettings(overdue_disabled = true)
    every { store.observe(any(), any()) } returns flowOf(testEntity(existing))

    val result = manager.update { it.copy(aog_disabled = true) }

    assertThat(result.isSuccess).isTrue()
    coVerify {
      store.put(
        "main",
        NotificationSettings(overdue_disabled = true, aog_disabled = true),
        any(),
      )
    }
  }

  private fun testEntity(settings: NotificationSettings): StorageEntity<NotificationSettings> =
    StorageEntity(
      id = "main",
      value = settings,
      updatedAt = kotlin.time.Instant.DISTANT_PAST
    )

  private fun testCursor(hydrated: Boolean): SyncCursor =
    SyncCursor(
      uid = TEST_UID,
      kind = CollectionKind.NotificationSettings,
      scope = mockk(relaxed = true),
      hydrated = hydrated,
      lastSeenRemote = null,
      failedAttempts = 0,
      lastAttemptAt = null,
    )
}
