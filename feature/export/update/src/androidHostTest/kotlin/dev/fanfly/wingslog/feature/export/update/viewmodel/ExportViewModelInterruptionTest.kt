package dev.fanfly.wingslog.feature.export.update.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.NoOpAnalyticsManager
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.datamanager.ExportRunPolicy
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * `ExportRunPolicy.stopWhenBackgrounded` (#343): leaving the foreground abandons an in-flight
 * export on platforms that cannot keep it alive, and the user restarts from the same setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelInterruptionTest {

  private val dispatcher = StandardTestDispatcher()

  private val thing = Thing(
    id = "thing-1",
    spec = listOf(Spec(key = "tail_number", value_ = "N12345")),
  )
  private val exportManager: ExportManager = mockk()
  private var exportStarts = 0
  private var exportCancelled = false

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    // A long-running export: emits one progress step, then sits until cancelled.
    every { exportManager.exportLogs(any()) } answers {
      flow {
        exportStarts++
        emit(ExportProgress.Running(ExportProgressStep.COLLECTING_DATA, 8))
        try {
          awaitCancellation()
        } finally {
          exportCancelled = true
        }
      }
    }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun buildViewModel(stopWhenBackgrounded: Boolean): ExportViewModel {
    val user: FirebaseUser = mockk {
      every { isAnonymous } returns true
      every { email } returns null
    }
    val auth: FirebaseAuth = mockk {
      every { authStateChanged } returns MutableStateFlow(user)
    }
    return ExportViewModel(
      exportManager = exportManager,
      fleetManager = mockk<FleetManager> {
        every { observeFleetDashboard() } returns flowOf(
          listOf(FleetEntry(thing = thing, shared = false, role = ShareRole.SHARE_ROLE_OWNER))
        )
      },
      logsManager = mockk<MaintenanceLogManager> {
        every { observeLogs(thing.id) } returns flowOf(emptyList())
      },
      taskDataManager = mockk<TaskDataManager> {
        every { observeTasks(thing.id) } returns flowOf(emptyList())
      },
      squawkManager = mockk<SquawkManager> {
        every { observeSquawks(thing.id) } returns flowOf(emptyList())
      },
      subscriptionManager = mockk<SubscriptionManager> {
        every { canEmailExports() } returns flowOf(false)
      },
      auth = auth,
      currentThingTemplate = mockk<CurrentThingTemplate>(relaxed = true),
      templateRegistry = BakedInTemplateRegistry(appVersionCode = Int.MAX_VALUE),
      analytics = NoOpAnalyticsManager,
      runPolicy = ExportRunPolicy(stopWhenBackgrounded = stopWhenBackgrounded),
    )
  }

  private fun startExport(vm: ExportViewModel) {
    advanceUntilIdleOnMain()
    vm.onExport()
    advanceUntilIdleOnMain()
    assertThat(vm.state.value).isInstanceOf(ExportUiState.Running::class.java)
  }

  private fun advanceUntilIdleOnMain() = dispatcher.scheduler.advanceUntilIdle()

  @Test
  fun `backgrounding under a stop policy cancels the export and shows Interrupted`() =
    runTest(dispatcher) {
      val vm = buildViewModel(stopWhenBackgrounded = true)
      startExport(vm)

      vm.onAppBackgrounded()
      advanceUntilIdle()

      assertThat(vm.state.value).isEqualTo(ExportUiState.Interrupted)
      assertThat(exportCancelled).isTrue()
    }

  @Test
  fun `backgrounding under a keep-running policy leaves the export alone`() =
    runTest(dispatcher) {
      val vm = buildViewModel(stopWhenBackgrounded = false)
      startExport(vm)

      vm.onAppBackgrounded()
      advanceUntilIdle()

      assertThat(vm.state.value).isInstanceOf(ExportUiState.Running::class.java)
      assertThat(exportCancelled).isFalse()
    }

  @Test
  fun `backgrounding while configuring is a no-op`() = runTest(dispatcher) {
    val vm = buildViewModel(stopWhenBackgrounded = true)
    advanceUntilIdle()
    val before = vm.state.value
    assertThat(before).isInstanceOf(ExportUiState.Configuring::class.java)

    vm.onAppBackgrounded()
    advanceUntilIdle()

    assertThat(vm.state.value).isEqualTo(before)
    assertThat(exportStarts).isEqualTo(0)
  }

  @Test
  fun `restart re-runs the export from the same setup`() = runTest(dispatcher) {
    val vm = buildViewModel(stopWhenBackgrounded = true)
    startExport(vm)
    vm.onAppBackgrounded()
    advanceUntilIdle()
    assertThat(vm.state.value).isEqualTo(ExportUiState.Interrupted)

    vm.onRestart()
    advanceUntilIdle()

    assertThat(vm.state.value).isInstanceOf(ExportUiState.Running::class.java)
    assertThat(exportStarts).isEqualTo(2)
  }

  @Test
  fun `back to setup from Interrupted restores the configuration`() = runTest(dispatcher) {
    val vm = buildViewModel(stopWhenBackgrounded = true)
    startExport(vm)
    vm.onAppBackgrounded()
    advanceUntilIdle()

    vm.onRetry()

    val configuring = vm.state.value as ExportUiState.Configuring
    assertThat(configuring.selectedThingIds).containsExactly(thing.id)
  }
}
