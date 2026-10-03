package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
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
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private val suggestions = mockk<TaskSuggestionManager>(relaxUnitFun = true)
  private val entry = mockk<TaskSuggestionEntry> { every { observe(THING_ID) } returns flowOf(SuggestEntry.Available) }

  private fun viewModel(
    starterTasks: List<StarterTask>,
    mode: String? = null,
    serverSource: Boolean = false,
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
      savedStateHandle = SavedStateHandle(
        buildMap {
          put(Screen.THING_ID, THING_ID)
          if (mode != null) put(Screen.SUGGESTIONS_MODE, mode)
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
    assertThat(vm.uiState.value.items.map { it.selected }).containsExactly(false, false, false)
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
  fun eachStarterTaskShowsAsTheCuratedSuggestionTheServerWouldSend() = runTest(dispatcher) {
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
      InspectionRule(meter_rule = MeterRule(meter_key = "odometer", interval = 5000f)),
    ).inOrder()
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

  private fun curated(id: String, title: String, preselect: Boolean = true) = TaskSuggestion(
    suggestion_id = SuggestionId(value_ = id),
    title = title,
    preselect = preselect,
    origin_kind = TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED,
  )

  private val curatedList = SuggestTasksResult(
    suggestions = listOf(curated("c0", "Annual"), curated("c1", "Oil change"), curated("c2", "ELT", preselect = false)),
  )

  private fun serving(vararg runs: SuggestionRun, started: AiStartResult = AiStartResult.Started(JOB, joined = false)) {
    coEvery { suggestions.start(THING_ID, any(), any()) } returns started
    every { suggestions.observeRun(THING_ID) } returns flowOf(*runs)
  }

  @Test
  fun theStarterModeAsksForTheCuratedListAndShowsItWithNothingChecked() = runTest(dispatcher) {
    serving(SuggestionRun.Idle, SuggestionRun.Ready(JOB, curatedList))

    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()

    coVerify { suggestions.start(THING_ID, Screen.StarterPack.MODE_STARTER, curatedOnly = true) }
    assertThat(vm.uiState.value.isLoading).isFalse()
    assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly("Annual", "Oil change", "ELT").inOrder()
    // The server's preselect is not read: the user checks what they need (PRD R27).
    assertThat(vm.uiState.value.items.map { it.selected }).containsExactly(false, false, false)
    assertThat(analytics.paramsFor("starter_tasks_offered").single()).containsEntry("task_count", "3")
  }

  @Test
  fun theSuggestModeStartsTheModelRun() = runTest(dispatcher) {
    serving(SuggestionRun.Idle, SuggestionRun.Working(JOB, null, null, curatedList))

    viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()

    coVerify { suggestions.start(THING_ID, Screen.StarterPack.MODE_SUGGEST, curatedOnly = false) }
  }

  @Test
  fun theModelsAnswerReplacesTheCuratedCardsAndKeepsTheUsersChoices() = runTest(dispatcher) {
    val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
    coEvery { suggestions.start(THING_ID, any(), any()) } returns AiStartResult.Started(JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    runs.emit(SuggestionRun.Working(JOB, "tailoring", null, curatedList))
    advanceUntilIdle()
    vm.onToggle(0) // check the annual

    val merged = SuggestTasksResult(
      suggestions = listOf(
        TaskSuggestion(suggestion_id = SuggestionId(value_ = "s1"), title = "Oil and filter", preselect = true),
        curated("c0", "Annual"),
        curated("c2", "ELT", preselect = false),
      ),
    )
    runs.emit(SuggestionRun.Ready(JOB, merged))
    advanceUntilIdle()

    assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly("Oil and filter", "Annual", "ELT").inOrder()
    // The annual stays checked; the model's new card starts unchecked, as every card does.
    assertThat(vm.uiState.value.items.map { it.selected }).containsExactly(false, true, false).inOrder()
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
  fun acceptingWritesTheTickedSuggestionsThroughTheManager() = runTest(dispatcher) {
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

    assertThat(chosen.captured.map { it.suggestion.title }).containsExactly("Annual", "Oil change").inOrder()
    coVerify(exactly = 0) { taskDataManager.addTask(any(), any()) }
    assertThat(vm.uiState.value.acceptedCount).isEqualTo(2)
    assertThat(analytics.paramsFor("starter_tasks_accepted").single()).containsEntry("task_count", "2")
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
  fun skippingClosesAFinishedRunButLeavesAWorkingOneRunning() = runTest(dispatcher) {
    serving(SuggestionRun.Ready(JOB, curatedList))
    val finished = viewModel(pack, serverSource = true)
    advanceUntilIdle()
    finished.onSkip()
    advanceUntilIdle()
    coVerify(exactly = 1) { suggestions.dismiss(JOB) }

    serving(SuggestionRun.Working(JOB, "tailoring", null, curatedList))
    val working = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()
    working.onSkip()
    advanceUntilIdle()
    coVerify(exactly = 1) { suggestions.dismiss(JOB) }
  }

  @Test
  fun theCuratedListOffersSuggestTasksWhereTheThingIsDescribedEnough() = runTest(dispatcher) {
    serving(SuggestionRun.Ready(JOB, curatedList))
    val described = viewModel(pack, serverSource = true)
    advanceUntilIdle()
    assertThat(described.uiState.value.canSuggest).isTrue()

    every { entry.observe(THING_ID) } returns flowOf(SuggestEntry.MissingIdentity(listOf("Model")))
    val thin = viewModel(pack, serverSource = true)
    advanceUntilIdle()
    assertThat(thin.uiState.value.canSuggest).isFalse()

    // Not on the app's pack, and not once the model run is the one shown.
    val appPack = viewModel(pack)
    advanceUntilIdle()
    assertThat(appPack.uiState.value.canSuggest).isFalse()
    serving(SuggestionRun.Working(JOB, null, null, curatedList))
    val suggesting = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()
    assertThat(suggesting.uiState.value.canSuggest).isFalse()
    assertThat(suggesting.uiState.value.isSuggesting).isTrue()
  }

  @Test
  fun suggestTasksStartsTheModelRunAndFollowsItFromTheCuratedCards() = runTest(dispatcher) {
    val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = true) } returns AiStartResult.Started(JOB, joined = false)
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false) } returns AiStartResult.Started(AI_JOB, joined = false)
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
    assertThat(vm.uiState.value.items.map { it.suggestion.title }).containsExactly("Annual", "Oil change", "ELT").inOrder()

    val merged = SuggestTasksResult(suggestions = listOf(TaskSuggestion(suggestion_id = SuggestionId(value_ = "s1"), title = "Spark plugs")) + curatedList.suggestions)
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
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false) } returns AiStartResult.Refused(AiErrorCode.UNAVAILABLE, null)

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
  fun aFailedModelRunKeepsTheCuratedCardsAndOffersTryAgain() = runTest(dispatcher) {
    val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false) } returns AiStartResult.Started(JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    runs.emit(SuggestionRun.Failed(JOB, AiErrorCode.PROVIDER_ERROR, curatedList))
    advanceUntilIdle()

    assertThat(vm.uiState.value.failure).isEqualTo(AiErrorCode.PROVIDER_ERROR)
    assertThat(vm.uiState.value.isDone).isFalse()
    assertThat(vm.uiState.value.items).hasSize(3)

    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false) } returns AiStartResult.Started(AI_JOB, joined = false)
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
    serving(SuggestionRun.Failed(JOB, AiErrorCode.STALE, result = null))

    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.isDone).isTrue()
    assertThat(vm.uiState.value.closingError).isEqualTo(AiErrorCode.STALE)
  }

  @Test
  fun theProgressLineFollowsTheRunsStage() = runTest(dispatcher) {
    val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
    coEvery { suggestions.start(THING_ID, any(), any()) } returns AiStartResult.Started(JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)

    runs.emit(SuggestionRun.Working(JOB, "reading_document", "Rotax MM.pdf", curatedList))
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
    val skipped = AiSkipped(AiErrorCode.DAILY_LIMIT, Instant.fromEpochMilliseconds(5_000))
    serving(SuggestionRun.Ready(JOB, curatedList, skipped))

    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.aiSkipped).isEqualTo(skipped)
    assertThat(vm.uiState.value.items).hasSize(3)
  }

  @Test
  fun anEmptyModelRunKeepsTheCuratedCardsAndOffersAddDetails() = runTest(dispatcher) {
    serving(SuggestionRun.Empty(JOB, curatedList))

    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.notEnough).isTrue()
    assertThat(vm.uiState.value.items).hasSize(3)

    vm.onAddDetails()
    advanceUntilIdle()
    coVerify { suggestions.dismiss(JOB) }
  }

  @Test
  fun anEmptyModelRunWithNoCuratedListStaysToSayNotEnough() = runTest(dispatcher) {
    // The custom template: the message is the whole screen (R21a).
    serving(SuggestionRun.Empty(JOB, result = null))

    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()

    assertThat(vm.uiState.value.isDone).isFalse()
    assertThat(vm.uiState.value.isLoading).isFalse()
    assertThat(vm.uiState.value.notEnough).isTrue()
  }

  @Test
  fun anAlreadyTrackedCardCannotBeChecked() = runTest(dispatcher) {
    val tracked = curated("c0", "Annual").copy(matches_existing_task_id = MaintenanceTaskId(value_ = "task-annual"))
    serving(SuggestionRun.Ready(JOB, SuggestTasksResult(suggestions = listOf(tracked, curated("c1", "Oil change")))))
    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()

    vm.onToggle(0)
    vm.onToggle(1)

    assertThat(vm.uiState.value.items.map { it.isAlreadyTracked }).containsExactly(true, false).inOrder()
    assertThat(vm.uiState.value.items.map { it.selected }).containsExactly(false, true).inOrder()
  }

  private fun ai(id: String, title: String) = TaskSuggestion(
    suggestion_id = SuggestionId(value_ = id),
    title = title,
    origin_kind = TaskOriginKind.TASK_ORIGIN_KIND_AI_THING,
  )

  @Test
  fun aModelRunReportsRequestedThenShownOnceWithTheSplit() = runTest(dispatcher) {
    val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
    coEvery { suggestions.start(THING_ID, any(), any()) } returns AiStartResult.Started(JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()

    val answer = SuggestTasksResult(suggestions = listOf(ai("s1", "Spark plugs")) + curatedList.suggestions)
    runs.emit(SuggestionRun.Ready(JOB, answer))
    advanceUntilIdle()
    runs.emit(SuggestionRun.Ready(JOB, answer)) // a listener re-delivery is not a second answer
    advanceUntilIdle()

    assertThat(analytics.paramsFor("task_suggestions_requested").single()).containsAtLeastEntriesIn(
      mapOf("source" to "suggest", "document_count" to "0"),
    )
    assertThat(analytics.paramsFor("task_suggestions_shown").single()).containsAtLeastEntriesIn(
      mapOf("curated_count" to "3", "ai_count" to "1", "latency_bucket" to "0-10s"),
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
  fun suggestMoreReportsItsOwnEntryPointAndARefusalReportsWhy() = runTest(dispatcher) {
    serving(SuggestionRun.Ready(JOB, curatedList))
    val vm = viewModel(pack, serverSource = true)
    advanceUntilIdle()
    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false) } returns AiStartResult.Refused(AiErrorCode.UNAVAILABLE, null)

    vm.onSuggest()
    advanceUntilIdle()

    assertThat(analytics.paramsFor("task_suggestions_failed").single()).containsEntry("reason", "unavailable")

    coEvery { suggestions.start(THING_ID, any(), curatedOnly = false) } returns AiStartResult.Started(AI_JOB, joined = false)
    vm.onSuggest()
    advanceUntilIdle()
    assertThat(analytics.paramsFor("task_suggestions_requested").single()).containsEntry("source", "suggest_more")
  }

  @Test
  fun aFailedRunIsReportedOnce() = runTest(dispatcher) {
    val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
    coEvery { suggestions.start(THING_ID, any(), any()) } returns AiStartResult.Started(JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()

    runs.emit(SuggestionRun.Failed(JOB, AiErrorCode.PROVIDER_ERROR, curatedList))
    advanceUntilIdle()
    runs.emit(SuggestionRun.Failed(JOB, AiErrorCode.PROVIDER_ERROR, curatedList))
    advanceUntilIdle()

    assertThat(analytics.paramsFor("task_suggestions_failed").single()).containsEntry("reason", "provider_error")
  }

  @Test
  fun acceptingReportsTheCuratedAndAiSplit() = runTest(dispatcher) {
    val answer = SuggestTasksResult(suggestions = listOf(ai("s1", "Spark plugs")) + curatedList.suggestions)
    val ready = SuggestionRun.Ready(JOB, answer)
    serving(ready)
    coEvery { suggestions.accept(THING_ID, ready, any()) } returns 2
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    advanceUntilIdle()
    vm.onToggle(0)
    vm.onToggle(1)

    vm.onAccept()
    advanceUntilIdle()

    assertThat(analytics.paramsFor("task_suggestions_accepted").single()).containsAtLeastEntriesIn(
      mapOf("curated_count" to "1", "ai_count" to "1"),
    )
  }

  // Returning to a held answer (PRD R19): leaving does not lose the model's cards.

  @Test
  fun reopeningTheListShowsTheHeldModelAnswerInsteadOfStartingOver() = runTest(dispatcher) {
    val answer = SuggestTasksResult(suggestions = listOf(ai("s1", "Tire rotation")) + curatedList.suggestions)
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
  fun leavingKeepsTheModelAnswerButClosesACuratedOnlyRun() = runTest(dispatcher) {
    val answer = SuggestTasksResult(suggestions = listOf(ai("s1", "Tire rotation")) + curatedList.suggestions)
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
  fun anEarlierEmptyOrFailedModelRunIsNotHeldSoTheListStartsAfresh() = runTest(dispatcher) {
    serving(SuggestionRun.Failed(JOB, AiErrorCode.PROVIDER_ERROR, curatedList))

    viewModel(pack, serverSource = true)
    advanceUntilIdle()

    coVerify { suggestions.start(THING_ID, Screen.StarterPack.MODE_STARTER, curatedOnly = true) }
  }

  // Changing a suggestion before adding it (PRD R28, T18).

  @Test
  fun aCardOpensInTheFormAsAcceptingWouldWriteItAndComesBackEditedAndChecked() = runTest(dispatcher) {
    serving(SuggestionRun.Ready(JOB, curatedList))
    val mapped = MaintenanceTask(title = "Annual", origin = TaskOrigin(kind = TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED))
    coEvery { suggestions.draftOf(THING_ID, curatedList.suggestions[0], any()) } returns mapped
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
  fun anAppPackCardAndAnAlreadyTrackedOneCannotBeEdited() = runTest(dispatcher) {
    val packVm = viewModel(pack)
    advanceUntilIdle()
    assertThat(packVm.draftFor(0)).isNull()

    val tracked = curated("c0", "Annual").copy(matches_existing_task_id = MaintenanceTaskId(value_ = "t"))
    serving(SuggestionRun.Ready(JOB, SuggestTasksResult(suggestions = listOf(tracked))))
    val serverVm = viewModel(pack, serverSource = true)
    advanceUntilIdle()
    assertThat(serverVm.draftFor(0)).isNull()
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
    vm.onEdited(mapped.copy(title = "Annual, owner-assisted").toDraftArg())

    vm.onAccept()
    advanceUntilIdle()

    assertThat(chosen.captured.single().edited?.title).isEqualTo("Annual, owner-assisted")
    assertThat(analytics.paramsFor("task_suggestions_accepted").single()).containsEntry("edited_count", "1")
  }

  @Test
  fun anEditSurvivesTheModelsAnswerReplacingTheCuratedList() = runTest(dispatcher) {
    val runs = MutableSharedFlow<SuggestionRun>(replay = 1)
    coEvery { suggestions.start(THING_ID, any(), any()) } returns AiStartResult.Started(JOB, joined = false)
    every { suggestions.observeRun(THING_ID) } returns runs
    coEvery { suggestions.draftOf(THING_ID, any(), any()) } returns MaintenanceTask(title = "Annual")
    val vm = viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST, serverSource = true)
    runs.emit(SuggestionRun.Working(JOB, "tailoring", null, curatedList))
    advanceUntilIdle()
    vm.draftFor(0)
    vm.onEdited(MaintenanceTask(title = "Annual, edited").toDraftArg())

    runs.emit(SuggestionRun.Ready(JOB, SuggestTasksResult(suggestions = listOf(ai("s1", "Tire rotation")) + curatedList.suggestions)))
    advanceUntilIdle()

    val annual = vm.uiState.value.items.single { it.suggestion.title == "Annual" }
    assertThat(annual.edited?.title).isEqualTo("Annual, edited")
    assertThat(annual.selected).isTrue()
  }

  private companion object {
    const val THING_ID = "thing-1"
    val JOB = AiJobId("job-1")
    val AI_JOB = AiJobId("job-2")
  }

  @Test
  fun theStarterRoutesAreUnchangedAndDefaultToStarterMode() {
    // Creation and the empty list build the same URL as before the mode existed.
    assertThat(Screen.StarterPack.createRoute(THING_ID)).isEqualTo("starter_pack/$THING_ID")
    assertThat(Screen.StarterPack.createRoute(THING_ID, Screen.StarterPack.MODE_SUGGEST))
      .isEqualTo("starter_pack/$THING_ID?mode=suggest")
    assertThat(viewModel(pack).uiState.value.mode).isEqualTo(Screen.StarterPack.MODE_STARTER)
  }

  @Test
  fun theTaskListsSuggestOpensInSuggestMode() {
    assertThat(viewModel(pack, mode = Screen.StarterPack.MODE_SUGGEST).uiState.value.mode)
      .isEqualTo(Screen.StarterPack.MODE_SUGGEST)
  }
}
