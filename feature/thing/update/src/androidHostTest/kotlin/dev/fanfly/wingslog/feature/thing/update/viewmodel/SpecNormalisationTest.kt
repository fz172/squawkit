package dev.fanfly.wingslog.feature.thing.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.ComponentPath
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.componentAt
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.thing.SpecField
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
 * That the form types a value the way the template says to, not the way an airplane does (#739).
 * The old rule was four conventional key names, so only the airplane's identifiers were covered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpecNormalisationTest {

  private val dispatcher = StandardTestDispatcher()
  private val fleetManager = mockk<FleetManager>()
  private val sharingManager = mockk<SharingManager>(relaxed = true)
  private val currentTemplate = mockk<CurrentThingTemplate>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { currentTemplate.templateId } returns "airplane"
    every { currentTemplate.capabilities } returns
      MutableStateFlow(CurrentThingTemplate.ALL_ENABLED)
    every { fleetManager.observeFleetDashboard() } returns emptyFlow()
    every { sharingManager.observeShareState(any()) } returns emptyFlow()
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel(templateId: String) = EditThingViewModel(
    fleetManager = fleetManager,
    sharingManager = sharingManager,
    currentThingTemplate = currentTemplate,
    templateRegistry = BakedInTemplateRegistry(appVersionCode = Int.MAX_VALUE),
    analytics = RecordingAnalyticsManager(),
    savedStateHandle = SavedStateHandle(mapOf(Screen.TEMPLATE_ID to templateId)),
  )

  @Test
  fun everyPresetsOwnIdentifierIsUpperCased() {
    // Each preset's own identifier, whatever it is called.
    val vm = viewModel("automotive")
    vm.onSpecChanged("vin", "1hgbh41jxmn109186")
    assertThat(vm.uiState.value.thing.specValue("vin"))
      .isEqualTo("1HGBH41JXMN109186")

    val boat = viewModel("boat")
    boat.onSpecChanged("hull_id", "abc12345d616")
    assertThat(boat.uiState.value.thing.specValue("hull_id"))
      .isEqualTo("ABC12345D616")

    val bike = viewModel("bike")
    bike.onSpecChanged("frame_number", "wtu123k0001z")
    assertThat(bike.uiState.value.thing.specValue("frame_number"))
      .isEqualTo("WTU123K0001Z")
  }

  @Test
  fun theAirplaneKeepsTheCasingItAlwaysHad() {
    // The rule changed shape, not behaviour, for the preset it was written around.
    val vm = viewModel("airplane")

    vm.onSpecChanged("tail_number", "n172fz")
    vm.onSpecChanged("serial", "17280512")
    vm.onSpecChanged("make", "cessna")
    vm.onSpecChanged("model", "skyhawk")

    val thing = vm.uiState.value.thing
    assertThat(thing.specValue("tail_number")).isEqualTo("N172FZ")
    assertThat(thing.specValue("serial")).isEqualTo("17280512")
    assertThat(thing.specValue("make")).isEqualTo("Cessna")
    assertThat(thing.specValue("model")).isEqualTo("Skyhawk")
  }

  @Test
  fun aKeyTheTemplateDoesNotDeclareIsLeftAlone() {
    // A field from a newer build: storing what was typed beats guessing at its casing.
    val vm = viewModel("home")
    vm.onSpecChanged("hoa_contact", "de la cruz")

    assertThat(vm.uiState.value.thing.specValue("hoa_contact"))
      .isEqualTo("de la cruz")
  }

  @Test
  fun aComponentsFieldsFollowTheSameRule() {
    // Same message, same flags — the two forms cannot disagree about what `numeric` means.
    val vm = viewModel("bike")
    val path: ComponentPath = listOf("drivetrain" to 0)

    vm.onComponentSpecChanged(
      path,
      SpecField(key = "pressure", numeric = true),
      "32.5"
    )
    vm.onComponentSpecChanged(
      path,
      SpecField(key = "part_number", is_identifier = true),
      "sram-gx-1275",
    )
    vm.onComponentSpecChanged(path, SpecField(key = "position"), "rear")

    val component = vm.uiState.value.thing.componentAt(path)
    assertThat(component?.specValue("pressure")).isEqualTo("32.5")
    assertThat(component?.specValue("part_number")).isEqualTo("SRAM-GX-1275")
    assertThat(component?.specValue("position")).isEqualTo("Rear")
  }
}
