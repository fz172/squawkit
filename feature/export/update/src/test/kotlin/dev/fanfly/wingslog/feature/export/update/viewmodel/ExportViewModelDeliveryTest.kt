package dev.fanfly.wingslog.feature.export.update.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryEmailSource
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelDeliveryTest {

  private val dispatcher = StandardTestDispatcher()

  private val exportManager: ExportManager = mockk()
  private val logsManager: MaintenanceLogManager = mockk()
  private val taskDataManager: TaskDataManager = mockk()
  private val squawkManager: SquawkManager = mockk()

  private lateinit var fleetManager: FleetManager
  private lateinit var subscriptionManager: SubscriptionManager
  private lateinit var auth: FirebaseAuth
  private val authState = MutableStateFlow<FirebaseUser?>(null)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    // Empty fleet keeps the aircraft observation path trivial; delivery resolution is the subject.
    fleetManager = mockk {
      every { observeFleetDashboard() } returns flowOf(emptyList())
    }
    auth = mockk()
    every { auth.authStateChanged } returns authState
    subscriptionManager = mockk()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun user(anonymous: Boolean, email: String?): FirebaseUser = mockk {
    every { isAnonymous } returns anonymous
    every { this@mockk.email } returns email
  }

  private fun buildViewModel(canEmail: Boolean): ExportViewModel {
    every { subscriptionManager.canEmailExports() } returns flowOf(canEmail)
    return ExportViewModel(
      exportManager = exportManager,
      fleetManager = fleetManager,
      logsManager = logsManager,
      taskDataManager = taskDataManager,
      squawkManager = squawkManager,
      subscriptionManager = subscriptionManager,
      auth = auth,
    )
  }

  private fun configuring(vm: ExportViewModel) =
    vm.state.value as ExportUiState.Configuring

  @Test
  fun `granted email export resolves delivery info and is not locked`() = runTest(dispatcher) {
    authState.value = user(anonymous = false, email = "pilot@example.com")
    val vm = buildViewModel(canEmail = true)
    advanceUntilIdle()

    val state = configuring(vm)
    assertThat(state.resolvedDeliveryInfo?.destinationEmail).isEqualTo("pilot@example.com")
    assertThat(state.resolvedDeliveryInfo?.source).isEqualTo(ExportDeliveryEmailSource.AUTH_FALLBACK)
    assertThat(state.emailDeliveryLocked).isFalse()
  }

  @Test
  fun `eligible user with gate off surfaces shown-locked email option`() = runTest(dispatcher) {
    authState.value = user(anonymous = false, email = "pilot@example.com")
    val vm = buildViewModel(canEmail = false)
    advanceUntilIdle()

    val state = configuring(vm)
    assertThat(state.resolvedDeliveryInfo).isNull()
    assertThat(state.emailDeliveryLocked).isTrue()
  }

  @Test
  fun `guest user is local-only and never locked`() = runTest(dispatcher) {
    authState.value = user(anonymous = true, email = null)
    val vm = buildViewModel(canEmail = false)
    advanceUntilIdle()

    val state = configuring(vm)
    assertThat(state.resolvedDeliveryInfo).isNull()
    assertThat(state.emailDeliveryLocked).isFalse()
  }

  @Test
  fun `signed-in user without an email is not locked`() = runTest(dispatcher) {
    authState.value = user(anonymous = false, email = "")
    val vm = buildViewModel(canEmail = false)
    advanceUntilIdle()

    val state = configuring(vm)
    assertThat(state.resolvedDeliveryInfo).isNull()
    assertThat(state.emailDeliveryLocked).isFalse()
  }
}
