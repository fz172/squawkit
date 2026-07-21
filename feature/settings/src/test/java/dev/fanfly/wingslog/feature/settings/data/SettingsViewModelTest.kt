package dev.fanfly.wingslog.feature.settings.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceController
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceStore
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.AppearanceMode
import dev.fanfly.wingslog.core.ui.theme.AppearanceStore
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var dbChecker: DatabaseIntegrityChecker
  private lateinit var featureLabManager: FeatureLabManager
  private lateinit var appearanceController: AppearanceController
  private lateinit var analyticsPreferenceController: AnalyticsPreferenceController
  private lateinit var viewModel: SettingsViewModel

  /** In-memory [AppearanceStore] so the controller needs no platform backing in tests. */
  private class InMemoryAppearanceStore : AppearanceStore {
    private var mode = AppearanceMode.SYSTEM
    override fun load() = mode
    override fun save(mode: AppearanceMode) {
      this.mode = mode
    }
  }

  /** In-memory [AnalyticsPreferenceStore] so the controller needs no platform backing in tests. */
  private class InMemoryAnalyticsPreferenceStore : AnalyticsPreferenceStore {
    private var enabled = true
    override fun load() = enabled
    override fun save(enabled: Boolean) {
      this.enabled = enabled
    }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    authManager = mockk(relaxed = true)
    attachmentManager = mockk(relaxed = true)
    dbChecker = mockk(relaxed = true)
    featureLabManager = mockk(relaxed = true)
    appearanceController = AppearanceController(InMemoryAppearanceStore())
    analyticsPreferenceController = AnalyticsPreferenceController(
      InMemoryAnalyticsPreferenceStore(),
      mockk(relaxed = true),
    )
    every { featureLabManager.observe() } returns flowOf(FeatureFlags())

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { mockUser.isAnonymous } returns false
    every { authManager.getCurrentUser() } returns mockUser

    coJustRun { dbChecker.wipeDataForUser(any()) }
    coJustRun { attachmentManager.wipeLocalData(any()) }
    coJustRun { authManager.logOut() }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun logOut_wipesUserData_whenUserSignedIn() = runTest(testDispatcher) {
    viewModel = SettingsViewModel(
      authManager,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isFeatureLabSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isAppleSignInSupported = false,
        isSubscriptionSupported = false,
      ),
    )

    viewModel.logOut()
    advanceUntilIdle()

    coVerify { dbChecker.wipeDataForUser(TEST_USER_ID) }
  }

  @Test
  fun logOut_wipesAttachmentData_whenUserSignedIn() = runTest(testDispatcher) {
    viewModel = SettingsViewModel(
      authManager,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isFeatureLabSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isAppleSignInSupported = false,
        isSubscriptionSupported = false,
      ),
    )

    viewModel.logOut()
    advanceUntilIdle()

    coVerify { attachmentManager.wipeLocalData(TEST_USER_ID) }
  }

  @Test
  fun logOut_callsAuthManagerLogOut() = runTest(testDispatcher) {
    viewModel = SettingsViewModel(
      authManager,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isFeatureLabSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isAppleSignInSupported = false,
        isSubscriptionSupported = false,
      ),
    )

    viewModel.logOut()
    advanceUntilIdle()

    coVerify { authManager.logOut() }
  }

  @Test
  fun logOut_skipsWipe_whenNoUserSignedIn() = runTest(testDispatcher) {
    every { authManager.getCurrentUser() } returns null
    viewModel = SettingsViewModel(
      authManager,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isFeatureLabSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isAppleSignInSupported = false,
        isSubscriptionSupported = false,
      ),
    )

    viewModel.logOut()
    advanceUntilIdle()

    coVerify(exactly = 0) { dbChecker.wipeDataForUser(any()) }
    coVerify(exactly = 0) { attachmentManager.wipeLocalData(any()) }
  }

  @Test
  fun logOut_setsStateToLoggedOut() = runTest(testDispatcher) {
    viewModel = SettingsViewModel(
      authManager,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isFeatureLabSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isAppleSignInSupported = false,
        isSubscriptionSupported = false,
      ),
    )

    viewModel.logOut()
    advanceUntilIdle()

    assertThat(viewModel.user.value.userStatus).isEqualTo(UserStatus.LOGGED_OUT)
  }
}
