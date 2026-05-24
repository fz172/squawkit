package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPreferencesTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var auth: FirebaseAuth
  private val authState = MutableStateFlow<FirebaseUser?>(null)

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous().create(driver)
    db = createWingsLogDatabase(driver)

    auth = mockk(relaxed = true)
    every { auth.authStateChanged } returns authState
  }

  @Test
  fun initialValue_isTrue() = runTest {
    val preferences = SyncPreferences(db, auth, ioContext = UnconfinedTestDispatcher(testScheduler))
    assertThat(preferences.state.value.cloudSyncEnabled).isTrue()
  }

  @Test
  fun userSignedIn_persistedValueIsUsed() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val preferences = SyncPreferences(db, auth, ioContext = dispatcher, scope = backgroundScope)

    val user1 = mockUser("user-1")
    every { auth.currentUser } returns user1
    authState.value = user1

    // Initially true
    assertThat(preferences.state.value.cloudSyncEnabled).isTrue()

    // Change to false
    preferences.setCloudSyncEnabled(false)
    preferences.state.filter { !it.cloudSyncEnabled }.first()
    assertThat(preferences.state.value.cloudSyncEnabled).isFalse()

    // Switch to another user
    val user2 = mockUser("user-2")
    every { auth.currentUser } returns user2
    authState.value = user2
    
    // Should be back to true for user2
    preferences.state.filter { it.cloudSyncEnabled }.first()
    assertThat(preferences.state.value.cloudSyncEnabled).isTrue()

    // Switch back to user 1
    every { auth.currentUser } returns user1
    authState.value = user1
    
    // Should be false again for user1
    preferences.state.filter { !it.cloudSyncEnabled }.first()
    assertThat(preferences.state.value.cloudSyncEnabled).isFalse()
  }

  @Test
  fun signedOut_usesDefault() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val preferences = SyncPreferences(db, auth, ioContext = dispatcher, scope = backgroundScope)
    authState.value = null
    assertThat(preferences.state.value.cloudSyncEnabled).isTrue()
  }

  @Test
  fun setCloudSyncEnabled_updatesDatabase() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val preferences = SyncPreferences(db, auth, ioContext = dispatcher, scope = backgroundScope)
    val uid = "user-1"
    val user = mockUser(uid)
    authState.value = user
    every { auth.currentUser } returns user

    preferences.setCloudSyncEnabled(false)

    val dbValue = db.schemaQueries.selectConfig(uid, "cloud_sync_enabled").executeAsOneOrNull()
    assertThat(dbValue).isEqualTo("false")
  }

  private fun mockUser(uid: String): FirebaseUser = mockk {
    every { this@mockk.uid } returns uid
    every { isAnonymous } returns false
  }
}
