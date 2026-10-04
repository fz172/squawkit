package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.FileTooLargeException
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.model.taskFromDraftArg
import dev.fanfly.wingslog.feature.tasks.model.toDraftArg
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.id.AttachmentId
import dev.fanfly.wingslog.rpc.suggesttasks.IdentifiedDocument
import dev.fanfly.wingslog.id.SuggestionId
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.MeterRule
import dev.fanfly.wingslog.task.SeasonalRule
import dev.fanfly.wingslog.task.StarterTask
import dev.fanfly.wingslog.task.TaskOrigin
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.task.TimeRule
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.AttachmentType
import dev.fanfly.wingslog.feature.attachment.model.attachmentFromDocumentArg
import dev.fanfly.wingslog.feature.attachment.model.toDocumentArg
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

/**
 * The two §13 events and what they count. `starter_tasks_offered` is the denominator: without it
 * a low acceptance count cannot be told apart from packs never shown, so it has to fire exactly
 * when a pack is on screen — and not for a Thing with nothing to offer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StarterPackViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val analytics = RecordingAnalyticsManager()
  private val fleetManager = mockk<FleetManager>()
  private val taskDataManager = mockk<TaskDataManager>()
  private val written = mutableListOf<MaintenanceTask>()

  private val pack = listOf(
    StarterTask(
      title = "HVAC filter",
      description = "Quarterly",
      interval_months = 3,
      default_selected = true
    ),
    StarterTask(
      title = "Clean gutters",
      description = "Twice a year",
      interval_months = 6,
      default_selected = true
    ),
    StarterTask(
      title = "Septic pump-out",
      description = "If on septic",
      interval_months = 36
    ),
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    val card = slot<MaintenanceTask>()
    coEvery { taskDataManager.addTask(THING_ID, capture(card)) } answers {
      written += card.captured
      Result.success(true)
    }
    // The Thing has no tasks unless a test says so.
    every { taskDataManager.observeTasks(THING_ID) } returns flowOf(emptyList())
    // A model run can start, without documents, unless a test says otherwise.
    coEvery { suggestions.eligibility(THING_ID, any()) } returns AiEligibility(true, null, false, null)
    coEvery { suggestions.isOwner(THING_ID) } returns true
    coEvery { suggestions.documentsOf(any()) } returns emptyList()
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private val suggestions = mockk<TaskSuggestionManager>(relaxUnitFun = true)
  private val attachments = mockk<AttachmentManager>(relaxUnitFun = true)
  private val entry = mockk<TaskSuggestionEntry> {
    every { observe(THING_ID) } returns flowOf(SuggestEntry.Available)
  }

  private fun viewModel(
    starterTasks: List<StarterTask>,
    mode: String? = null,
    serverSource: Boolean = false,
    document: Attachment? = null,
  ): StarterPackViewModel {
    val thing = Thing(
      id = THING_ID,
      template = ThingTemplate(
        id = "home",
        version = 7,
        starter_tasks = starterTasks
      ),
    )
    every { fleetManager.loadThing(THING_ID) } returns flowOf(thing)
    return StarterPackViewModel(
      fleetManager = fleetManager,
      taskDataManager = taskDataManager,
      templateRegistry = BakedInTemplateRegistry(appVersionCode = 1),
      analytics = analytics,
      capability = AppCapability(
        isDeveloperOptionsSupported = serverSource,
        isCameraCaptureSupported = true,
        isAnonymousLoginSupported = true,
        isAdsSupported = false,
        isTaskSuggestionsSupported = serverSource,
      ),
      suggestionManager = suggestions,
      suggestEntry = entry,
      attachmentManager = attachments,
      savedStateHandle = SavedStateHandle(
        buildMap {
          put(Screen.THING_ID, THING_ID)
          if (mode != null) put(Screen.SUGGESTIONS_MODE, mode)
          if (document != null) put(Screen.SUGGESTIONS_DOCUMENT, document.toDocumentArg())
        },
      ),
    )
  }

  @Test
  fun showingThePackEmitsOfferedOnceWithTheWholeCount() = runTest(dispatcher) {
    val vm = viewModel(pack)
    advanceUntilIdle()

    assertThat(vm.uiState.value.isLoading).isFalse()
    // Nothing checked to start, whatever the pack's default (PRD R27, 2026-10-03).
    assertThat(vm.uiState.value.items.map { it.selected }).containsExactly(
      false,
      false,
      false
    )
    assertThat(analytics.countOf("starter_tasks_offered")).isEqualTo(1)
    assertThat(
      analytics.paramsFor("starter_tasks_offered")
        .single()
    )
      .containsAtLeastEntriesIn(
        mapOf(
          "template_id" to "home",
          "task_count" to "3"
        )
      )
    assertThat(analytics.countOf("starter_tasks_accepted")).isEqualTo(0)
  }

  @Test
  fun eachStarterTaskShowsAsTheCuratedSuggestionTheServerWouldSend() =
    runTest(dispatcher) {
      val oil = StarterTask(
        title = "Oil change",
        description = "Oil and filter",
        meter_key = "odometer",
        interval = 5000f,
        interval_months = 6,
        component_slot_key = "engine",
        months = listOf(10, 4, 4, 13),
        default_selected = true,
      )
      val vm = viewModel(listOf(pack[0], oil))
      advanceUntilIdle()

      val shown = vm.uiState.value.items[1].suggestion
      assertThat(shown.suggestion_id?.value_).isEqualTo("c1")
      assertThat(shown.title).isEqualTo("Oil change")
      assertThat(shown.description).isEqualTo("Oil and filter")
      assertThat(shown.component_slot_key).isEqualTo("engine")
      assertThat(shown.preselect).isTrue()
      assertThat(shown.origin_kind).isEqualTo(TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED)
      assertThat(shown.rules).containsExactly(
        InspectionRule(seasonal_rule = SeasonalRule(months = listOf(4, 10))),
        InspectionRule(time_rule = TimeRule(interval_months = 6)),
        InspectionRule(
          meter_rule = MeterRule(
            meter_key = "odometer",
            interval = 5000f
          )
        ),
      )
        .inOrder()
      assertThat(vm.uiState.value.items[1].starterTask).isEqualTo(oil)
    }

  @Test
  fun acceptingWritesTheCheckedOnesAndCountsOnlyThose() = runTest(dispatcher) {
    val vm = viewModel(pack)
    advanceUntilIdle()
    vm.onToggle(1) // the gutters
    vm.onToggle(2) // and the septic one

    vm.onAccept()
    advanceUntilIdle()

    assertThat(written.map { it.title }).containsExactly(
      "Clean gutters",
      "Septic pump-out"
    )
      .inOrder()
    // Ordinary cards: the due engine needs a dated TimeRule. Only the origin says they came from the
    // pack (design §4.1).
    written.forEach { assertThat(it.rules.single().time_rule?.creation_date).isNotNull() }
    assertThat(written.map { it.origin?.kind }
                 .distinct())
      .containsExactly(TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED)
    assertThat(
      analytics.paramsFor("starter_tasks_accepted")
        .single()
    )
      .containsAtLeastEntriesIn(
        mapOf(
          "template_id" to "home",
          "task_count" to "2"
        )
      )
    assertThat(vm.uiState.value.isDone).isTrue()
    assertThat(vm.uiState.value.acceptedCount).isEqualTo(2)
  }

  @Test
  fun skippingWritesNothingAndEmitsNoAcceptance() = runTest(dispatcher) {
    val vm = viewModel(pack)
    advanceUntilIdle()

    vm.onSkip()

    assertThat(written).isEmpty()
    coVerify(exactly = 0) { taskDataManager.addTask(any(), any()) }
    assertThat(analytics.countOf("starter_tasks_accepted")).isEqualTo(0)
    assertThat(vm.uiState.value.isDone).isTrue()
  }

  @Test
  fun aFailedWriteDropsOnlyItsOwnCard() = runTest(dispatcher) {
    coEvery {
      taskDataManager.addTask(
        THING_ID,
        match { it.title == "HVAC filter" })
    } returns
      Result.failure(IllegalStateException("offline"))
    val vm = viewModel(pack)
    advanceUntilIdle()
    vm.onToggle(0)
    vm.onToggle(1)

    vm.onAccept()
    advanceUntilIdle()

    assertThat(written.map { it.title }).containsExactly("Clean gutters")
    assertThat(
      analytics.paramsFor("starter_tasks_accepted")
        .single()
    ).containsEntry("task_count", "1")
  }

  @Test
  fun aThingWithNoPackIsNotAnOffer() = runTest(dispatcher) {
    val vm = viewModel(emptyList())
    advanceUntilIdle()

    assertThat(vm.uiState.value.isDone).isTrue()
    assertThat(analytics.countOf("starter_tasks_offered")).isEqualTo(0)
  }

  // The server source (developer builds until T25): cards come from a suggestion run.

  private fun curated(id: String, title: String, preselect: Boolean = true) =
    TaskSuggestion(
      suggestion_id = SuggestionId(value_ = id),
      title = title,
      preselect = preselect,
      origin_kind = TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED,
    )

  private val curatedList = SuggestTasksResult(
    suggestions = listOf(
      curated("c0", "Annual"),
      curated("c1", "Oil change"),
      curated("c2", "ELT", preselect = false)
    ),
  )

  private fun serving(
    vararg runs: SuggestionRun,
    started: AiStartResult = AiStartResult.Started(
      JOB,
      joined = false
    )
  ) {
    coEvery { suggestions.start(THING_ID, any(), any()) } returns started
    every { suggestions.observeRun(THING_ID) } returns flowOf(*runs)
  }

  private val runs = MutableSharedFlow<SuggestionRun>(replay = 1)

  /**
   * The suggest mode on [curated], whose sources sheet opens at once, and *Suggest* on it: the
   * model run is [JOB], and [runs] carries what the test emits next.
   */
  private fun TestScope.suggestModeModelRun(
    curated: SuggestTasksResult = curatedList,
    documentsAllowed: Boolean = false,
  ): StarterPackViewModel {
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = true) } returns
      AiStartResult.Started(CURATED_JOB, joined = false)
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false, documents = any()) } returns
      AiStartResult.Started(JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    coEvery { suggestions.eligibility(THING_ID, any()) } returns
      AiEligibility(true, null, documentsAllowed, null)
    coEvery { suggestions.isOwner(THING_ID) } returns true
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    runs.tryEmit(SuggestionRun.Ready(CURATED_JOB, curated))
    advanceUntilIdle()
    vm.onSuggest()
    advanceUntilIdle()
    return vm
  }

  @Test
  fun theStarterModeAsksForTheCuratedListAndShowsItWithNothingChecked() =
    runTest(dispatcher) {
      serving(SuggestionRun.Idle, SuggestionRun.Ready(JOB, curatedList))

      val vm = viewModel(pack, serverSource = true)
      advanceUntilIdle()

      coVerify {
        suggestions.start(
          THING_ID,
          Screen.StarterPack.MODE_STARTER,
          curatedOnly = true
        )
      }
      assertThat(vm.uiState.value.isLoading).isFalse()
      assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly(
        "Annual",
        "Oil change",
        "ELT"
      )
        .inOrder()
      // The server's preselect is not read: the user checks what they need (PRD R27).
      assertThat(vm.uiState.value.items.map { it.selected }).containsExactly(
        false,
        false,
        false
      )
      assertThat(
        analytics.paramsFor("starter_tasks_offered")
          .single()
      ).containsEntry("task_count", "3")
    }

  @Test
  fun theSuggestModeOffersTheAiButtonAndOpensNothingByItself() = runTest(dispatcher) {
    serving(SuggestionRun.Idle, SuggestionRun.Ready(JOB, curatedList))
    coEvery { suggestions.eligibility(THING_ID, any()) } returns
      AiEligibility(true, null, true, null)

    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()

    coVerify { suggestions.start(THING_ID, Screen.StarterPack.MODE_SUGGEST, curatedOnly = true) }
    coVerify(exactly = 0) { suggestions.start(THING_ID, any(), curatedOnly = false, any()) }
    assertThat(vm.uiState.value.items).hasSize(3)
    assertThat(vm.uiState.value.canSuggest).isTrue()
    // Owner's decision, 2026-10-04: nothing pops up unasked.
    assertThat(vm.uiState.value.sources).isNull()
  }

  private fun picked(name: String, mime: String = "application/pdf") =
    PickedFile(uri = "file://$name", name = name, mimeType = mime, sizeBytes = 1_000)

  private fun storing(vararg names: String) {
    names.forEach { name ->
      coEvery {
        attachments.addPickedFile(THING_ID, match { it.name == name }, name, QuotaChecker.MAX_AI_DOCUMENT_BYTES)
      } returns Attachment(id = "blob-$name", name = name)
    }
  }

  /** The suggest mode on [eligibility], and the AI button tapped: the sheet, where docs are allowed. */
  private fun TestScope.openSheet(
    eligibility: AiEligibility = AiEligibility(true, null, true, null),
    owner: Boolean = true,
  ): StarterPackViewModel {
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = true) } returns
      AiStartResult.Started(CURATED_JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    coEvery { suggestions.eligibility(THING_ID, any()) } returns eligibility
    coEvery { suggestions.isOwner(THING_ID) } returns owner
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false, documents = any()) } returns
      AiStartResult.Started(JOB, joined = false)
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    runs.tryEmit(SuggestionRun.Ready(CURATED_JOB, curatedList))
    advanceUntilIdle()
    // The AI button.
    vm.onOpenSources()
    advanceUntilIdle()
    return vm
  }

  @Test
  fun theAiButtonAsksForDocumentsWhereTheOwnerHasPro() = runTest(dispatcher) {
    val vm = openSheet()

    coVerify { suggestions.eligibility(THING_ID, false) }
    assertThat(vm.uiState.value.sources).isEqualTo(
      SourcesState(isChecking = false, documentsAllowed = true, isOwner = true),
    )
    coVerify(exactly = 0) { suggestions.start(THING_ID, any(), curatedOnly = false, any()) }
  }

  @Test
  fun withoutProTheAiButtonStartsTheRunWithNoSheet() = runTest(dispatcher) {
    val vm = openSheet(eligibility = AiEligibility(true, null, false, null), owner = false)

    assertThat(vm.uiState.value.sources).isNull()
    assertThat(vm.uiState.value.isSuggesting).isTrue()
    coVerify { suggestions.start(THING_ID, any(), curatedOnly = false, documents = emptyList()) }
  }

  @Test
  fun theReviewCarriesWhatTheRunMadeOfEachDocument() = runTest(dispatcher) {
    val vm = suggestModeModelRun()
    val manual = IdentifiedDocument(
      blob_id = AttachmentId(value_ = "blob-1"),
      name = "915iS_MM.pdf",
      title = "Rotax 915 iS MM",
      matches_thing = true,
    )
    val stray = IdentifiedDocument(name = "Airmaster_manual.pdf", matches_thing = false)

    runs.emit(SuggestionRun.Ready(JOB, curatedList.copy(documents = listOf(manual, stray))))
    advanceUntilIdle()

    assertThat(vm.uiState.value.documents).containsExactly(manual, stray).inOrder()
  }

  @Test
  fun theRunsDocumentsAreAtHandForItsCitations() = runTest(dispatcher) {
    val manual = Attachment(id = "blob-1", name = "MM.pdf", mime_type = "application/pdf")
    coEvery { suggestions.documentsOf(JOB) } returns listOf(manual)
    val vm = suggestModeModelRun()

    runs.emit(SuggestionRun.Working(JOB, "reading_document", "MM.pdf", curatedList))
    advanceUntilIdle()

    assertThat(vm.uiState.value.runDocuments).containsExactly(manual)
    coVerify(exactly = 1) { suggestions.documentsOf(JOB) }
  }

  @Test
  fun aDocumentIsNamedByTheTitleItCarriesElseItsFileName() {
    assertThat(IdentifiedDocument(name = "a.pdf", title = "Rotax MM").displayTitle()).isEqualTo("Rotax MM")
    assertThat(IdentifiedDocument(name = "a.pdf").displayTitle()).isEqualTo("a.pdf")
  }

  @Test
  fun tasksFromADocumentOpensTheSheetToPickOnce() = runTest(dispatcher) {
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = true) } returns
      AiStartResult.Started(CURATED_JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    coEvery { suggestions.eligibility(THING_ID, any()) } returns AiEligibility(true, null, true, null)
    coEvery { suggestions.isOwner(THING_ID) } returns true
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_DOCUMENT, serverSource = true)
    runs.tryEmit(SuggestionRun.Ready(CURATED_JOB, curatedList))
    advanceUntilIdle()

    assertThat(vm.uiState.value.sources?.pickOnOpen).isTrue()
    vm.onPickOnOpenHandled()
    assertThat(vm.uiState.value.sources?.pickOnOpen).isFalse()

    // Opened again from *Suggest more*, it waits for the user.
    vm.onSourcesDismissed()
    vm.onOpenSources()
    assertThat(vm.uiState.value.sources?.pickOnOpen).isFalse()
  }

  private val onRecord = Attachment(
    id = "blob-poh",
    name = "POH.pdf",
    type = AttachmentType.ATTACHMENT_TYPE_PDF,
    mime_type = "application/pdf",
  )

  private fun TestScope.findTasksIn(
    document: Attachment,
    eligibility: AiEligibility = AiEligibility(true, null, true, null),
  ): StarterPackViewModel {
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = true) } returns
      AiStartResult.Started(CURATED_JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    coEvery { suggestions.eligibility(THING_ID, any()) } returns eligibility
    coEvery { suggestions.isOwner(THING_ID) } returns true
    val vm = viewModel(
      pack,
      mode = Screen.StarterPack.MODE_DOCUMENT,
      serverSource = true,
      document = document,
    )
    runs.tryEmit(SuggestionRun.Ready(CURATED_JOB, curatedList))
    advanceUntilIdle()
    return vm
  }

  @Test
  fun findTasksInADocumentStartsTheSheetWithItAndNoPicker() = runTest(dispatcher) {
    val vm = findTasksIn(onRecord)

    assertThat(vm.uiState.value.sources?.documents).containsExactly(onRecord)
    assertThat(vm.uiState.value.sources?.pickOnOpen).isFalse()
    coVerify(exactly = 0) { attachments.addPickedFile(any(), any(), any(), any()) }
  }

  @Test
  fun suggestReadsTheStoredFile() = runTest(dispatcher) {
    val vm = findTasksIn(onRecord)
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false, documents = any()) } returns
      AiStartResult.Started(JOB, joined = false)

    vm.onSuggest()
    advanceUntilIdle()

    coVerify { suggestions.start(THING_ID, any(), curatedOnly = false, documents = listOf(onRecord)) }
  }

  @Test
  fun aFreeOwnerGetsTheUpsellInsteadOfTheDocument() = runTest(dispatcher) {
    val vm = findTasksIn(onRecord, eligibility = AiEligibility(true, null, false, null))

    assertThat(vm.uiState.value.sources?.documents).isEmpty()
    // The sheet acts on it with the upsell, as for *Tasks from a document*.
    assertThat(vm.uiState.value.sources?.pickOnOpen).isTrue()
  }

  @Test
  fun aFileTheReaderCannotTakeIsNotPreset() = runTest(dispatcher) {
    val vm = findTasksIn(onRecord.copy(mime_type = "text/plain", type = AttachmentType.ATTACHMENT_TYPE_FILE))

    assertThat(vm.uiState.value.sources?.documents).isEmpty()
    assertThat(vm.uiState.value.sources?.pickOnOpen).isTrue()
  }

  @Test
  fun theDocumentRouteCarriesTheFile() {
    val route = Screen.StarterPack.createRoute(THING_ID, document = onRecord.toDocumentArg())
    assertThat(route).startsWith("starter_pack/$THING_ID?mode=document&document=")
    assertThat(attachmentFromDocumentArg(route.substringAfter("document="))).isEqualTo(onRecord)
  }

  @Test
  fun theSuggestModeDoesNotPickByItself() = runTest(dispatcher) {
    val vm = openSheet()
    assertThat(vm.uiState.value.sources?.pickOnOpen).isFalse()
  }

  @Test
  fun aPickedPdfOrPhotoIsStoredAtTheAiDocumentCap() = runTest(dispatcher) {
    val vm = openSheet()
    storing("POH.pdf", "page.jpg")

    vm.onAddDocuments(listOf(picked("POH.pdf"), picked("page.jpg", "image/jpeg")))
    advanceUntilIdle()

    assertThat(vm.uiState.value.sources?.documents?.map { it.name })
      .containsExactly("POH.pdf", "page.jpg").inOrder()
    assertThat(vm.uiState.value.sources?.isAdding).isFalse()
    assertThat(vm.uiState.value.sources?.problem).isNull()
  }

  @Test
  fun anythingButAPdfOrAnImageIsNotAdded() = runTest(dispatcher) {
    val vm = openSheet()

    vm.onAddDocuments(listOf(picked("notes.txt", "text/plain")))
    advanceUntilIdle()

    assertThat(vm.uiState.value.sources?.documents).isEmpty()
    assertThat(vm.uiState.value.sources?.problem).isEqualTo(DocumentProblem.UNSUPPORTED)
    coVerify(exactly = 0) { attachments.addPickedFile(any(), any(), any(), any()) }
    storing("POH.pdf")
    vm.onAddDocuments(listOf(picked("POH.pdf")))
    advanceUntilIdle()
    assertThat(vm.uiState.value.sources?.problem).isNull()
  }

  @Test
  fun aFileOverTheCapSaysSo() = runTest(dispatcher) {
    val vm = openSheet()
    coEvery { attachments.addPickedFile(THING_ID, any(), any(), any()) } throws
      FileTooLargeException(30_000_000)

    vm.onAddDocuments(listOf(picked("AMM.pdf")))
    advanceUntilIdle()

    assertThat(vm.uiState.value.sources?.problem).isEqualTo(DocumentProblem.TOO_LARGE)
  }

  @Test
  fun aFourthDocumentIsNotAdded() = runTest(dispatcher) {
    val vm = openSheet()
    storing("a.pdf", "b.pdf", "c.pdf", "d.pdf")

    vm.onAddDocuments(listOf(picked("a.pdf"), picked("b.pdf"), picked("c.pdf"), picked("d.pdf")))
    advanceUntilIdle()

    assertThat(vm.uiState.value.sources?.documents).hasSize(3)
    assertThat(vm.uiState.value.sources?.problem).isEqualTo(DocumentProblem.TOO_MANY)
  }

  @Test
  fun aFreeOwnersPickAddsNothing() = runTest(dispatcher) {
    // Only a document entry point opens the sheet for a free owner.
    val vm = findTasksIn(onRecord, eligibility = AiEligibility(true, null, false, null))

    vm.onAddDocuments(listOf(picked("POH.pdf")))
    advanceUntilIdle()

    coVerify(exactly = 0) { attachments.addPickedFile(any(), any(), any(), any()) }
  }

  @Test
  fun removingADocumentLetsGoOfIt() = runTest(dispatcher) {
    val vm = openSheet()
    storing("POH.pdf")
    vm.onAddDocuments(listOf(picked("POH.pdf")))
    advanceUntilIdle()

    vm.onRemoveDocument("blob-POH.pdf")
    advanceUntilIdle()

    assertThat(vm.uiState.value.sources?.documents).isEmpty()
    coVerify { attachments.release(Attachment(id = "blob-POH.pdf", name = "POH.pdf"), null) }
  }

  @Test
  fun closingTheSheetLetsGoOfItsDocumentsAndKeepsTheList() = runTest(dispatcher) {
    val vm = openSheet()
    storing("POH.pdf")
    vm.onAddDocuments(listOf(picked("POH.pdf")))
    advanceUntilIdle()

    vm.onSourcesDismissed()
    advanceUntilIdle()

    assertThat(vm.uiState.value.sources).isNull()
    assertThat(vm.uiState.value.isDone).isFalse()
    assertThat(vm.uiState.value.canSuggest).isTrue()
    coVerify { attachments.release(Attachment(id = "blob-POH.pdf", name = "POH.pdf"), null) }
  }

  @Test
  fun suggestStartsTheModelRunWithTheDocumentsAndCountsThem() = runTest(dispatcher) {
    val vm = openSheet()
    storing("POH.pdf")
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false, documents = any()) } returns
      AiStartResult.Started(JOB, joined = false)
    vm.onAddDocuments(listOf(picked("POH.pdf")))
    advanceUntilIdle()

    vm.onSuggest()
    advanceUntilIdle()

    val manual = Attachment(id = "blob-POH.pdf", name = "POH.pdf")
    coVerify { suggestions.start(THING_ID, any(), curatedOnly = false, documents = listOf(manual)) }
    assertThat(vm.uiState.value.sources).isNull()
    assertThat(vm.uiState.value.isSuggesting).isTrue()
    // The run owns them now; the manager lets them go when it ends.
    coVerify(exactly = 0) { attachments.release(any(), any()) }
    assertThat(analytics.paramsFor("task_suggestions_requested").single())
      .containsAtLeastEntriesIn(mapOf("source" to "suggest", "document_count" to "1"))
  }

  @Test
  fun aRefusedStartLetsGoOfTheDocuments() = runTest(dispatcher) {
    val vm = openSheet()
    storing("POH.pdf")
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false, documents = any()) } returns
      AiStartResult.Refused(AiErrorCode.DOCUMENT_MISSING, null)
    vm.onAddDocuments(listOf(picked("POH.pdf")))
    advanceUntilIdle()

    vm.onSuggest()
    advanceUntilIdle()

    assertThat(vm.uiState.value.notice).isEqualTo(AiErrorCode.DOCUMENT_MISSING)
    coVerify { attachments.release(Attachment(id = "blob-POH.pdf", name = "POH.pdf"), null) }
  }

  @Test
  fun theDailyLimitShowsWhenAiIsBackAndOpensNoSheet() = runTest(dispatcher) {
    val back = Instant.fromEpochMilliseconds(9_000)
    val vm = openSheet(eligibility = AiEligibility(false, AiErrorCode.DAILY_LIMIT, true, back))

    assertThat(vm.uiState.value.sources).isNull()
    assertThat(vm.uiState.value.canSuggest).isFalse()
    assertThat(vm.uiState.value.aiUnavailable).isEqualTo(AiSkipped(AiErrorCode.DAILY_LIMIT, back))
    assertThat(vm.uiState.value.isCheckingAi).isFalse()
    // Nothing on screen starts a run.
    vm.onOpenSources()
    vm.onSuggest()
    advanceUntilIdle()
    assertThat(vm.uiState.value.sources).isNull()
    coVerify(exactly = 0) { suggestions.start(THING_ID, any(), curatedOnly = false, any()) }
  }

  @Test
  fun theSheetWaitsForTheAnswerAndSaysItIsChecking() = runTest(dispatcher) {
    val answer = CompletableDeferred<AiEligibility>()
    coEvery { suggestions.eligibility(THING_ID, any()) } coAnswers { answer.await() }
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = true) } returns
      AiStartResult.Started(CURATED_JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_DOCUMENT, serverSource = true)
    runs.tryEmit(SuggestionRun.Ready(CURATED_JOB, curatedList))
    advanceUntilIdle()

    // The cards are up; the sheet is not, and the screen says why it is waiting.
    assertThat(vm.uiState.value.items).hasSize(3)
    assertThat(vm.uiState.value.isCheckingAi).isTrue()
    assertThat(vm.uiState.value.sources).isNull()

    answer.complete(AiEligibility(true, null, true, null))
    advanceUntilIdle()

    assertThat(vm.uiState.value.isCheckingAi).isFalse()
    assertThat(vm.uiState.value.sources?.isChecking).isFalse()
    assertThat(vm.uiState.value.sources?.documentsAllowed).isTrue()
  }

  @Test
  fun theSheetOpensFromTheAnswerAlreadyIn() = runTest(dispatcher) {
    serving(SuggestionRun.Ready(JOB, curatedList))
    coEvery { suggestions.eligibility(THING_ID, any()) } returns AiEligibility(true, null, true, null)
    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()

    vm.onOpenSources()

    assertThat(vm.uiState.value.sources?.isChecking).isFalse()
    coVerify(exactly = 1) { suggestions.eligibility(THING_ID, any()) }
  }

  @Test
  fun theStarterModeAlsoSaysWhenAiIsBack() = runTest(dispatcher) {
    val back = Instant.fromEpochMilliseconds(9_000)
    coEvery { suggestions.eligibility(THING_ID, any()) } returns
      AiEligibility(false, AiErrorCode.DAILY_LIMIT, false, back)
    serving(SuggestionRun.Ready(JOB, curatedList))
    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.canSuggest).isFalse()
    assertThat(vm.uiState.value.aiUnavailable?.reason).isEqualTo(AiErrorCode.DAILY_LIMIT)
  }

  @Test
  fun closingTheSheetOverNoCardsClosesTheScreen() = runTest(dispatcher) {
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = true) } returns
      AiStartResult.Started(CURATED_JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    coEvery { suggestions.eligibility(THING_ID, any()) } returns AiEligibility(true, null, true, null)
    coEvery { suggestions.isOwner(THING_ID) } returns true
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    // The custom template: no curated list, and the sheet stays open over nothing.
    runs.tryEmit(SuggestionRun.Ready(CURATED_JOB, SuggestTasksResult()))
    advanceUntilIdle()
    vm.onOpenSources()
    assertThat(vm.uiState.value.isDone).isFalse()
    assertThat(vm.uiState.value.sources).isNotNull()

    vm.onSourcesDismissed()

    assertThat(vm.uiState.value.isDone).isTrue()
  }

  @Test
  fun theModelsAnswerReplacesTheCuratedCardsAndKeepsTheUsersChoices() =
    runTest(dispatcher) {
      val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          any()
        )
      } returns AiStartResult.Started(JOB, joined = false)
      every { suggestions.observeRun(THING_ID) } returns runs
      val vm = viewModel(
        pack,
        mode = Screen.StarterPack.MODE_SUGGEST,
        serverSource = true
      )
      runs.emit(SuggestionRun.Working(JOB, "tailoring", null, curatedList))
      advanceUntilIdle()
      vm.onToggle(0) // check the annual

      val merged = SuggestTasksResult(
        suggestions = listOf(
          TaskSuggestion(
            suggestion_id = SuggestionId(value_ = "s1"),
            title = "Oil and filter",
            preselect = true
          ),
          curated("c0", "Annual"),
          curated("c2", "ELT", preselect = false),
        ),
      )
      runs.emit(SuggestionRun.Ready(JOB, merged))
      advanceUntilIdle()

      assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly(
        "Oil and filter",
        "Annual",
        "ELT"
      )
        .inOrder()
      // The annual stays checked; the model's new card starts unchecked, as every card does.
      assertThat(vm.uiState.value.items.map { it.selected }).containsExactly(
        false,
        true,
        false
      )
        .inOrder()
      // Offered once, when cards first showed.
      assertThat(analytics.countOf("starter_tasks_offered")).isEqualTo(1)
    }

  @Test
  fun ignoresAnOlderJobUntilTheListenerCatchesUp() = runTest(dispatcher) {
    serving(SuggestionRun.Ready(AiJobId("older"), curatedList))

    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.items).isEmpty()
    assertThat(vm.uiState.value.isLoading).isTrue()
  }

  @Test
  fun acceptingWritesTheTickedSuggestionsThroughTheManager() =
    runTest(dispatcher) {
      val ready = SuggestionRun.Ready(JOB, curatedList)
      serving(ready)
      val chosen = slot<List<AcceptedSuggestion>>()
      coEvery { suggestions.accept(THING_ID, ready, capture(chosen)) } returns 2
      val vm = viewModel(pack, serverSource = true)
      advanceUntilIdle()
      vm.onToggle(0)
      vm.onToggle(1)

      vm.onAccept()
      advanceUntilIdle()

      assertThat(chosen.captured.map { it.suggestion.title }).containsExactly(
        "Annual",
        "Oil change"
      )
        .inOrder()
      coVerify(exactly = 0) { taskDataManager.addTask(any(), any()) }
      assertThat(vm.uiState.value.acceptedCount).isEqualTo(2)
      assertThat(
        analytics.paramsFor("starter_tasks_accepted")
          .single()
      ).containsEntry("task_count", "2")
    }

  @Test
  fun aRefusedStartClosesTheScreenWithNothingOffered() = runTest(dispatcher) {
    serving(started = AiStartResult.Refused(AiErrorCode.UNAVAILABLE, null))

    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.isDone).isTrue()
    // The task tab says why: "No internet connection" (PRD R51).
    assertThat(vm.uiState.value.closingError).isEqualTo(AiErrorCode.UNAVAILABLE)
    assertThat(analytics.countOf("starter_tasks_offered")).isEqualTo(0)
  }

  @Test
  fun aTemplateWithNoCuratedListIsNotAnOffer() = runTest(dispatcher) {
    serving(SuggestionRun.Empty(JOB, result = null))

    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.isDone).isTrue()
    assertThat(analytics.countOf("starter_tasks_offered")).isEqualTo(0)
  }

  @Test
  fun skippingClosesAFinishedRunButLeavesAWorkingOneRunning() =
    runTest(dispatcher) {
      serving(SuggestionRun.Ready(JOB, curatedList))
      val finished = viewModel(pack, serverSource = true)
      advanceUntilIdle()
      finished.onSkip()
      advanceUntilIdle()
      coVerify(exactly = 1) { suggestions.dismiss(JOB) }

      serving(SuggestionRun.Working(JOB, "tailoring", null, curatedList))
      val working = viewModel(
        pack,
        mode = Screen.StarterPack.MODE_SUGGEST,
        serverSource = true
      )
      advanceUntilIdle()
      working.onSkip()
      advanceUntilIdle()
      coVerify(exactly = 1) { suggestions.dismiss(JOB) }
    }

  @Test
  fun theCuratedListOffersSuggestTasksWhereTheThingIsDescribedEnough() =
    runTest(dispatcher) {
      serving(SuggestionRun.Ready(JOB, curatedList))
      val described = viewModel(pack, serverSource = true)
      advanceUntilIdle()
      assertThat(described.uiState.value.canSuggest).isTrue()

      every { entry.observe(THING_ID) } returns flowOf(
        SuggestEntry.MissingIdentity(
          listOf("Model")
        )
      )
      val thin = viewModel(pack, serverSource = true)
      advanceUntilIdle()
      assertThat(thin.uiState.value.canSuggest).isFalse()

      // Not on the app's pack, and not once the model run is the one shown.
      val appPack = viewModel(pack)
      advanceUntilIdle()
      assertThat(appPack.uiState.value.canSuggest).isFalse()
      serving(SuggestionRun.Working(JOB, null, null, curatedList))
      val suggesting = viewModel(
        pack,
        mode = Screen.StarterPack.MODE_SUGGEST,
        serverSource = true
      )
      advanceUntilIdle()
      assertThat(suggesting.uiState.value.canSuggest).isFalse()
      assertThat(suggesting.uiState.value.isSuggesting).isTrue()
    }

  @Test
  fun suggestTasksStartsTheModelRunAndFollowsItFromTheCuratedCards() =
    runTest(dispatcher) {
      val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          curatedOnly = true
        )
      } returns AiStartResult.Started(JOB, joined = false)
      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          curatedOnly = false
        )
      } returns AiStartResult.Started(AI_JOB, joined = false)
      every { suggestions.observeRun(THING_ID) } returns runs
      val vm = viewModel(pack, serverSource = true)
      runs.emit(SuggestionRun.Ready(JOB, curatedList))
      advanceUntilIdle()

      vm.onSuggest()
      advanceUntilIdle()

      assertThat(vm.uiState.value.canSuggest).isFalse()
      assertThat(vm.uiState.value.isSuggesting).isTrue()
      coVerify { suggestions.dismiss(JOB) }

      runs.emit(SuggestionRun.Working(AI_JOB, "tailoring", null, curatedList))
      advanceUntilIdle()
      assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly(
        "Annual",
        "Oil change",
        "ELT"
      )
        .inOrder()

      val merged = SuggestTasksResult(
        suggestions = listOf(
          TaskSuggestion(
            suggestion_id = SuggestionId(value_ = "s1"), title = "Spark plugs"
          )
        ) + curatedList.suggestions
      )
      runs.emit(SuggestionRun.Ready(AI_JOB, merged))
      advanceUntilIdle()
      assertThat(vm.uiState.value.isSuggesting).isFalse()
      assertThat(vm.uiState.value.items.first().suggestion.title).isEqualTo("Spark plugs")
    }

  @Test
  fun aSuggestTasksThatDoesNotStartLeavesTheButton() = runTest(dispatcher) {
    serving(SuggestionRun.Ready(JOB, curatedList))
    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()
    coEvery {
      suggestions.start(
        THING_ID,
        any(),
        curatedOnly = false
      )
    } returns AiStartResult.Refused(AiErrorCode.UNAVAILABLE, null)

    vm.onSuggest()
    advanceUntilIdle()

    assertThat(vm.uiState.value.canSuggest).isTrue()
    assertThat(vm.uiState.value.isSuggesting).isFalse()
    assertThat(vm.uiState.value.notice).isEqualTo(AiErrorCode.UNAVAILABLE)
    coVerify(exactly = 0) { suggestions.dismiss(any()) }

    vm.onNoticeShown()
    assertThat(vm.uiState.value.notice).isNull()
  }

  @Test
  fun aFailedModelRunKeepsTheCuratedCardsAndOffersTryAgain() =
    runTest(dispatcher) {
      val vm = suggestModeModelRun()
      runs.emit(
        SuggestionRun.Failed(
          JOB,
          AiErrorCode.PROVIDER_ERROR,
          curatedList
        )
      )
      advanceUntilIdle()

      assertThat(vm.uiState.value.failure).isEqualTo(AiErrorCode.PROVIDER_ERROR)
      assertThat(vm.uiState.value.isDone).isFalse()
      assertThat(vm.uiState.value.items).hasSize(3)

      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          curatedOnly = false
        )
      } returns AiStartResult.Started(AI_JOB, joined = false)
      vm.onRetry()
      advanceUntilIdle()

      assertThat(vm.uiState.value.failure).isNull()
      assertThat(vm.uiState.value.isSuggesting).isTrue()
      coVerify { suggestions.dismiss(JOB) }
      runs.emit(SuggestionRun.Working(AI_JOB, "tailoring", null, curatedList))
      advanceUntilIdle()
      assertThat(vm.uiState.value.failure).isNull()
    }

  @Test
  fun aFailedRunWithNothingToShowClosesAndSaysWhy() = runTest(dispatcher) {
    val vm = suggestModeModelRun(curated = SuggestTasksResult())
    runs.emit(SuggestionRun.Failed(JOB, AiErrorCode.STALE, result = null))
    advanceUntilIdle()

    assertThat(vm.uiState.value.isDone).isTrue()
    assertThat(vm.uiState.value.closingError).isEqualTo(AiErrorCode.STALE)
  }

  @Test
  fun theProgressLineFollowsTheRunsStage() = runTest(dispatcher) {
    val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
    coEvery {
      suggestions.start(
        THING_ID,
        any(),
        any()
      )
    } returns AiStartResult.Started(JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    val vm = viewModel(
      pack,
      mode = Screen.StarterPack.MODE_SUGGEST,
      serverSource = true
    )

    runs.emit(
      SuggestionRun.Working(
        JOB,
        "reading_document",
        "Rotax MM.pdf",
        curatedList
      )
    )
    advanceUntilIdle()
    assertThat(vm.uiState.value.stage).isEqualTo("reading_document")
    assertThat(vm.uiState.value.stageArg).isEqualTo("Rotax MM.pdf")

    runs.emit(SuggestionRun.Ready(JOB, curatedList))
    advanceUntilIdle()
    assertThat(vm.uiState.value.isSuggesting).isFalse()
    assertThat(vm.uiState.value.stage).isNull()
  }

  @Test
  fun aCuratedOnlyAnswerSaysWhyTheModelWasSkipped() = runTest(dispatcher) {
    val skipped =
      AiSkipped(AiErrorCode.DAILY_LIMIT, Instant.fromEpochMilliseconds(5_000))
    val vm = suggestModeModelRun()
    runs.emit(SuggestionRun.Ready(JOB, curatedList, skipped))
    advanceUntilIdle()

    assertThat(vm.uiState.value.aiSkipped).isEqualTo(skipped)
    assertThat(vm.uiState.value.items).hasSize(3)
  }

  @Test
  fun anEmptyModelRunKeepsTheCuratedCardsAndOffersAddDetails() =
    runTest(dispatcher) {
      val vm = suggestModeModelRun()
      runs.emit(SuggestionRun.Empty(JOB, curatedList))
      advanceUntilIdle()

      assertThat(vm.uiState.value.notEnough).isTrue()
      assertThat(vm.uiState.value.items).hasSize(3)

      vm.onAddDetails()
      advanceUntilIdle()
      coVerify { suggestions.dismiss(JOB) }
    }

  @Test
  fun anEmptyModelRunWithNoCuratedListStaysToSayNotEnough() =
    runTest(dispatcher) {
      // The custom template: the message is the whole screen (R21a).
      val vm = suggestModeModelRun(curated = SuggestTasksResult())
      runs.emit(SuggestionRun.Empty(JOB, result = null))
      advanceUntilIdle()

      assertThat(vm.uiState.value.isDone).isFalse()
      assertThat(vm.uiState.value.isLoading).isFalse()
      assertThat(vm.uiState.value.notEnough).isTrue()
    }

  @Test
  fun aSuggestionTheServerSaysIsTrackedIsNotShown() = runTest(dispatcher) {
    val tracked = curated("c0", "Annual").copy(
      matches_existing_task_id = MaintenanceTaskId(value_ = "task-annual")
    )
    serving(
      SuggestionRun.Ready(
        JOB,
        SuggestTasksResult(
          suggestions = listOf(
            tracked,
            curated("c1", "Oil change")
          )
        )
      )
    )

    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly(
      "Oil change"
    )
    // Offered: what was shown.
    assertThat(
      analytics.paramsFor("starter_tasks_offered")
        .single()
    ).containsEntry("task_count", "1")
  }

  @Test
  fun aSuggestionTitledLikeATaskTheThingHasIsNotShown() = runTest(dispatcher) {
    every { taskDataManager.observeTasks(THING_ID) } returns flowOf(
      listOf(
        MaintenanceTask(id = "t1", title = "  oil   CHANGE ")
      )
    )
    serving(SuggestionRun.Ready(JOB, curatedList))

    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly(
      "Annual",
      "ELT"
    )
      .inOrder()
  }

  @Test
  fun theAppsOwnPackAlsoHidesWhatTheThingHas() = runTest(dispatcher) {
    every { taskDataManager.observeTasks(THING_ID) } returns flowOf(
      listOf(
        MaintenanceTask(id = "t1", title = "Clean gutters")
      )
    )

    val vm = viewModel(pack)
    advanceUntilIdle()

    assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly(
      "HVAC filter",
      "Septic pump-out"
    )
      .inOrder()
  }

  private fun ai(id: String, title: String) = TaskSuggestion(
    suggestion_id = SuggestionId(value_ = id),
    title = title,
    origin_kind = TaskOriginKind.TASK_ORIGIN_KIND_AI_THING,
  )

  @Test
  fun aModelRunReportsRequestedThenShownOnceWithTheSplit() =
    runTest(dispatcher) {
      suggestModeModelRun()

      val answer = SuggestTasksResult(
        suggestions = listOf(
          ai(
            "s1",
            "Spark plugs"
          )
        ) + curatedList.suggestions
      )
      runs.emit(SuggestionRun.Ready(JOB, answer))
      advanceUntilIdle()
      runs.emit(
        SuggestionRun.Ready(
          JOB,
          answer
        )
      ) // a listener re-delivery is not a second answer
      advanceUntilIdle()

      assertThat(
        analytics.paramsFor("task_suggestions_requested")
          .single()
      ).containsAtLeastEntriesIn(
        mapOf("source" to "suggest", "document_count" to "0"),
      )
      assertThat(
        analytics.paramsFor("task_suggestions_shown")
          .single()
      ).containsAtLeastEntriesIn(
        mapOf(
          "curated_count" to "3",
          "ai_count" to "1",
          "latency_bucket" to "0-10s"
        ),
      )
    }

  @Test
  fun theCuratedListAloneIsNotAModelRequest() = runTest(dispatcher) {
    serving(SuggestionRun.Ready(JOB, curatedList))

    viewModel(pack, serverSource = true)
    advanceUntilIdle()

    assertThat(analytics.countOf("task_suggestions_requested")).isEqualTo(0)
    assertThat(analytics.countOf("task_suggestions_shown")).isEqualTo(0)
  }

  @Test
  fun suggestMoreReportsItsOwnEntryPointAndARefusalReportsWhy() =
    runTest(dispatcher) {
      serving(SuggestionRun.Ready(JOB, curatedList))
      val vm = viewModel(pack, serverSource = true)
      advanceUntilIdle()
      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          curatedOnly = false
        )
      } returns AiStartResult.Refused(AiErrorCode.UNAVAILABLE, null)

      vm.onSuggest()
      advanceUntilIdle()

      assertThat(
        analytics.paramsFor("task_suggestions_failed")
          .single()
      ).containsEntry("reason", "unavailable")

      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          curatedOnly = false
        )
      } returns AiStartResult.Started(AI_JOB, joined = false)
      vm.onSuggest()
      advanceUntilIdle()
      assertThat(
        analytics.paramsFor("task_suggestions_requested")
          .single()
      ).containsEntry("source", "suggest_more")
    }

  @Test
  fun aFailedRunIsReportedOnce() = runTest(dispatcher) {
    suggestModeModelRun()

    runs.emit(
      SuggestionRun.Failed(
        JOB,
        AiErrorCode.PROVIDER_ERROR,
        curatedList
      )
    )
    advanceUntilIdle()
    runs.emit(
      SuggestionRun.Failed(
        JOB,
        AiErrorCode.PROVIDER_ERROR,
        curatedList
      )
    )
    advanceUntilIdle()

    assertThat(
      analytics.paramsFor("task_suggestions_failed")
        .single()
    ).containsEntry("reason", "provider_error")
  }

  @Test
  fun acceptingReportsTheCuratedAndAiSplit() = runTest(dispatcher) {
    val answer = SuggestTasksResult(
      suggestions = listOf(
        ai(
          "s1",
          "Spark plugs"
        )
      ) + curatedList.suggestions
    )
    val ready = SuggestionRun.Ready(JOB, answer)
    serving(ready)
    coEvery { suggestions.accept(THING_ID, ready, any()) } returns 2
    val vm = viewModel(
      pack,
      mode = Screen.StarterPack.MODE_SUGGEST,
      serverSource = true
    )
    advanceUntilIdle()
    vm.onToggle(0)
    vm.onToggle(1)

    vm.onAccept()
    advanceUntilIdle()

    assertThat(
      analytics.paramsFor("task_suggestions_accepted")
        .single()
    ).containsAtLeastEntriesIn(
      mapOf("curated_count" to "1", "ai_count" to "1"),
    )
  }

  // Returning to a held answer (PRD R19): leaving does not lose the model's cards.

  @Test
  fun reopeningTheListShowsTheHeldModelAnswerInsteadOfStartingOver() =
    runTest(dispatcher) {
      val answer = SuggestTasksResult(
        suggestions = listOf(
          ai(
            "s1",
            "Tire rotation"
          )
        ) + curatedList.suggestions
      )
      serving(SuggestionRun.Ready(JOB, answer))

      val vm = viewModel(pack, serverSource = true)
      advanceUntilIdle()

      coVerify(exactly = 0) { suggestions.start(any(), any(), any()) }
      assertThat(vm.uiState.value.items.map { it.suggestion.title }).contains("Tire rotation")
      assertThat(vm.uiState.value.canSuggest).isFalse()
      // Reported when it first arrived, not again on every return.
      assertThat(analytics.countOf("task_suggestions_shown")).isEqualTo(0)
    }

  @Test
  fun leavingKeepsTheModelAnswerButClosesACuratedOnlyRun() =
    runTest(dispatcher) {
      val answer = SuggestTasksResult(
        suggestions = listOf(
          ai(
            "s1",
            "Tire rotation"
          )
        ) + curatedList.suggestions
      )
      serving(SuggestionRun.Ready(JOB, answer))
      val withAnswer = viewModel(pack, serverSource = true)
      advanceUntilIdle()
      withAnswer.onSkip()
      advanceUntilIdle()
      coVerify(exactly = 0) { suggestions.dismiss(any()) }

      serving(SuggestionRun.Ready(JOB, curatedList))
      val curatedOnly = viewModel(pack, serverSource = true)
      advanceUntilIdle()
      curatedOnly.onSkip()
      advanceUntilIdle()
      coVerify(exactly = 1) { suggestions.dismiss(JOB) }
    }

  @Test
  fun anEarlierEmptyOrFailedModelRunIsNotHeldSoTheListStartsAfresh() =
    runTest(dispatcher) {
      serving(
        SuggestionRun.Failed(
          JOB,
          AiErrorCode.PROVIDER_ERROR,
          curatedList
        )
      )

      viewModel(pack, serverSource = true)
      advanceUntilIdle()

      coVerify {
        suggestions.start(
          THING_ID,
          Screen.StarterPack.MODE_STARTER,
          curatedOnly = true
        )
      }
    }

  // Changing a suggestion before adding it (PRD R28, T18).

  @Test
  fun aCardOpensInTheFormAsAcceptingWouldWriteItAndComesBackEditedAndChecked() =
    runTest(dispatcher) {
      serving(SuggestionRun.Ready(JOB, curatedList))
      val mapped = MaintenanceTask(
        title = "Annual",
        origin = TaskOrigin(kind = TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED)
      )
      coEvery {
        suggestions.draftOf(
          THING_ID,
          curatedList.suggestions[0],
          any()
        )
      } returns mapped
      val vm = viewModel(pack, serverSource = true)
      advanceUntilIdle()

      val arg = vm.draftFor(0)!!
      assertThat(taskFromDraftArg(arg)).isEqualTo(mapped)

      val edited = mapped.copy(title = "Annual inspection (owner-assisted)")
      vm.onEdited(edited.toDraftArg())

      val card = vm.uiState.value.items[0]
      assertThat(card.edited).isEqualTo(edited)
      assertThat(card.selected).isTrue()
      // Editing again starts from the edit, not from the suggestion.
      assertThat(taskFromDraftArg(vm.draftFor(0)!!)).isEqualTo(edited)
    }

  @Test
  fun anAppPackCardCannotBeEdited() = runTest(dispatcher) {
    val packVm = viewModel(pack)
    advanceUntilIdle()

    assertThat(packVm.draftFor(0)).isNull()
  }

  @Test
  fun acceptingWritesTheEditAndCountsIt() = runTest(dispatcher) {
    val ready = SuggestionRun.Ready(JOB, curatedList)
    serving(ready)
    val mapped = MaintenanceTask(title = "Annual")
    coEvery { suggestions.draftOf(THING_ID, any(), any()) } returns mapped
    val chosen = slot<List<AcceptedSuggestion>>()
    coEvery { suggestions.accept(THING_ID, ready, capture(chosen)) } returns 1
    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()
    vm.draftFor(0)
    vm.onEdited(
      mapped.copy(title = "Annual, owner-assisted")
        .toDraftArg()
    )

    vm.onAccept()
    advanceUntilIdle()

    assertThat(chosen.captured.single().edited?.title).isEqualTo("Annual, owner-assisted")
    assertThat(
      analytics.paramsFor("task_suggestions_accepted")
        .single()
    ).containsEntry("edited_count", "1")
  }

  @Test
  fun anEditSurvivesTheModelsAnswerReplacingTheCuratedList() =
    runTest(dispatcher) {
      val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          any()
        )
      } returns AiStartResult.Started(JOB, joined = false)
      every { suggestions.observeRun(THING_ID) } returns runs
      coEvery {
        suggestions.draftOf(
          THING_ID,
          any(),
          any()
        )
      } returns MaintenanceTask(title = "Annual")
      val vm = viewModel(
        pack,
        mode = Screen.StarterPack.MODE_SUGGEST,
        serverSource = true
      )
      runs.emit(SuggestionRun.Working(JOB, "tailoring", null, curatedList))
      advanceUntilIdle()
      vm.draftFor(0)
      vm.onEdited(MaintenanceTask(title = "Annual, edited").toDraftArg())

      runs.emit(
        SuggestionRun.Ready(
          JOB,
          SuggestTasksResult(
            suggestions = listOf(
              ai(
                "s1",
                "Tire rotation"
              )
            ) + curatedList.suggestions
          )
        )
      )
      advanceUntilIdle()

      val annual =
        vm.uiState.value.items.single { it.suggestion.title == "Annual" }
      assertThat(annual.edited?.title).isEqualTo("Annual, edited")
      assertThat(annual.selected).isTrue()
    }

  private companion object {
    const val THING_ID = "thing-1"
    val JOB = AiJobId("job-1")
    val AI_JOB = AiJobId("job-2")
    val CURATED_JOB = AiJobId("job-0")
  }

  @Test
  fun theStarterRoutesAreUnchangedAndDefaultToStarterMode() {
    // Creation and the empty list build the same URL as before the mode existed.
    assertThat(Screen.StarterPack.createRoute(THING_ID)).isEqualTo("starter_pack/$THING_ID")
    assertThat(
      Screen.StarterPack.createRoute(
        THING_ID,
        Screen.StarterPack.MODE_SUGGEST
      )
    )
      .isEqualTo("starter_pack/$THING_ID?mode=suggest")
    assertThat(viewModel(pack).uiState.value.mode).isEqualTo(Screen.StarterPack.MODE_STARTER)
  }

  @Test
  fun theTaskListsSuggestOpensInSuggestMode() {
    assertThat(
      viewModel(
        pack,
        mode = Screen.StarterPack.MODE_SUGGEST
      ).uiState.value.mode
    )
      .isEqualTo(Screen.StarterPack.MODE_SUGGEST)
  }
}
