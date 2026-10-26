package dev.fanfly.wingslog.feature.dashboard.host

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewEvent
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.id.ThingId
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
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val THING_ID = "thing-1"

@OptIn(ExperimentalCoroutinesApi::class)
class ThingOverviewViewModelTest {

  private val fleetManager: FleetManager = mockk()
  private val sharingManager: SharingManager = mockk()
  private val auth: FirebaseAuth = mockk()

  private val thing = Thing(id = THING_ID, template = AirplaneTemplate.TEMPLATE)

  private val thingId = ThingId(THING_ID)

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    every { fleetManager.loadThing(thingId) } returns flowOf(thing)
    every { sharingManager.observeMyRole(THING_ID) } returns flowOf(ShareRole.OWNER)
    every { sharingManager.observeIsShared(THING_ID) } returns flowOf(false)
    every { auth.currentUser } returns null
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = ThingOverviewViewModel(
    fleetManager,
    sharingManager,
    BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE),
    auth,
    thingId,
  )

  @Test
  fun loadsThingAndSharingRole() = runTest {
    val state = viewModel().uiState.value as ThingOverviewUiState.Success

    assertThat(state.thing).isEqualTo(thing)
    assertThat(state.myRole).isEqualTo(ShareRole.OWNER)
    assertThat(state.shared).isFalse()
    assertThat(state.isAnonymous).isTrue()
  }

  @Test
  fun deleteConfirm_deletesThingAndNavigatesBack() = runTest {
    coEvery { fleetManager.deleteThing(thingId) } returns Result.success(true)
    val vm = viewModel()

    vm.onAction(ThingOverviewAction.DeleteConfirm)

    coVerify(exactly = 1) { fleetManager.deleteThing(thingId) }
    assertThat(vm.events.first()).isEqualTo(ThingOverviewEvent.NavigateBack)
  }
}
