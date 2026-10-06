package dev.fanfly.wingslog.feature.tasks.suggestions.update.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.nav.SuggestionsMode
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.FileTooLargeException
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.model.toDocumentsArg
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.update.review.DocumentProblem
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AddTasksViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val fleetManager = mockk<FleetManager>()
  private val suggestions = mockk<TaskSuggestionManager>(relaxUnitFun = true)
  private val attachments = mockk<AttachmentManager>(relaxUnitFun = true)
  private val entryFlow = MutableStateFlow<SuggestEntry>(SuggestEntry.Available)
  private val entry = mockk<TaskSuggestionEntry> {
    every { observe(THING_ID) } returns entryFlow
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { fleetManager.loadThing(THING_ID) } returns flowOf(
      Thing(
        id = THING_ID,
        template = ThingTemplate(id = "airplane", version = 1),
        spec = listOf(
          Spec(key = "make", value_ = "Sling"),
          Spec(key = "model", value_ = "4 TSi"),
        ),
      ),
    )
    coEvery { suggestions.isOwner(THING_ID) } returns true
    allowing(AiEligibility(true, null, true, null))
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun allowing(eligibility: AiEligibility) {
    coEvery { suggestions.eligibility(THING_ID, any()) } returns eligibility
  }

  private val store = ViewModelStore()

  private fun TestScope.viewModel(): AddTasksViewModel = AddTasksViewModel(
    fleetManager = fleetManager,
    templateRegistry = BakedInTemplateRegistry(appVersionCode = 1),
    suggestionManager = suggestions,
    suggestEntry = entry,
    attachmentManager = attachments,
    savedStateHandle = SavedStateHandle(mapOf(Screen.THING_ID to THING_ID)),
    cleanupScope = this,
  ).also { store.put("add", it) }

  private fun picked(name: String, mime: String = "application/pdf") =
    PickedFile(
      uri = "file://$name",
      name = name,
      mimeType = mime,
      sizeBytes = 1_000
    )

  private fun storing(vararg names: String) {
    names.forEach { name ->
      coEvery {
        attachments.addPickedFile(
          THING_ID,
          match { it.name == name },
          name,
          QuotaChecker.MAX_AI_DOCUMENT_BYTES
        )
      } returns Attachment(id = "blob-$name", name = name)
    }
  }

  @Test
  fun theSheetNamesTheDetailsItIsBasedOn() = runTest(dispatcher) {
    val vm = viewModel()
    advanceUntilIdle()

    assertThat(vm.uiState.value.details).isEqualTo("Sling 4 TSi")
  }

  @Test
  fun aProOwnerGetsTheManualsArea() = runTest(dispatcher) {
    val vm = viewModel()
    // Nothing in the slot is decided before the server answers.
    assertThat(vm.uiState.value.showsManuals).isFalse()
    assertThat(vm.uiState.value.showsPromo).isFalse()
    advanceUntilIdle()

    assertThat(vm.uiState.value.showsManuals).isTrue()
    assertThat(vm.uiState.value.showsPromo).isFalse()
    assertThat(vm.uiState.value.canSuggest).isTrue()
  }

  @Test
  fun aFreeOwnerGetsThePromoInTheSameSlot() = runTest(dispatcher) {
    allowing(AiEligibility(true, null, false, null))

    val vm = viewModel()
    advanceUntilIdle()

    assertThat(vm.uiState.value.showsManuals).isFalse()
    assertThat(vm.uiState.value.showsPromo).isTrue()
    // The promo never blocks the free suggestion.
    assertThat(vm.uiState.value.canSuggest).isTrue()
  }

  @Test
  fun aMemberOfAFreeOwnersThingGetsNeither() = runTest(dispatcher) {
    allowing(AiEligibility(true, null, false, null))
    coEvery { suggestions.isOwner(THING_ID) } returns false

    val vm = viewModel()
    advanceUntilIdle()

    assertThat(vm.uiState.value.showsManuals).isFalse()
    assertThat(vm.uiState.value.showsPromo).isFalse()
  }

  @Test
  fun aGuestIsNotAskedAboutAndSuggestSignsIn() = runTest(dispatcher) {
    entryFlow.value = SuggestEntry.SignInRequired

    val vm = viewModel()
    advanceUntilIdle()

    coVerify(exactly = 0) { suggestions.eligibility(any(), any()) }
    assertThat(vm.uiState.value.canSuggest).isTrue()
    assertThat(vm.uiState.value.showsPromo).isFalse()
    // The route signs in; there is no suggestions screen to open.
    assertThat(vm.onSuggest()).isNull()
  }

  @Test
  fun suggestHandsThePickedManualsToTheAddMode() = runTest(dispatcher) {
    storing("MM.pdf", "LM.pdf")
    val vm = viewModel()
    advanceUntilIdle()
    vm.onAddDocuments(listOf(picked("MM.pdf"), picked("LM.pdf")))
    advanceUntilIdle()

    val route = vm.onSuggest()

    val manuals = listOf(
      Attachment(id = "blob-MM.pdf", name = "MM.pdf"),
      Attachment(id = "blob-LM.pdf", name = "LM.pdf"),
    )
    assertThat(route).isEqualTo(
      Screen.Suggestions.createRoute(
        THING_ID,
        SuggestionsMode.ADD,
        manuals.toDocumentsArg()
      ),
    )
    // The run lets them go from now on, not the sheet.
    store.clear()
    advanceUntilIdle()
    coVerify(exactly = 0) { attachments.release(any(), any()) }
  }

  @Test
  fun suggestWithoutManualsIsTheAddModeAlone() = runTest(dispatcher) {
    val vm = viewModel()
    advanceUntilIdle()

    assertThat(vm.onSuggest()).isEqualTo("suggestions/$THING_ID?mode=add")
  }

  @Test
  fun atTheDailyLimitSuggestOpensTheCuratedListAndSaysWhen() =
    runTest(dispatcher) {
      val back = Instant.fromEpochMilliseconds(9_000)
      allowing(AiEligibility(false, AiErrorCode.DAILY_LIMIT, true, back))

      val vm = viewModel()
      advanceUntilIdle()

      assertThat(vm.uiState.value.sources.blocked).isEqualTo(AiErrorCode.DAILY_LIMIT)
      assertThat(vm.uiState.value.sources.availableAt).isEqualTo(back)
      assertThat(vm.uiState.value.showsManuals).isFalse()
      assertThat(vm.onSuggest()).isEqualTo("suggestions/$THING_ID")
    }

  @Test
  fun closingWithoutSuggestingLetsTheManualsGo() = runTest(dispatcher) {
    storing("MM.pdf")
    val vm = viewModel()
    advanceUntilIdle()
    vm.onAddDocuments(listOf(picked("MM.pdf")))
    advanceUntilIdle()

    store.clear()
    advanceUntilIdle()

    coVerify {
      attachments.release(
        Attachment(
          id = "blob-MM.pdf",
          name = "MM.pdf"
        ), null
      )
    }
  }

  @Test
  fun removingAManualLetsItGo() = runTest(dispatcher) {
    storing("MM.pdf")
    val vm = viewModel()
    advanceUntilIdle()
    vm.onAddDocuments(listOf(picked("MM.pdf")))
    advanceUntilIdle()

    vm.onRemoveDocument("blob-MM.pdf")
    advanceUntilIdle()

    assertThat(vm.uiState.value.sources.documents).isEmpty()
    coVerify {
      attachments.release(
        Attachment(
          id = "blob-MM.pdf",
          name = "MM.pdf"
        ), null
      )
    }
  }

  @Test
  fun aPickIsCheckedForTypeSizeAndCount() = runTest(dispatcher) {
    storing("a.pdf", "b.pdf", "c.pdf", "d.pdf")
    coEvery {
      attachments.addPickedFile(
        THING_ID,
        match { it.name == "big.pdf" },
        "big.pdf",
        any()
      )
    } throws FileTooLargeException(1)
    val vm = viewModel()
    advanceUntilIdle()

    vm.onAddDocuments(listOf(picked("notes.txt", mime = "text/plain")))
    advanceUntilIdle()
    assertThat(vm.uiState.value.sources.problem).isEqualTo(DocumentProblem.UNSUPPORTED)

    vm.onAddDocuments(listOf(picked("big.pdf")))
    advanceUntilIdle()
    assertThat(vm.uiState.value.sources.problem).isEqualTo(DocumentProblem.TOO_LARGE)

    vm.onAddDocuments(
      listOf(
        picked("a.pdf"),
        picked("b.pdf"),
        picked("c.pdf"),
        picked("d.pdf")
      )
    )
    advanceUntilIdle()
    assertThat(vm.uiState.value.sources.documents).hasSize(3)
    assertThat(vm.uiState.value.sources.problem).isEqualTo(DocumentProblem.TOO_MANY)
  }

  private companion object {
    const val THING_ID = "thing-1"
  }
}
