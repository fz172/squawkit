package dev.fanfly.wingslog.feature.thing.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * That the type chosen in the picker is the one the create form is built from (#738).
 *
 * The form used to describe itself from `LocalThingLexicon`, which is provided app-wide from the
 * *selected* Thing — so every create form read "Add Aircraft" and laid out airplane spec fields
 * whatever the picker had been told. Nothing failed; the form simply ignored the choice, which is
 * the whole point of having a picker. These assert the state the composition reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PickedTemplateSeedsTheFormTest {

  private val dispatcher = StandardTestDispatcher()
  private val fleetManager = mockk<FleetManager>()
  private val sharingManager = mockk<SharingManager>(relaxed = true)
  private val currentTemplate = mockk<CurrentThingTemplate>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    // The ambient selection is an airplane throughout — that is exactly what used to leak in.
    every { currentTemplate.templateId } returns "airplane"
    every { currentTemplate.capabilities } returns
      MutableStateFlow(CurrentThingTemplate.ALL_ENABLED)
    every { fleetManager.observeFleetDashboard() } returns emptyFlow()
    every { sharingManager.observeShareState(any()) } returns emptyFlow()
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel(templateId: String?) = EditThingViewModel(
    fleetManager = fleetManager,
    sharingManager = sharingManager,
    currentThingTemplate = currentTemplate,
    templateRegistry = BakedInTemplateRegistry(appVersionCode = Int.MAX_VALUE),
    analytics = RecordingAnalyticsManager(),
    savedStateHandle = SavedStateHandle(
      if (templateId == null) emptyMap() else mapOf(Screen.TEMPLATE_ID to templateId),
    ),
  )

  @Test
  fun theFormIsBuiltFromThePickedType() {
    val state = viewModel("automotive").uiState.value

    assertThat(state.template?.id).isEqualTo("automotive")
    // The words too, not only the fields: the title is rendered from this.
    assertThat(state.lexicon.thing?.singular).isNotEqualTo("aircraft")
    assertThat(state.thing.template?.id).isEqualTo("automotive")
  }

  @Test
  fun eachPresetSeedsItsOwnSpecFields() {
    // A car is not asked for a tail number and an airplane is not asked for a VIN. Comparing the
    // sets rather than naming keys keeps this honest as the presets are edited.
    val car = viewModel("automotive").uiState.value.template?.spec_fields?.map { it.key }
    val home = viewModel("home").uiState.value.template?.spec_fields?.map { it.key }

    assertThat(car).isNotEmpty()
    assertThat(home).isNotEmpty()
    assertThat(car).isNotEqualTo(home)
  }

  @Test
  fun noPickedTypeStillOpensTheForm() {
    // The empty-fleet path and every edit arrive with no argument. Refusing to open would be a
    // worse failure than falling back the way creation always did.
    val state = viewModel(null).uiState.value

    assertThat(state.template).isNotNull()
  }

  @Test
  fun anIdThisBuildDoesNotCarryFallsBack() {
    // A template published after this build ships, named on a route we somehow received.
    val state = viewModel("submarine").uiState.value

    assertThat(state.template).isNotNull()
    assertThat(state.thing.template).isNull()
  }
}
