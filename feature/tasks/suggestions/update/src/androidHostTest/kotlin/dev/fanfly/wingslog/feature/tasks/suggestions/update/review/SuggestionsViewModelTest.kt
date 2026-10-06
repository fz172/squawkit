package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.nav.SuggestionsMode
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.attachmentsFromDocumentsArg
import dev.fanfly.wingslog.feature.attachment.model.toDocumentsArg
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.model.taskFromDraftArg
import dev.fanfly.wingslog.feature.tasks.model.toDraftArg
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.AddedBatch
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.RecentlyAddedTasks
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.id.AttachmentId
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.id.SuggestionId
import dev.fanfly.wingslog.rpc.suggesttasks.IdentifiedDocument
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.MeterRule
import dev.fanfly.wingslog.task.TaskOrigin
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.task.TimeRule
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
 * when cards are on screen — and not for a Thing with nothing to offer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val analytics = RecordingAnalyticsManager()
  private val fleetManager = mockk<FleetManager>()
  private val taskDataManager = mockk<TaskDataManager>()
  private val written = mutableListOf<MaintenanceTask>()

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
    coEvery { suggestions.eligibility(THING_ID, any()) } returns AiEligibility(
      true,
      null,
      false,
      null
    )
    coEvery { suggestions.isOwner(THING_ID) } returns true
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private val suggestions = mockk<TaskSuggestionManager>(relaxUnitFun = true)
  private val attachments = mockk<AttachmentManager>(relaxUnitFun = true)
  private val recentlyAdded = RecentlyAddedTasks()
  private val entry = mockk<TaskSuggestionEntry> {
    every { observe(THING_ID) } returns flowOf(SuggestEntry.Available)
  }

  private fun viewModel(
    mode: SuggestionsMode? = null,
    picked: List<Attachment> = emptyList(),
  ): SuggestionsViewModel {
    val thing = Thing(
      id = THING_ID,
      template = ThingTemplate(
        id = "home",
        version = 7
      ),
    )
    every { fleetManager.loadThing(THING_ID) } returns flowOf(thing)
    return SuggestionsViewModel(
      fleetManager = fleetManager,
      taskDataManager = taskDataManager,
      templateRegistry = BakedInTemplateRegistry(appVersionCode = 1),
      analytics = analytics,
      suggestionManager = suggestions,
      suggestEntry = entry,
      attachmentManager = attachments,
      recentlyAdded = recentlyAdded,
      savedStateHandle = SavedStateHandle(
        buildMap {
          put(Screen.THING_ID, THING_ID)
          if (mode != null) put(Screen.SUGGESTIONS_MODE, mode.wire)
          if (picked.isNotEmpty()) put(
            Screen.SUGGESTIONS_DOCUMENT,
            picked.toDocumentsArg()
          )
        },
      ),
    )
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
   * The curated mode on [curated], and its AI button: the model run is [JOB], and [runs] carries
   * what the test emits next.
   */
  private fun TestScope.curatedModelRun(
    curated: SuggestTasksResult = curatedList,
    documentsAllowed: Boolean = false,
  ): SuggestionsViewModel {
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = true) } returns
      AiStartResult.Started(CURATED_JOB, joined = false)
    coEvery {
      suggestions.start(
        THING_ID,
        any(),
        curatedOnly = false,
        documents = any()
      )
    } returns
      AiStartResult.Started(JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    coEvery { suggestions.eligibility(THING_ID, any()) } returns
      AiEligibility(true, null, documentsAllowed, null)
    coEvery { suggestions.isOwner(THING_ID) } returns true
    val vm = viewModel()
    runs.tryEmit(SuggestionRun.Ready(CURATED_JOB, curated))
    advanceUntilIdle()
    vm.onSuggest()
    advanceUntilIdle()
    return vm
  }

  @Test
  fun theCuratedModeAsksForTheCuratedListAndShowsItWithNothingChecked() =
    runTest(dispatcher) {
      serving(SuggestionRun.Idle, SuggestionRun.Ready(JOB, curatedList))

      val vm = viewModel()
      advanceUntilIdle()

      coVerify {
        suggestions.start(
          THING_ID,
          SuggestionsMode.CURATED.wire,
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

  private val manuals = listOf(
    Attachment(id = "blob-mm", name = "MM.pdf", mime_type = "application/pdf"),
    Attachment(id = "blob-lm", name = "LM.pdf", mime_type = "application/pdf"),
  )

  @Test
  fun theAddModeStartsTheModelRunAtOnceWithThePickedDocuments() =
    runTest(dispatcher) {
      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          curatedOnly = false,
          documents = any()
        )
      } returns
        AiStartResult.Started(JOB, joined = false)
      every { suggestions.observeRun(THING_ID) } returns runs

      val vm = viewModel(mode = SuggestionsMode.ADD, picked = manuals)
      advanceUntilIdle()
      runs.emit(SuggestionRun.Working(JOB, "tailoring", null, curatedList))
      advanceUntilIdle()

      coVerify {
        suggestions.start(
          THING_ID,
          "add",
          curatedOnly = false,
          documents = manuals
        )
      }
      coVerify(exactly = 0) {
        suggestions.start(
          THING_ID,
          any(),
          curatedOnly = true,
          any()
        )
      }
      // The curated list the run starts with is up, and can be picked from while it works.
      assertThat(vm.uiState.value.items).hasSize(3)
      assertThat(vm.uiState.value.isSuggesting).isTrue()
      assertThat(vm.uiState.value.canSuggest).isFalse()
      // The run owns them now.
      coVerify(exactly = 0) { attachments.release(any(), any()) }
      assertThat(
        analytics.paramsFor("task_suggestions_requested")
          .single()
      )
        .containsAtLeastEntriesIn(
          mapOf(
            "source" to "add",
            "document_count" to "2"
          )
        )
    }

  @Test
  fun anIntervalChangedInPlaceEditsTheDraftAndChecksTheRow() =
    runTest(dispatcher) {
      val oil = curated("c1", "Oil change").copy(
        rules = listOf(
          InspectionRule(
            meter_rule = MeterRule(
              meter_key = "tach",
              interval = 50f
            )
          ),
          InspectionRule(time_rule = TimeRule(interval_years = 1)),
        ),
      )
      serving(
        SuggestionRun.Idle,
        SuggestionRun.Ready(
          JOB,
          SuggestTasksResult(suggestions = listOf(oil))
        )
      )
      coEvery { suggestions.draftOf(THING_ID, oil, any()) } returns
        MaintenanceTask(title = "Oil change", rules = oil.rules)
      val vm = viewModel()
      advanceUntilIdle()

      vm.onMeterIntervalChange(0, 25f)
      vm.onMonthsChange(0, 6)
      advanceUntilIdle()

      val item = vm.uiState.value.items.single()
      assertThat(item.selected).isTrue()
      // Both edits landed, the second on top of the first.
      assertThat(item.edited?.rules).containsExactly(
        InspectionRule(
          meter_rule = MeterRule(
            meter_key = "tach",
            interval = 25f
          )
        ),
        InspectionRule(
          time_rule = TimeRule(
            interval_months = 6,
            interval_years = 0
          )
        ),
      )
        .inOrder()
      coVerify(exactly = 1) { suggestions.draftOf(THING_ID, oil, any()) }

      // Nothing to change on a rule it does not have, and no zero interval.
      vm.onDaysChange(0, 30)
      vm.onMeterIntervalChange(0, 0f)
      advanceUntilIdle()
      assertThat(vm.uiState.value.items.single().edited?.rules?.first()?.meter_rule?.interval)
        .isEqualTo(25f)
    }

  @Test
  fun selectAllPicksTheWholeSectionThenClearEmptiesIt() = runTest(dispatcher) {
    val engine = curatedList.copy(
      suggestions = curatedList.suggestions + listOf(
        curated("e0", "Spark plugs").copy(component_slot_key = "engine"),
        curated("e1", "Carb sync").copy(component_slot_key = "engine"),
      ),
    )
    serving(SuggestionRun.Idle, SuggestionRun.Ready(JOB, engine))
    val vm = viewModel()
    advanceUntilIdle()
    vm.onToggle(3)

    vm.onToggleGroup(listOf(3, 4))
    assertThat(vm.uiState.value.items.map { it.selected })
      .containsExactly(false, false, false, true, true)
      .inOrder()

    vm.onToggleGroup(listOf(3, 4))
    assertThat(vm.uiState.value.items.map { it.selected })
      .containsExactly(false, false, false, false, false)
      .inOrder()

    // The Thing's own section is the empty slot.
    vm.onToggleGroup(listOf(0, 1, 2))
    assertThat(vm.uiState.value.items.map { it.selected })
      .containsExactly(true, true, true, false, false)
      .inOrder()
  }

  @Test
  fun theRowsStillToComeAreTheManualsWhenTheRunReadsThem() =
    runTest(dispatcher) {
      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          curatedOnly = false,
          documents = any()
        )
      } returns
        AiStartResult.Started(JOB, joined = false)
      every { suggestions.observeRun(THING_ID) } returns runs

      val withManuals = viewModel(mode = SuggestionsMode.ADD, picked = manuals)
      advanceUntilIdle()
      runs.emit(SuggestionRun.Working(JOB, "tailoring", null, curatedList))
      advanceUntilIdle()
      assertThat(withManuals.uiState.value.readsDocuments).isTrue()

      val without = viewModel(mode = SuggestionsMode.ADD)
      advanceUntilIdle()
      assertThat(without.uiState.value.readsDocuments).isFalse()
    }

  @Test
  fun aRunOpenedAgainReadsDocumentsWhenAStageNamesOne() = runTest(dispatcher) {
    serving(
      SuggestionRun.Working(
        AI_JOB,
        "reading_document",
        "MM.pdf",
        curatedList
      )
    )

    val vm = viewModel()
    advanceUntilIdle()

    assertThat(vm.uiState.value.isSuggesting).isTrue()
    assertThat(vm.uiState.value.readsDocuments).isTrue()
  }

  @Test
  fun aRefusedAddRunLetsGoOfTheFilesAndFallsBackToTheCuratedList() =
    runTest(dispatcher) {
      coEvery {
        suggestions.start(
          THING_ID,
          any(),
          curatedOnly = false,
          documents = any()
        )
      } returns
        AiStartResult.Refused(AiErrorCode.DOCUMENT_MISSING, null)
      coEvery { suggestions.start(THING_ID, any(), curatedOnly = true) } returns
        AiStartResult.Started(CURATED_JOB, joined = false)
      every { suggestions.observeRun(THING_ID) } returns runs

      val vm = viewModel(mode = SuggestionsMode.ADD, picked = manuals)
      advanceUntilIdle()
      runs.emit(SuggestionRun.Ready(CURATED_JOB, curatedList))
      advanceUntilIdle()

      manuals.forEach { coVerify { attachments.release(it, null) } }
      assertThat(vm.uiState.value.notice).isEqualTo(AiErrorCode.DOCUMENT_MISSING)
      assertThat(vm.uiState.value.isSuggesting).isFalse()
      assertThat(vm.uiState.value.items).hasSize(3)
      // The AI button is back, to try again.
      assertThat(vm.uiState.value.canSuggest).isTrue()
    }

  @Test
  fun anAnswerAlreadyHeldIsShownAndThePickedFilesAreLetGo() =
    runTest(dispatcher) {
      serving(SuggestionRun.Working(AI_JOB, "tailoring", null, curatedList))

      viewModel(mode = SuggestionsMode.ADD, picked = manuals)
      advanceUntilIdle()

      coVerify(exactly = 0) { suggestions.start(any(), any(), any(), any()) }
      manuals.forEach { coVerify { attachments.release(it, null) } }
    }

  @Test
  fun theAddRouteCarriesThePickedDocuments() {
    val arg = manuals.toDocumentsArg()

    assertThat(
      Screen.Suggestions.createRoute(
        THING_ID,
        SuggestionsMode.ADD,
        arg
      )
    )
      .isEqualTo("suggestions/$THING_ID?mode=add&document=$arg")
    assertThat(attachmentsFromDocumentsArg(arg)).isEqualTo(manuals)
    assertThat(attachmentsFromDocumentsArg("")).isEmpty()
    // The curated mode carries no documents.
    assertThat(
      Screen.Suggestions.createRoute(
        THING_ID,
        SuggestionsMode.CURATED,
        arg
      )
    )
      .isEqualTo("suggestions/$THING_ID")
  }

  @Test
  fun theReviewCarriesWhatTheRunMadeOfEachDocument() = runTest(dispatcher) {
    val vm = curatedModelRun()
    val manual = IdentifiedDocument(
      blob_id = AttachmentId(value_ = "blob-1"),
      name = "915iS_MM.pdf",
      title = "Rotax 915 iS MM",
      matches_thing = true,
    )
    val stray =
      IdentifiedDocument(name = "Airmaster_manual.pdf", matches_thing = false)

    runs.emit(
      SuggestionRun.Ready(
        JOB,
        curatedList.copy(
          documents = listOf(
            manual,
            stray
          )
        )
      )
    )
    advanceUntilIdle()

    assertThat(vm.uiState.value.documents).containsExactly(manual, stray)
      .inOrder()
  }

  @Test
  fun aDocumentIsNamedByTheTitleItCarriesElseItsFileName() {
    assertThat(
      IdentifiedDocument(
        name = "a.pdf",
        title = "Rotax MM"
      ).displayTitle()
    ).isEqualTo("Rotax MM")
    assertThat(IdentifiedDocument(name = "a.pdf").displayTitle()).isEqualTo("a.pdf")
  }

  @Test
  fun theCuratedModeAlsoSaysWhenAiIsBack() = runTest(dispatcher) {
    val back = Instant.fromEpochMilliseconds(9_000)
    coEvery { suggestions.eligibility(THING_ID, any()) } returns
      AiEligibility(false, AiErrorCode.DAILY_LIMIT, false, back)
    serving(SuggestionRun.Ready(JOB, curatedList))
    val vm = viewModel()
    advanceUntilIdle()

    assertThat(vm.uiState.value.canSuggest).isFalse()
    assertThat(vm.uiState.value.aiUnavailable?.reason).isEqualTo(AiErrorCode.DAILY_LIMIT)
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
      val vm = viewModel()
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

    val vm = viewModel()
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
      coEvery {
        suggestions.accept(
          THING_ID,
          ready,
          capture(chosen)
        )
      } returns listOf("t1", "t2")
      val vm = viewModel()
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
      // The task tab says so, with *Undo*.
      assertThat(recentlyAdded.batch.value).isEqualTo(
        AddedBatch(
          THING_ID,
          listOf(
            "t1",
            "t2"
          )
        )
      )
      assertThat(
        analytics.paramsFor("starter_tasks_accepted")
          .single()
      ).containsEntry("task_count", "2")
    }

  @Test
  fun aRefusedStartClosesTheScreenWithNothingOffered() = runTest(dispatcher) {
    serving(started = AiStartResult.Refused(AiErrorCode.UNAVAILABLE, null))

    val vm = viewModel()
    advanceUntilIdle()

    assertThat(vm.uiState.value.isDone).isTrue()
    // The task tab says why: "No internet connection" (PRD R51).
    assertThat(vm.uiState.value.closingError).isEqualTo(AiErrorCode.UNAVAILABLE)
    assertThat(analytics.countOf("starter_tasks_offered")).isEqualTo(0)
  }

  @Test
  fun aTemplateWithNoCuratedListIsNotAnOffer() = runTest(dispatcher) {
    serving(SuggestionRun.Empty(JOB, result = null))

    val vm = viewModel()
    advanceUntilIdle()

    assertThat(vm.uiState.value.isDone).isTrue()
    assertThat(analytics.countOf("starter_tasks_offered")).isEqualTo(0)
  }

  @Test
  fun skippingClosesAFinishedRunButLeavesAWorkingOneRunning() =
    runTest(dispatcher) {
      serving(SuggestionRun.Ready(JOB, curatedList))
      val finished = viewModel()
      advanceUntilIdle()
      finished.onSkip()
      advanceUntilIdle()
      coVerify(exactly = 1) { suggestions.dismiss(JOB) }

      serving(SuggestionRun.Working(JOB, "tailoring", null, curatedList))
      val working = viewModel()
      advanceUntilIdle()
      working.onSkip()
      advanceUntilIdle()
      coVerify(exactly = 1) { suggestions.dismiss(JOB) }
    }

  @Test
  fun theCuratedListOffersSuggestTasksWhereTheThingIsDescribedEnough() =
    runTest(dispatcher) {
      serving(SuggestionRun.Ready(JOB, curatedList))
      val described = viewModel()
      advanceUntilIdle()
      assertThat(described.uiState.value.canSuggest).isTrue()

      every { entry.observe(THING_ID) } returns flowOf(
        SuggestEntry.MissingIdentity(
          listOf("Model")
        )
      )
      val thin = viewModel()
      advanceUntilIdle()
      assertThat(thin.uiState.value.canSuggest).isFalse()

      // Not once the model run is the one shown.
      serving(SuggestionRun.Working(JOB, null, null, curatedList))
      val suggesting = viewModel()
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
      val vm = viewModel()
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
    val vm = viewModel()
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
      val vm = curatedModelRun()
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
    val vm = curatedModelRun(curated = SuggestTasksResult())
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
    val vm = viewModel()

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
    val vm = curatedModelRun()
    runs.emit(SuggestionRun.Ready(JOB, curatedList, skipped))
    advanceUntilIdle()

    assertThat(vm.uiState.value.aiSkipped).isEqualTo(skipped)
    assertThat(vm.uiState.value.items).hasSize(3)
  }

  @Test
  fun anEmptyModelRunKeepsTheCuratedCardsAndOffersAddDetails() =
    runTest(dispatcher) {
      val vm = curatedModelRun()
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
      val vm = curatedModelRun(curated = SuggestTasksResult())
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

    val vm = viewModel()
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

    val vm = viewModel()
    advanceUntilIdle()

    assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly(
      "Annual",
      "ELT"
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
      curatedModelRun()

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
        mapOf("source" to "suggest_more", "document_count" to "0"),
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

    viewModel()
    advanceUntilIdle()

    assertThat(analytics.countOf("task_suggestions_requested")).isEqualTo(0)
    assertThat(analytics.countOf("task_suggestions_shown")).isEqualTo(0)
  }

  @Test
  fun suggestMoreReportsItsOwnEntryPointAndARefusalReportsWhy() =
    runTest(dispatcher) {
      serving(SuggestionRun.Ready(JOB, curatedList))
      val vm = viewModel()
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
    curatedModelRun()

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
    coEvery { suggestions.accept(THING_ID, ready, any()) } returns listOf(
      "t1",
      "t2"
    )
    val vm = viewModel()
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

      val vm = viewModel()
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
      val withAnswer = viewModel()
      advanceUntilIdle()
      withAnswer.onSkip()
      advanceUntilIdle()
      coVerify(exactly = 0) { suggestions.dismiss(any()) }

      serving(SuggestionRun.Ready(JOB, curatedList))
      val curatedOnly = viewModel()
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

      viewModel()
      advanceUntilIdle()

      coVerify {
        suggestions.start(
          THING_ID,
          SuggestionsMode.CURATED.wire,
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
      val vm = viewModel()
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
  fun acceptingWritesTheEditAndCountsIt() = runTest(dispatcher) {
    val ready = SuggestionRun.Ready(JOB, curatedList)
    serving(ready)
    val mapped = MaintenanceTask(title = "Annual")
    coEvery { suggestions.draftOf(THING_ID, any(), any()) } returns mapped
    val chosen = slot<List<AcceptedSuggestion>>()
    coEvery {
      suggestions.accept(
        THING_ID,
        ready,
        capture(chosen)
      )
    } returns listOf("t1")
    val vm = viewModel()
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
      val vm = viewModel()
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
  fun theCuratedRouteIsTheBareRouteAndTheDefault() {
    // The empty list and a finished run's push build the same URL as before the mode existed.
    assertThat(Screen.Suggestions.createRoute(THING_ID)).isEqualTo("suggestions/$THING_ID")
    assertThat(Screen.Suggestions.createRoute(THING_ID, SuggestionsMode.ADD))
      .isEqualTo("suggestions/$THING_ID?mode=add")
    assertThat(viewModel().uiState.value.mode).isEqualTo(SuggestionsMode.CURATED)
  }

  @Test
  fun theModeReadsTheRoutesWordAndAnythingElseIsTheCuratedMode() {
    assertThat(SuggestionsMode.fromWire("add")).isEqualTo(SuggestionsMode.ADD)
    // An old, removed or mistyped link still opens the list.
    assertThat(SuggestionsMode.fromWire(null)).isEqualTo(SuggestionsMode.CURATED)
    assertThat(SuggestionsMode.fromWire("suggest")).isEqualTo(SuggestionsMode.CURATED)
    assertThat(SuggestionsMode.fromWire("document")).isEqualTo(SuggestionsMode.CURATED)
    assertThat(SuggestionsMode.fromWire("Add")).isEqualTo(SuggestionsMode.CURATED)
  }
}
