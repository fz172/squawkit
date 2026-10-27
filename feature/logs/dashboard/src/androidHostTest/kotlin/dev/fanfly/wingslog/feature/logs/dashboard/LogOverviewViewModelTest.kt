package dev.fanfly.wingslog.feature.logs.dashboard

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.CurrentReading
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.Thing
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
private val THING = ThingId(THING_ID)

@OptIn(ExperimentalCoroutinesApi::class)
class LogOverviewViewModelTest {

  private val logManager: MaintenanceLogManager = mockk()
  private val thing = Thing(id = THING_ID, template = AirplaneTemplate.TEMPLATE)

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    every { logManager.observeLogs(THING) } returns flowOf(emptyList())
    every { logManager.observeCurrentReadings(THING) } returns flowOf(emptyList())
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = LogOverviewViewModel(
    logManager = logManager,
    templateRegistry = BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE),
    thing = thing,
  )

  @Test
  fun meters_showTheCurrentReadingsTheTemplateDeclares_asOfTheNewest() = runTest {
    every { logManager.observeCurrentReadings(THING) } returns flowOf(
      listOf(
        CurrentReading(
          MeterKeys.AIRFRAME_HOURS,
          2100.0,
          asOf = LocalDate(2026, 4, 1),
          isManual = false,
        ),
        // Set by hand after the last log, so it is what dates the row.
        CurrentReading(
          MeterKeys.ENGINE_HOURS,
          1200.0,
          asOf = LocalDate(2026, 5, 3),
          isManual = true,
        ),
        // A meter the airplane template does not declare: kept in storage, not shown.
        CurrentReading(
          "odometer",
          84512.0,
          asOf = LocalDate(2026, 6, 1),
          isManual = false,
        ),
      )
    )

    val stats = viewModel().uiState.value.logStats!!

    assertThat(stats.valueFor(MeterKeys.AIRFRAME_HOURS)).isEqualTo(2100.0)
    assertThat(stats.valueFor(MeterKeys.ENGINE_HOURS)).isEqualTo(1200.0)
    assertThat(stats.valueFor("odometer")).isNull()
    assertThat(stats.readingsAsOf).isEqualTo(LocalDate(2026, 5, 3))
  }

  @Test
  fun setMeterReading_storesItThroughTheLogManager() = runTest {
    coEvery {
      logManager.setManualReading(THING, MeterKeys.ENGINE_HOURS, 1200.0)
    } returns Result.success(true)

    viewModel().setMeterReading(MeterKeys.ENGINE_HOURS, 1200.0)

    coVerify(exactly = 1) {
      logManager.setManualReading(THING, MeterKeys.ENGINE_HOURS, 1200.0)
    }
  }

  @Test
  fun setMeterReadingFailure_reportsIt() = runTest {
    coEvery {
      logManager.setManualReading(THING, MeterKeys.ENGINE_HOURS, 1200.0)
    } returns Result.failure(IllegalStateException("disk full"))
    val vm = viewModel()

    vm.setMeterReading(MeterKeys.ENGINE_HOURS, 1200.0)

    assertThat(vm.events.first())
      .isEqualTo(UiText.StringRes(CoreRes.string.save_failed))
  }
}
