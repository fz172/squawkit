package dev.fanfly.wingslog.feature.export.update.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.NoOpAnalyticsManager
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.ThingInflater
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
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
 * That the export list names each Thing in its own terms.
 *
 * Every row used to read "Untitled aircraft" / "Aircraft details incomplete" for anything that is
 * not an aeroplane: the row model carried `tailNumber` and `makeModel`, which a house has neither
 * of, and the screen filled the placeholders from `LocalThingLexicon` — the *selected* Thing's
 * words, which cannot describe a mixed list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportSelectionRowTest {

  private val dispatcher = StandardTestDispatcher()
  private val fleet = MutableStateFlow<List<FleetEntry>>(emptyList())

  @Before
  fun setUp() = Dispatchers.setMain(dispatcher)

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel(): ExportViewModel =
    ExportViewModel(
      exportManager = mockk(relaxed = true),
      fleetManager = mockk<FleetManager> { every { observeFleetDashboard() } returns fleet },
      logsManager = mockk<MaintenanceLogManager> {
        every { observeLogs(any()) } returns flowOf(emptyList())
      },
      taskDataManager = mockk<TaskDataManager> {
        every { observeTasks(any()) } returns flowOf(emptyList())
      },
      squawkManager = mockk<SquawkManager> {
        every { observeSquawks(any()) } returns flowOf(emptyList())
      },
      subscriptionManager = mockk<SubscriptionManager> {
        every { canEmailExports() } returns flowOf(false)
      },
      auth = mockk<FirebaseAuth> {
        every { authStateChanged } returns MutableStateFlow<FirebaseUser?>(null)
      },
      currentThingTemplate = mockk<CurrentThingTemplate>(relaxed = true),
      templateRegistry = BakedInTemplateRegistry(appVersionCode = Int.MAX_VALUE),
      analytics = NoOpAnalyticsManager,
    )

  private fun rows(vm: ExportViewModel): List<ThingSelectionRow> =
    (vm.state.value as ExportUiState.Configuring).things

  private fun entry(thing: Thing, template: dev.fanfly.wingslog.thing.ThingTemplate) =
    FleetEntry(
      thing = ThingInflater.inflate(thing, template),
      shared = false,
      role = ShareRole.SHARE_ROLE_OWNER,
    )

  @Test
  fun `a home is named by its address, not "Untitled aircraft"`() = runTest(dispatcher) {
    fleet.value = listOf(
      entry(
        Thing(id = "h1", spec = listOf(Spec(key = "address", value_ = "655 Disko Drive"))),
        CanonicalTemplates.HOME,
      )
    )
    val vm = viewModel()
    advanceUntilIdle()

    val row = rows(vm).single()
    assertThat(row.label).isEqualTo("655 Disko Drive")
    assertThat(row.label).doesNotContain("aircraft")
    assertThat(row.subtitle).doesNotContain("Aircraft")
  }

  @Test
  fun `a mixed fleet names each row from its own template`() = runTest(dispatcher) {
    // The reason one ambient lexicon cannot serve this screen: these two rows need different words
    // at the same moment.
    fleet.value = listOf(
      entry(
        Thing(
          id = "c1",
          spec = listOf(
            Spec(key = "make", value_ = "Honda"),
            Spec(key = "model", value_ = "Civic"),
          ),
        ),
        CanonicalTemplates.AUTOMOTIVE,
      ),
      entry(
        Thing(id = "h1", name = "Lake house"),
        CanonicalTemplates.HOME,
      ),
    )
    val vm = viewModel()
    advanceUntilIdle()

    assertThat(rows(vm).map { it.label }).containsExactly("Honda Civic", "Lake house")
  }

  @Test
  fun `a Thing with nothing filled in falls back to its type`() = runTest(dispatcher) {
    fleet.value = listOf(entry(Thing(id = "h1"), CanonicalTemplates.HOME))
    val vm = viewModel()
    advanceUntilIdle()

    assertThat(rows(vm).single().label).isEqualTo("Home")
  }
}
