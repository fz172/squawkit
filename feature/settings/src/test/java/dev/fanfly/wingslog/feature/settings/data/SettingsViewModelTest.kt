package dev.fanfly.wingslog.feature.settings.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceController
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceStore
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AccountDeleter
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.AppearanceMode
import dev.fanfly.wingslog.core.ui.theme.AppearanceStore
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
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
  private lateinit var accountDeleter: AccountDeleter
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var dbChecker: DatabaseIntegrityChecker
  private lateinit var featureLabManager: DeveloperOptionsManager
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
    accountDeleter = mockk(relaxed = true)
    attachmentManager = mockk(relaxed = true)
    dbChecker = mockk(relaxed = true)
    featureLabManager = mockk(relaxed = true)
    appearanceController = AppearanceController(InMemoryAppearanceStore())
    analyticsPreferenceController = AnalyticsPreferenceController(
      InMemoryAnalyticsPreferenceStore(),
      mockk(relaxed = true),
    )
    every { featureLabManager.observe() } returns flowOf(DeveloperFlags())

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
      accountDeleter,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isDeveloperOptionsSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isSubscriptionSupported = false,
        isAdsSupported = false,
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
      accountDeleter,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isDeveloperOptionsSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isSubscriptionSupported = false,
        isAdsSupported = false,
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
      accountDeleter,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isDeveloperOptionsSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isSubscriptionSupported = false,
        isAdsSupported = false,
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
      accountDeleter,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isDeveloperOptionsSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isSubscriptionSupported = false,
        isAdsSupported = false,
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
      accountDeleter,
      attachmentManager,
      dbChecker,
      featureLabManager,
      appearanceController,
      analyticsPreferenceController,
      AppCapability(
        isDeveloperOptionsSupported = false,
        isAircraftSharingSupported = true,
        isStressTestSupported = false,
        isCameraCaptureSupported = false,
        isAnonymousLoginSupported = false,
        isSubscriptionSupported = false,
        isAdsSupported = false,
      ),
    )

    viewModel.logOut()
    advanceUntilIdle()

    assertThat(viewModel.user.value.userStatus).isEqualTo(UserStatus.LOGGED_OUT)
  }

  /**
   * The Settings account row picks between "Log in" (start the upgrade) and "Log out" purely from
   * this flag, and the only other writer — refreshAccountState() — runs after a *completed*
   * upgrade. So a guest that starts at the `false` default can never reach the control that would
   * change it: they are shown "Log out", which for a guest erases the only copy of their data.
   */
  @Test
  fun init_readsAnonymousStateFromTheCurrentUser() = runTest(testDispatcher) {
    val guest = mockk<FirebaseUser>()
    every { guest.uid } returns TEST_USER_ID
    every { guest.isAnonymous } returns true
    every { authManager.getCurrentUser() } returns guest

    viewModel = buildViewModel()

    assertThat(viewModel.user.value.isAnonymous).isTrue()
  }

  @Test
  fun init_leavesAnonymousFalseForAPermanentAccount() =
    runTest(testDispatcher) {
      // setUp() already stubs a non-anonymous user.
      viewModel = buildViewModel()

      assertThat(viewModel.user.value.isAnonymous).isFalse()
    }

  @Test
  fun init_leavesAnonymousFalseWhenThereIsNoUser() = runTest(testDispatcher) {
    every { authManager.getCurrentUser() } returns null

    viewModel = buildViewModel()

    assertThat(viewModel.user.value.isAnonymous).isFalse()
  }

  /**
   * The one property that matters most here. A failed delete leaves the account and its records
   * intact — and those records may exist nowhere else — so wiping the device would destroy the only
   * copy, in response to an operation that did not happen.
   */
  @Test
  fun deleteAccount_leavesLocalDataAloneWhenTheServerRefuses() =
    runTest(testDispatcher) {
      coEvery { accountDeleter.deleteAccount() } returns false
      viewModel = buildViewModel()

      viewModel.confirmDeleteAccount()
      advanceUntilIdle()

      assertThat(viewModel.user.value.deletion).isEqualTo(AccountDeletion.Failed)
      coVerify(exactly = 0) { attachmentManager.wipeLocalData(any()) }
      coVerify(exactly = 0) { dbChecker.wipeDataForUser(any()) }
      coVerify(exactly = 0) { authManager.logOut() }
    }

  @Test
  fun deleteAccount_wipesTheDeviceOnceTheAccountIsGone() =
    runTest(testDispatcher) {
      coEvery { accountDeleter.deleteAccount() } returns true
      viewModel = buildViewModel()

      viewModel.confirmDeleteAccount()
      advanceUntilIdle()

      coVerify { authManager.logOut() }
      coVerify { attachmentManager.wipeLocalData(any()) }
      coVerify { dbChecker.wipeDataForUser(any()) }
      assertThat(viewModel.user.value.userStatus).isEqualTo(UserStatus.LOGGED_OUT)
    }

  /** The row opens a confirmation; it must not delete anything by itself. */
  @Test
  fun askingToDelete_destroysNothing() = runTest(testDispatcher) {
    viewModel = buildViewModel()

    viewModel.askToDeleteAccount()
    advanceUntilIdle()

    assertThat(viewModel.user.value.deletion).isEqualTo(AccountDeletion.Confirming)
    coVerify(exactly = 0) { accountDeleter.deleteAccount() }
  }

  private fun buildViewModel() = SettingsViewModel(
    authManager,
    accountDeleter,
    attachmentManager,
    dbChecker,
    featureLabManager,
    appearanceController,
    analyticsPreferenceController,
    AppCapability(
      isDeveloperOptionsSupported = false,
      isAircraftSharingSupported = true,
      isStressTestSupported = false,
      isCameraCaptureSupported = false,
      isAnonymousLoginSupported = true,
      isSubscriptionSupported = false,
      isAdsSupported = false,
    ),
  )
}
