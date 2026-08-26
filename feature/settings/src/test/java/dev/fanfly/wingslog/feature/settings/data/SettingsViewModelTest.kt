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
import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.notifications.datamanager.SignOutCoordinator
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
private const val TEST_USER_EMAIL = "pilot@example.com"

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
  private lateinit var adConsentManager: AdConsentManager
  private lateinit var notificationPermission: NotificationPermission
  private lateinit var notificationPrefsManager: NotificationPrefsManager
  private lateinit var signOutCoordinator: SignOutCoordinator
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
    adConsentManager = mockk(relaxed = true)
    notificationPermission = mockk(relaxed = true)
    notificationPrefsManager = mockk(relaxed = true)
    signOutCoordinator = mockk(relaxed = true)
    every { notificationPermission.observe() } returns MutableStateFlow(PermissionState.GRANTED)
    every { notificationPrefsManager.observe() } returns
      flowOf(PrefsState.Resolved(NotificationSettings()))
    appearanceController = AppearanceController(InMemoryAppearanceStore())
    analyticsPreferenceController = AnalyticsPreferenceController(
      InMemoryAnalyticsPreferenceStore(),
      mockk(relaxed = true),
    )
    every { featureLabManager.observe() } returns flowOf(DeveloperFlags())

    every { authManager.getCurrentUser() } returns userWithEmail(TEST_USER_EMAIL)

    coJustRun { dbChecker.wipeDataForUser(any()) }
    coJustRun { attachmentManager.wipeLocalData(any()) }
    coJustRun { authManager.logOut() }
    coJustRun { signOutCoordinator.signOut() }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun logOut_wipesUserData_whenUserSignedIn() = runTest(testDispatcher) {
    viewModel = buildViewModel()

    viewModel.logOut()
    advanceUntilIdle()

    coVerify { dbChecker.wipeDataForUser(TEST_USER_ID) }
  }

  @Test
  fun logOut_wipesAttachmentData_whenUserSignedIn() = runTest(testDispatcher) {
    viewModel = buildViewModel()

    viewModel.logOut()
    advanceUntilIdle()

    coVerify { attachmentManager.wipeLocalData(TEST_USER_ID) }
  }

  /**
   * Through the coordinator, never `authManager.logOut()` directly — that shortcut is exactly what
   * left the corruption-recovery path signing out without clearing this device's push token (#550).
   */
  @Test
  fun logOut_signsOutThroughTheCoordinator() = runTest(testDispatcher) {
    viewModel = buildViewModel()

    viewModel.logOut()
    advanceUntilIdle()

    coVerify { signOutCoordinator.signOut() }
    coVerify(exactly = 0) { authManager.logOut() }
  }

  @Test
  fun logOut_skipsWipe_whenNoUserSignedIn() = runTest(testDispatcher) {
    every { authManager.getCurrentUser() } returns null
    viewModel = buildViewModel()

    viewModel.logOut()
    advanceUntilIdle()

    coVerify(exactly = 0) { dbChecker.wipeDataForUser(any()) }
    coVerify(exactly = 0) { attachmentManager.wipeLocalData(any()) }
  }

  @Test
  fun logOut_setsStateToLoggedOut() = runTest(testDispatcher) {
    viewModel = buildViewModel()

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

      // Directly, not through the coordinator: deleteMyAccount already removed push_devices with the
      // rest of users/{uid}, and the Auth user is gone server-side, so there is nothing left to
      // clear and no session left to authorize the attempt.
      coVerify { authManager.logOut() }
      coVerify(exactly = 0) { signOutCoordinator.signOut() }
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

  /** With an address the pilot would recognise, that address is what the dialog asks them to type. */
  @Test
  fun askingToDelete_challengesWithTheAccountEmail() = runTest(testDispatcher) {
    viewModel = buildViewModel()

    viewModel.askToDeleteAccount()

    assertThat(viewModel.user.value.deletionChallenge)
      .isEqualTo(DeletionChallenge.Email(TEST_USER_EMAIL))
  }

  /**
   * Apple Hide My Email gives us an alias the pilot has never been shown. Asking them to type it
   * would be an unanswerable question, so the fixed phrase takes over.
   */
  @Test
  fun askingToDelete_fallsBackToThePhraseForAHiddenAppleAddress() = runTest(testDispatcher) {
    every { authManager.getCurrentUser() } returns
      userWithEmail("abc123xyz@privaterelay.appleid.com")
    viewModel = buildViewModel()

    viewModel.askToDeleteAccount()

    assertThat(viewModel.user.value.deletionChallenge).isEqualTo(DeletionChallenge.Phrase)
  }

  /** Same fallback when the provider handed us no address at all. */
  @Test
  fun askingToDelete_fallsBackToThePhraseWithoutAnEmail() = runTest(testDispatcher) {
    every { authManager.getCurrentUser() } returns userWithEmail(null)
    viewModel = buildViewModel()

    viewModel.askToDeleteAccount()

    assertThat(viewModel.user.value.deletionChallenge).isEqualTo(DeletionChallenge.Phrase)
  }

  /** Reopening starts from a blank field, so yesterday's typing cannot arm today's confirm. */
  @Test
  fun askingToDelete_clearsAnyEarlierTyping() = runTest(testDispatcher) {
    viewModel = buildViewModel()
    viewModel.askToDeleteAccount()
    viewModel.setDeleteAccountInput(TEST_USER_EMAIL)

    viewModel.cancelDeleteAccount()
    viewModel.askToDeleteAccount()

    assertThat(viewModel.user.value.deletionInput).isEmpty()
  }

  /** Typed text belongs to the ViewModel, not to a composable that a rotation can throw away. */
  @Test
  fun typedConfirmation_isKeptInState() = runTest(testDispatcher) {
    viewModel = buildViewModel()
    viewModel.askToDeleteAccount()

    viewModel.setDeleteAccountInput("pilot@")

    assertThat(viewModel.user.value.deletionInput).isEqualTo("pilot@")
  }

  /** A delete already in flight cannot be re-aimed by editing the field behind the spinner. */
  @Test
  fun typedConfirmation_isFrozenWhileTheDeleteRuns() = runTest(testDispatcher) {
    coEvery { accountDeleter.deleteAccount() } coAnswers {
      viewModel.setDeleteAccountInput("changed my mind")
      assertThat(viewModel.user.value.deletionInput).isNotEqualTo("changed my mind")
      false
    }
    viewModel = buildViewModel()
    viewModel.askToDeleteAccount()
    viewModel.setDeleteAccountInput(TEST_USER_EMAIL)

    viewModel.confirmDeleteAccount()
    advanceUntilIdle()

    assertThat(viewModel.user.value.deletion).isEqualTo(AccountDeletion.Failed)
  }

  @Test
  fun notificationsRowState_grantedAndOn_isDefault() = runTest(testDispatcher) {
    viewModel = buildViewModel()
    advanceUntilIdle()

    assertThat(viewModel.user.value.notificationsRowState).isEqualTo(NotificationsRowState.DEFAULT)
  }

  @Test
  fun notificationsRowState_masterSwitchOff_isOff() = runTest(testDispatcher) {
    every { notificationPrefsManager.observe() } returns
      flowOf(PrefsState.Resolved(NotificationSettings(all_disabled = true)))

    viewModel = buildViewModel()
    advanceUntilIdle()

    assertThat(viewModel.user.value.notificationsRowState).isEqualTo(NotificationsRowState.OFF)
  }

  @Test
  fun notificationsRowState_permissionDenied_isBlocked() = runTest(testDispatcher) {
    every { notificationPermission.observe() } returns MutableStateFlow(PermissionState.DENIED)

    viewModel = buildViewModel()
    advanceUntilIdle()

    assertThat(viewModel.user.value.notificationsRowState).isEqualTo(NotificationsRowState.BLOCKED)
  }

  @Test
  fun notificationsRowState_permissionUnsupported_isBlocked() = runTest(testDispatcher) {
    every { notificationPermission.observe() } returns MutableStateFlow(PermissionState.UNSUPPORTED)

    viewModel = buildViewModel()
    advanceUntilIdle()

    assertThat(viewModel.user.value.notificationsRowState).isEqualTo(NotificationsRowState.BLOCKED)
  }

  /** An OS-level block is true regardless of the in-app toggle, and is the more actionable fix. */
  @Test
  fun notificationsRowState_deniedAndMasterSwitchOff_blockedWins() = runTest(testDispatcher) {
    every { notificationPermission.observe() } returns MutableStateFlow(PermissionState.DENIED)
    every { notificationPrefsManager.observe() } returns
      flowOf(PrefsState.Resolved(NotificationSettings(all_disabled = true)))

    viewModel = buildViewModel()
    advanceUntilIdle()

    assertThat(viewModel.user.value.notificationsRowState).isEqualTo(NotificationsRowState.BLOCKED)
  }

  /** Unresolved preferences must never read as OFF — that would be a guess, not a fact. */
  @Test
  fun notificationsRowState_prefsUnresolved_readsAsDefault() = runTest(testDispatcher) {
    every { notificationPrefsManager.observe() } returns flowOf(PrefsState.Unresolved)

    viewModel = buildViewModel()
    advanceUntilIdle()

    assertThat(viewModel.user.value.notificationsRowState).isEqualTo(NotificationsRowState.DEFAULT)
  }

  private fun userWithEmail(email: String?) = mockk<FirebaseUser> {
    every { uid } returns TEST_USER_ID
    every { isAnonymous } returns false
    every { this@mockk.email } returns email
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
      isStressTestSupported = false,
      isCameraCaptureSupported = false,
      isAnonymousLoginSupported = true,
      isAdsSupported = false,
    ),
    adConsentManager,
    notificationPermission,
    notificationPrefsManager,
    signOutCoordinator,
  )
}
