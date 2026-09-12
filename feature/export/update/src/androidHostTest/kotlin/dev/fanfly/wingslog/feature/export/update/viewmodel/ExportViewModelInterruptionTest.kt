package dev.fanfly.wingslog.feature.export.update.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.NoOpAnalyticsManager
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
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

/**
 * The ViewModel against `ExportJobCoordinator` and `ExportRunPolicy` (#343): leaving the
 * foreground abandons an in-flight export on platforms that cannot keep it alive, the user restarts
 * from the same setup, and a job that outlives the screen is picked up on the way back in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelInterruptionTest {

  private val dispatcher = StandardTestDispatcher()

  private val thing = Thing(
    id = "thing-1",
    spec = listOf(Spec(key = "tail_number", value_ = "N12345")),
  )
  private val coordinator = FakeExportJobCoordinator()

  @Before
  fun setUp() = Dispatchers.setMain(dispatcher)

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun buildViewModel(
    stopWhenBackgrounded: Boolean,
    survivesLeavingScreen: Boolean = false,
  ): ExportViewModel {
    val user: FirebaseUser = mockk {
      every { isAnonymous } returns true
      every { email } returns null
    }
    val auth: FirebaseAuth = mockk {
      every { authStateChanged } returns MutableStateFlow(user)
    }
    return ExportViewModel(
      exportManager = mockk<ExportManager>(relaxed = true),
      jobCoordinator = coordinator,
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
      runPolicy = ExportRunPolicy(
        stopWhenBackgrounded = stopWhenBackgrounded,
        survivesLeavingScreen = survivesLeavingScreen,
      ),
    )
  }

  private fun startExport(vm: ExportViewModel) {
    dispatcher.scheduler.advanceUntilIdle()
    vm.onExport()
    dispatcher.scheduler.advanceUntilIdle()
    assertThat(vm.state.value).isInstanceOf(ExportUiState.Running::class.java)
  }

  @Test
  fun `backgrounding under a stop policy cancels the job and shows Interrupted`() =
    runTest(dispatcher) {
      val vm = buildViewModel(stopWhenBackgrounded = true)
      startExport(vm)

      vm.onAppBackgrounded()
      advanceUntilIdle()

      assertThat(vm.state.value).isEqualTo(ExportUiState.Interrupted)
      assertThat(coordinator.cancels).isEqualTo(1)
      assertThat(coordinator.job.value).isNull()
    }

  @Test
  fun `backgrounding under a keep-running policy leaves the job alone`() =
    runTest(dispatcher) {
      val vm = buildViewModel(stopWhenBackgrounded = false)
      startExport(vm)

      vm.onAppBackgrounded()
      advanceUntilIdle()

      assertThat(vm.state.value).isInstanceOf(ExportUiState.Running::class.java)
      assertThat(coordinator.cancels).isEqualTo(0)
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
    assertThat(coordinator.starts).isEqualTo(0)
    assertThat(coordinator.cancels).isEqualTo(0)
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
    assertThat(coordinator.starts).isEqualTo(2)
    assertThat(coordinator.job.value?.request?.thingIds).containsExactly(thing.id)
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

  @Test
  fun `progress and success arrive from the coordinator with the job's own scope`() =
    runTest(dispatcher) {
      val vm = buildViewModel(stopWhenBackgrounded = false)
      startExport(vm)

      coordinator.emit(ExportProgress.Running(ExportProgressStep.SAVING_FILE, 74))
      advanceUntilIdle()
      assertThat(vm.state.value).isEqualTo(
        ExportUiState.Running(ExportProgressStep.SAVING_FILE, 74)
      )

      coordinator.emit(success())
      advanceUntilIdle()
      val shown = vm.state.value as ExportUiState.Success
      assertThat(shown.exportId).isEqualTo("exp-1")
      assertThat(shown.selectedTailNumbers).containsExactly("N12345")
      assertThat(shown.dateRange).isEqualTo(DateRangeOption.AllTime)
    }

  @Test
  fun `a screen opened after the job finished shows the result`() = runTest(dispatcher) {
    // Simulates Android: the worker ran to completion while no screen was attached.
    coordinator.start(
      dev.fanfly.wingslog.feature.export.datamanager.ExportRequest(
        thingIds = listOf(thing.id),
        dateRange = dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange.AllTime,
        includeOpenSquawks = true,
      )
    )
    coordinator.emit(success())

    val vm = buildViewModel(stopWhenBackgrounded = false, survivesLeavingScreen = true)
    advanceUntilIdle()

    val shown = vm.state.value as ExportUiState.Success
    assertThat(shown.exportId).isEqualTo("exp-1")
    // Filled in once the fleet rows arrived, even though the result was there first.
    assertThat(shown.selectedTailNumbers).containsExactly("N12345")
  }

  @Test
  fun `done clears a finished job and returns to setup`() = runTest(dispatcher) {
    val vm = buildViewModel(stopWhenBackgrounded = false)
    startExport(vm)
    coordinator.emit(success())
    advanceUntilIdle()

    vm.onDone()
    advanceUntilIdle()

    assertThat(vm.state.value).isInstanceOf(ExportUiState.Configuring::class.java)
    assertThat(coordinator.clears).isEqualTo(1)
    assertThat(coordinator.job.value).isNull()
  }

  @Test
  fun `a failure in the pipeline lands on the error screen`() = runTest(dispatcher) {
    val vm = buildViewModel(stopWhenBackgrounded = false)
    startExport(vm)

    coordinator.emit(ExportProgress.Error("disk full"))
    advanceUntilIdle()

    assertThat(vm.state.value).isEqualTo(ExportUiState.Error("disk full"))
  }

  private fun success() = ExportProgress.Success(
    exportId = "exp-1",
    filePath = "content://downloads/1",
    fileName = "SquawkIt_Logs_N12345_20260912.zip",
    displayLocation = "",
    sizeBytes = 1234L,
    displayLocationKind = ExportDisplayLocation.DOWNLOADS_SQUAWKIT,
  )
}
