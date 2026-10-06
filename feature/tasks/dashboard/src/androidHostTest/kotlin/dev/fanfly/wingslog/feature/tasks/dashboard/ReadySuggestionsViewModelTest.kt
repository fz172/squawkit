package dev.fanfly.wingslog.feature.tasks.dashboard

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.rpc.suggesttasks.IdentifiedDocument
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.TaskOriginKind
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadySuggestionsViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val runs = MutableStateFlow<SuggestionRun>(SuggestionRun.Idle)
  private val suggestions = mockk<TaskSuggestionManager> {
    every { observeRun(THING) } returns runs
  }

  @Before
  fun setUp() = Dispatchers.setMain(dispatcher)

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun suggestion(origin: TaskOriginKind, tracked: Boolean = false) = TaskSuggestion(
    title = "t",
    origin_kind = origin,
    matches_existing_task_id = if (tracked) MaintenanceTaskId(value_ = "x") else null,
  )

  @Test
  fun aHeldModelAnswerIsReadyWithItsCountAndDocument() = runTest(dispatcher) {
    val vm = ReadySuggestionsViewModel(suggestions, THING)
    backgroundScope.launch { vm.ready.collect {} }

    runs.value = SuggestionRun.Ready(
      AiJobId("job"),
      SuggestTasksResult(
        suggestions = listOf(
          suggestion(TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT),
          suggestion(TaskOriginKind.TASK_ORIGIN_KIND_AI_THING),
          suggestion(TaskOriginKind.TASK_ORIGIN_KIND_AI_THING, tracked = true),
          suggestion(TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED),
        ),
        documents = listOf(IdentifiedDocument(name = "Rotax.pdf")),
      ),
    )
    advanceUntilIdle()

    assertThat(vm.ready.value).isEqualTo(ReadySuggestions(count = 2, document = "Rotax.pdf"))
  }

  @Test
  fun nothingWhileWorkingForACuratedListOrOnceClosed() = runTest(dispatcher) {
    val vm = ReadySuggestionsViewModel(suggestions, THING)
    backgroundScope.launch { vm.ready.collect {} }

    runs.value = SuggestionRun.Working(AiJobId("job"), "tailoring", null)
    advanceUntilIdle()
    assertThat(vm.ready.value).isNull()

    runs.value = SuggestionRun.Ready(
      AiJobId("job"),
      SuggestTasksResult(suggestions = listOf(suggestion(TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED))),
    )
    advanceUntilIdle()
    assertThat(vm.ready.value).isNull()

    runs.value = SuggestionRun.Idle
    advanceUntilIdle()
    assertThat(vm.ready.value).isNull()
  }

  private companion object {
    const val THING = "thing-1"
  }
}
