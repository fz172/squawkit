package dev.fanfly.wingslog.feature.settings.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.userprofile.database.UserProfileManager
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val TEST_USER_ID = "test-user-123"

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var authManager: AuthManager
  private lateinit var userProfileManager: UserProfileManager
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var dbChecker: DatabaseIntegrityChecker
  private lateinit var featureLabManager: FeatureLabManager
  private lateinit var viewModel: SettingsViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    authManager = mockk(relaxed = true)
    userProfileManager = mockk(relaxed = true)
    attachmentManager = mockk(relaxed = true)
    dbChecker = mockk(relaxed = true)
    featureLabManager = mockk(relaxed = true)
    every { featureLabManager.observe() } returns flowOf(FeatureFlags())

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { mockUser.photoURL } returns null
    every { mockUser.displayName } returns "Test User"
    every { authManager.getCurrentUser() } returns mockUser

    // observeLicenseInfo() is called in init — return an empty flow to avoid blocking.
    every { userProfileManager.observeLicenseInfo() } returns flowOf(null)

    justRun { dbChecker.wipeDataForUser(any()) }
    coJustRun { attachmentManager.wipeLocalData(any()) }
    coJustRun { authManager.logOut() }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun logOut_wipesUserData_whenUserSignedIn() = runTest(testDispatcher) {
    viewModel = SettingsViewModel(authManager, userProfileManager, attachmentManager, dbChecker, featureLabManager)

    viewModel.logOut()
    advanceUntilIdle()

    verify { dbChecker.wipeDataForUser(TEST_USER_ID) }
  }

  @Test
  fun logOut_wipesAttachmentData_whenUserSignedIn() = runTest(testDispatcher) {
    viewModel = SettingsViewModel(authManager, userProfileManager, attachmentManager, dbChecker, featureLabManager)

    viewModel.logOut()
    advanceUntilIdle()

    coVerify { attachmentManager.wipeLocalData(TEST_USER_ID) }
  }

  @Test
  fun logOut_callsAuthManagerLogOut() = runTest(testDispatcher) {
    viewModel = SettingsViewModel(authManager, userProfileManager, attachmentManager, dbChecker, featureLabManager)

    viewModel.logOut()
    advanceUntilIdle()

    coVerify { authManager.logOut() }
  }

  @Test
  fun logOut_skipsWipe_whenNoUserSignedIn() = runTest(testDispatcher) {
    every { authManager.getCurrentUser() } returns null
    viewModel = SettingsViewModel(authManager, userProfileManager, attachmentManager, dbChecker, featureLabManager)

    viewModel.logOut()
    advanceUntilIdle()

    verify(exactly = 0) { dbChecker.wipeDataForUser(any()) }
    coVerify(exactly = 0) { attachmentManager.wipeLocalData(any()) }
  }

  @Test
  fun logOut_setsStateToLoggedOut() = runTest(testDispatcher) {
    viewModel = SettingsViewModel(authManager, userProfileManager, attachmentManager, dbChecker, featureLabManager)

    viewModel.logOut()
    advanceUntilIdle()

    assertThat(viewModel.user.value.userStatus).isEqualTo(UserStatus.LOGGED_OUT)
    assertThat(viewModel.user.value.displayName).isNull()
    assertThat(viewModel.user.value.photoUri).isNull()
  }
}
