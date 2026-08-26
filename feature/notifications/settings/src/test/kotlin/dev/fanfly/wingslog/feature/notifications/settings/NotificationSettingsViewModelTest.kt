package dev.fanfly.wingslog.feature.notifications.settings

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.fanfly.wingslog.feature.sync.data.SyncPrefs
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var prefsManager: NotificationPrefsManager
  private lateinit var permission: NotificationPermission
  private lateinit var auth: FirebaseAuth
  private lateinit var syncPreferences: SyncPreferences

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    prefsManager = mockk(relaxed = true)
    permission = mockk(relaxed = true)
    auth = mockk(relaxed = true)
    syncPreferences = mockk(relaxed = true)

    every { prefsManager.observe() } returns flowOf(
      PrefsState.Resolved(
        NotificationSettings()
      )
    )
    every { permission.observe() } returns MutableStateFlow(PermissionState.GRANTED)
    every { permission.canOpenSystemSettings } returns true
    every { auth.authStateChanged } returns flowOf(signedInUser())
    every { syncPreferences.state } returns MutableStateFlow(
      SyncPrefs(
        cloudSyncEnabled = true
      )
    )
    coEvery { prefsManager.update(any()) } returns Result.success(Unit)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun signedInUser(anonymous: Boolean = false): FirebaseUser =
    mockk<FirebaseUser> { every { isAnonymous } returns anonymous }

  /**
   * [NotificationSettingsViewModel.uiState] is `stateIn(WhileSubscribed(...))` — deliberately, per
   * design §9.2, so its seed default is the real "before combine has emitted" case. That means
   * nothing updates `.value` until something subscribes, unlike an eagerly-collected StateFlow;
   * `backgroundScope` is that subscriber for the life of the test, cancelled automatically after.
   */
  private fun TestScope.viewModel(): NotificationSettingsViewModel {
    val vm = NotificationSettingsViewModel(
      prefsManager,
      permission,
      auth,
      syncPreferences
    )
    backgroundScope.launch { vm.uiState.collect {} }
    return vm
  }

  // --- isLoading disables toggles (design §9.2) ---

  @Test
  fun uiState_defaultsToLoading_beforeAnyEmission() {
    // The stateIn seed, unmodified by a real combine emission yet, is exactly the #451 case.
    assertThat(NotificationSettingsUiState().isLoading).isTrue()
  }

  @Test
  fun uiState_prefsUnresolved_isLoadingTrue() = runTest(testDispatcher) {
    every { prefsManager.observe() } returns flowOf(PrefsState.Unresolved)
    val viewModel = viewModel()

    assertThat(viewModel.uiState.value.isLoading).isTrue()
  }

  @Test
  fun uiState_prefsResolved_isLoadingFalse() = runTest(testDispatcher) {
    val viewModel = viewModel()

    assertThat(viewModel.uiState.value.isLoading).isFalse()
  }

  // --- State-table fields (design §9.3) ---

  @Test
  fun uiState_deniedPermission_reflectedInState() = runTest(testDispatcher) {
    every { permission.observe() } returns MutableStateFlow(PermissionState.DENIED)
    val viewModel = viewModel()

    assertThat(viewModel.uiState.value.permission).isEqualTo(PermissionState.DENIED)
  }

  @Test
  fun uiState_unsupportedPermission_dropsOpenSettingsAffordance() =
    runTest(testDispatcher) {
      every { permission.observe() } returns MutableStateFlow(PermissionState.UNSUPPORTED)
      every { permission.canOpenSystemSettings } returns false
      val viewModel = viewModel()

      assertThat(viewModel.uiState.value.permission).isEqualTo(PermissionState.UNSUPPORTED)
      assertThat(viewModel.uiState.value.canOpenSystemSettings).isFalse()
    }

  @Test
  fun uiState_anonymousUser_isSignedInFalse() = runTest(testDispatcher) {
    every { auth.authStateChanged } returns flowOf(signedInUser(anonymous = true))
    val viewModel = viewModel()

    assertThat(viewModel.uiState.value.isSignedIn).isFalse()
  }

  @Test
  fun uiState_signedOut_isSignedInFalse() = runTest(testDispatcher) {
    every { auth.authStateChanged } returns flowOf(null)
    val viewModel = viewModel()

    assertThat(viewModel.uiState.value.isSignedIn).isFalse()
  }

  @Test
  fun uiState_realAccount_isSignedInTrue() = runTest(testDispatcher) {
    val viewModel = viewModel()

    assertThat(viewModel.uiState.value.isSignedIn).isTrue()
  }

  @Test
  fun uiState_cloudSyncOff_reflectedInState() = runTest(testDispatcher) {
    every { syncPreferences.state } returns MutableStateFlow(
      SyncPrefs(
        cloudSyncEnabled = false
      )
    )
    val viewModel = viewModel()

    assertThat(viewModel.uiState.value.isCloudSyncEnabled).isFalse()
  }

  // --- Master switch (§9.3's "flipping the master on triggers the OS prompt inline") ---

  @Test
  fun onAllNotificationsToggled_on_whileUndetermined_requestsPermission() =
    runTest(testDispatcher) {
      every { permission.observe() } returns MutableStateFlow(PermissionState.UNDETERMINED)
      coEvery { permission.request() } returns PermissionState.GRANTED
      val viewModel = viewModel()

      viewModel.onAllNotificationsToggled(true)

      coVerify { permission.request() }
      val mutate = slot<(NotificationSettings) -> NotificationSettings>()
      coVerify { prefsManager.update(capture(mutate)) }
      assertThat(mutate.captured(NotificationSettings()).allEnabled).isTrue()
    }

  @Test
  fun onAllNotificationsToggled_on_whileGranted_doesNotReRequest() =
    runTest(testDispatcher) {
      val viewModel = viewModel()

      viewModel.onAllNotificationsToggled(true)

      coVerify(exactly = 0) { permission.request() }
    }

  @Test
  fun onAllNotificationsToggled_off_writesWithoutRequestingPermission() =
    runTest(testDispatcher) {
      val viewModel = viewModel()

      viewModel.onAllNotificationsToggled(false)

      coVerify(exactly = 0) { permission.request() }
      val mutate = slot<(NotificationSettings) -> NotificationSettings>()
      coVerify { prefsManager.update(capture(mutate)) }
      assertThat(mutate.captured(NotificationSettings()).allEnabled).isFalse()
    }

  // --- Every other toggle writes through its positive-name mutator, never a raw NotificationSettings ---

  @Test
  fun onSquawkPriorityToggled_writesThroughTheMutator() =
    runTest(testDispatcher) {
      val viewModel = viewModel()

      viewModel.onSquawkPriorityToggled(false)

      val mutate = slot<(NotificationSettings) -> NotificationSettings>()
      coVerify { prefsManager.update(capture(mutate)) }
      assertThat(mutate.captured(NotificationSettings()).squawk_priority_disabled).isTrue()
    }

  @Test
  fun onAircraftActivityToggled_writesThroughTheMutator() =
    runTest(testDispatcher) {
      val viewModel = viewModel()

      viewModel.onAircraftActivityToggled(false)

      val mutate = slot<(NotificationSettings) -> NotificationSettings>()
      coVerify { prefsManager.update(capture(mutate)) }
      assertThat(mutate.captured(NotificationSettings()).aircraft_activity_disabled).isTrue()
    }

  // --- A failed write surfaces saveError instead of failing silently ---

  @Test
  fun onSquawkPriorityToggled_writeFails_setsSaveError() =
    runTest(testDispatcher) {
      coEvery { prefsManager.update(any()) } returns Result.failure(
        RuntimeException("boom")
      )
      val viewModel = viewModel()

      viewModel.onSquawkPriorityToggled(false)

      assertThat(viewModel.uiState.value.saveError).isTrue()
    }

  @Test
  fun onSquawkPriorityToggled_writeSucceeds_saveErrorStaysFalse() =
    runTest(testDispatcher) {
      val viewModel = viewModel()

      viewModel.onSquawkPriorityToggled(false)

      assertThat(viewModel.uiState.value.saveError).isFalse()
    }

  @Test
  fun onSaveErrorShown_clearsTheSignal() = runTest(testDispatcher) {
    coEvery { prefsManager.update(any()) } returns Result.failure(
      RuntimeException("boom")
    )
    val viewModel = viewModel()
    viewModel.onSquawkPriorityToggled(false)

    viewModel.onSaveErrorShown()

    assertThat(viewModel.uiState.value.saveError).isFalse()
  }
}
