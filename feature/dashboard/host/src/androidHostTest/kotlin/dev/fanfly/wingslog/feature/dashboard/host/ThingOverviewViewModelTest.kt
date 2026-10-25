package dev.fanfly.wingslog.feature.dashboard.host

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.CurrentReading
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewEvent
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.thing.Thing
import dev.gitlive.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import wingslog.core.sharedassets.generated.resources.save_failed
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

private const val THING_ID = "thing-1"

@OptIn(ExperimentalCoroutinesApi::class)
class ThingOverviewViewModelTest {

  private val fleetManager: FleetManager = mockk()
  private val logManager: MaintenanceLogManager = mockk()
  private val taskStatusManager: TaskStatusManager = mockk()
  private val sharingManager: SharingManager = mockk()
  private val auth: FirebaseAuth = mockk()

  private val thing = Thing(id = THING_ID, template = AirplaneTemplate.TEMPLATE)

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    every { fleetManager.loadThing(THING_ID) } returns flowOf(thing)
    every { logManager.observeLogs(THING_ID) } returns flowOf(emptyList())
    every { logManager.observeCurrentReadings(THING_ID) } returns flowOf(
      emptyList()
    )
    every { taskStatusManager.observeTasksWithStatus(THING_ID) } returns
      flowOf(emptyList())
    every { sharingManager.observeMyRole(THING_ID) } returns flowOf(ShareRole.OWNER)
    every { sharingManager.observeIsShared(THING_ID) } returns flowOf(false)
    every { auth.currentUser } returns null
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = ThingOverviewViewModel(
    fleetManager,
    logManager,
    taskStatusManager,
    sharingManager,
    BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE),
    auth,
    THING_ID,
  )

  private val ThingOverviewViewModel.success: ThingOverviewUiState.Success
    get() = uiState.value as ThingOverviewUiState.Success

  @Test
  fun meters_showTheCurrentReadingsTheTemplateDeclares_asOfTheNewest() = runTest {
    every { logManager.observeCurrentReadings(THING_ID) } returns flowOf(
      listOf(
        CurrentReading(
          MeterKeys.AIRFRAME_HOURS,
          2100.0,
          asOf = LocalDate(2026, 4, 1),
          isManual = false
        ),
        // Set by hand after the last log, so it is what dates the row.
        CurrentReading(
          MeterKeys.ENGINE_HOURS,
          1200.0,
          asOf = LocalDate(2026, 5, 3),
          isManual = true
        ),
        // A meter the airplane template does not declare: kept in storage, not shown.
        CurrentReading(
          "odometer",
          84512.0,
          asOf = LocalDate(2026, 6, 1),
          isManual = false
        ),
      )
    )

    val stats = viewModel().success.logStats!!

    assertThat(stats.valueFor(MeterKeys.AIRFRAME_HOURS)).isEqualTo(2100.0)
    assertThat(stats.valueFor(MeterKeys.ENGINE_HOURS)).isEqualTo(1200.0)
    assertThat(stats.valueFor("odometer")).isNull()
    assertThat(stats.readingsAsOf).isEqualTo(LocalDate(2026, 5, 3))
  }

  @Test
  fun setMeterReading_storesItThroughTheLogManager() = runTest {
    coEvery {
      logManager.setManualReading(THING_ID, MeterKeys.ENGINE_HOURS, 1200.0)
    } returns Result.success(true)

    viewModel().onAction(
      ThingOverviewAction.SetMeterReading(MeterKeys.ENGINE_HOURS, 1200.0)
    )

    coVerify(exactly = 1) {
      logManager.setManualReading(THING_ID, MeterKeys.ENGINE_HOURS, 1200.0)
    }
  }

  @Test
  fun setMeterReadingFailure_reportsIt() = runTest {
    coEvery {
      logManager.setManualReading(THING_ID, MeterKeys.ENGINE_HOURS, 1200.0)
    } returns Result.failure(IllegalStateException("disk full"))
    val vm = viewModel()

    vm.onAction(
      ThingOverviewAction.SetMeterReading(MeterKeys.ENGINE_HOURS, 1200.0)
    )

    assertThat(vm.events.first())
      .isEqualTo(ThingOverviewEvent.ShowMessage(UiText.StringRes(CoreRes.string.save_failed)))
  }
}
