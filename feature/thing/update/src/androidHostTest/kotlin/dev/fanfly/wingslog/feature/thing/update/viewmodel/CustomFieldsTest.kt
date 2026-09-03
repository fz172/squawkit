package dev.fanfly.wingslog.feature.thing.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.customSpecs
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
 * Fields the user names themselves, on the preset that has nothing else.
 *
 * `custom` declared no spec fields at all, so its create form was empty and every Thing made from
 * it stored no name and read as "Custom" everywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomFieldsTest {

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
  fun theCustomFormAsksForAName() {
    // The empty form is the bug. One required field, and it is what the Thing is called.
    val vm = viewModel("custom")
    val fields = vm.uiState.value.template?.spec_fields.orEmpty()

    assertThat(fields.map { it.key }).containsExactly("name")
    assertThat(fields.single().required).isTrue()
    assertThat(fields.single().title_candidate).isTrue()
    assertThat(vm.uiState.value.isValid).isFalse()

    vm.onSpecChanged("name", "espresso machine")

    assertThat(vm.uiState.value.isValid).isTrue()
  }

  @Test
  fun aUserNamedFieldCarriesItsOwnLabel() {
    val vm = viewModel("custom")

    vm.onAddCustomField()
    val key = vm.uiState.value.thing.customSpecs()
      .single().key
    vm.onCustomFieldChanged(key, "water hardness", "7 grains")

    val field = vm.uiState.value.thing.customSpecs()
      .single()
    assertThat(field.key).isEqualTo("custom_1")
    // Sentence case on both halves — the label is user input too.
    assertThat(field.label).isEqualTo("Water hardness")
    assertThat(field.value_).isEqualTo("7 grains")
  }

  @Test
  fun theTemplateSaysHowManyThereMayBe() {
    val vm = viewModel("custom")

    repeat(5) { vm.onAddCustomField() }

    assertThat(vm.uiState.value.thing.customSpecs()).hasSize(3)
    assertThat(vm.uiState.value.template?.custom_spec_fields).isEqualTo(3)
  }

  @Test
  fun aPresetThatOffersNoneGetsNone() {
    // Every preset describing a real domain declares its fields; only `custom` cannot.
    val vm = viewModel("airplane")

    vm.onAddCustomField()

    assertThat(vm.uiState.value.thing.customSpecs()).isEmpty()
  }

  @Test
  fun removingOneFreesItsSlot() {
    // The key is positional and permanent, so a freed slot is reused rather than pushing the
    // next field past the limit.
    val vm = viewModel("custom")
    repeat(3) { vm.onAddCustomField() }
    vm.onRemoveCustomField("custom_2")

    vm.onAddCustomField()

    assertThat(vm.uiState.value.thing.customSpecs().map { it.key })
      .containsExactly("custom_1", "custom_2", "custom_3")
  }

  @Test
  fun bothHalvesAreCappedAtFiftyCharacters() {
    val vm = viewModel("custom")
    vm.onAddCustomField()

    vm.onCustomFieldChanged("custom_1", "x".repeat(80), "y".repeat(80))

    val field = vm.uiState.value.thing.customSpecs()
      .single()
    assertThat(field.label).hasLength(50)
    assertThat(field.value_).hasLength(50)
  }
}
