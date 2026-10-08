package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.id.SuggestionId
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.TaskOriginKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionSessionTest {

  private val manager = mockk<TaskSuggestionManager>(relaxUnitFun = true)
  private val listener = MutableSharedFlow<SuggestionRun>(replay = 1)
  private val session = SuggestionSession(manager, THING)

  private fun suggestion(id: String, kind: TaskOriginKind) = TaskSuggestion(
    suggestion_id = SuggestionId(value_ = id),
    origin_kind = kind,
  )

  private val curated = SuggestTasksResult(
    suggestions = listOf(suggestion("c0", TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED)),
    generation_version = "tasks-5",
  )
  private val answer = curated.copy(
    suggestions = curated.suggestions + suggestion("s1", TaskOriginKind.TASK_ORIGIN_KIND_AI_THING),
  )

  private fun starting(curatedOnly: Boolean, result: AiStartResult) {
    coEvery { manager.start(THING, any(), curatedOnly, any()) } returns result
  }

  /** Everything [SuggestionSession.runs] says while the test runs. */
  private fun TestScope.said(): List<SuggestionRun> {
    val said = mutableListOf<SuggestionRun>()
    backgroundScope.launch { session.runs.collect { said += it } }
    return said
  }

  @Test
  fun `comes back to a run that holds the model's answer, and follows it`() = runTest {
    val held = SuggestionRun.Ready(MODEL, answer)
    every { manager.observeRun(THING) } returns flowOf(held)

    assertThat(session.resume()).isEqualTo(held)
    val said = said()
    runCurrent()

    assertThat(said).containsExactly(held)
    assertThat(session.modelRequested).isTrue()
    coVerify(exactly = 0) { manager.start(any(), any(), any(), any()) }
  }

  @Test
  fun `comes back to a run still working`() = runTest {
    val working = SuggestionRun.Working(MODEL, SuggestionStage.TAILORING, null, curated)
    every { manager.observeRun(THING) } returns flowOf(working)

    assertThat(session.resume()).isEqualTo(working)
  }

  @Test
  fun `does not come back to a run with nothing of the model's`() = runTest {
    for (run in listOf(
      SuggestionRun.Idle,
      SuggestionRun.Ready(CURATED, curated),
      SuggestionRun.Empty(MODEL, curated),
      SuggestionRun.Failed(MODEL, AiErrorCode.STALE, curated),
    )) {
      every { manager.observeRun(THING) } returns flowOf(run)
      assertThat(session.resume()).isNull()
    }
    assertThat(session.modelRequested).isFalse()
  }

  @Test
  fun `says the followed job's runs and no other's`() = runTest {
    every { manager.observeRun(THING) } returns listener
    starting(curatedOnly = true, AiStartResult.Started(CURATED, joined = false))
    val said = said()

    // Until the listener catches up with the job just started, the newest it knows is older.
    listener.emit(SuggestionRun.Ready(AiJobId("older"), answer))
    session.startCurated(SuggestionEntryPoint.CURATED)
    runCurrent()
    assertThat(said).isEmpty()

    val ours = SuggestionRun.Ready(CURATED, curated)
    listener.emit(ours)
    runCurrent()

    assertThat(said).containsExactly(ours)
    assertThat(session.run).isEqualTo(ours)
    assertThat(session.modelRequested).isFalse()
  }

  @Test
  fun `a run the listener says before its start returns is said once it is followed`() = runTest {
    every { manager.observeRun(THING) } returns listener
    starting(curatedOnly = true, AiStartResult.Started(CURATED, joined = false))
    val said = said()
    session.startCurated(SuggestionEntryPoint.CURATED)
    listener.emit(SuggestionRun.Ready(CURATED, curated))
    runCurrent()

    // Written already finished, so the listener says it once, while start is on its way back.
    val born = SuggestionRun.Ready(MODEL, curated)
    coEvery { manager.start(THING, any(), curatedOnly = false, any()) } coAnswers {
      listener.emit(born)
      runCurrent()
      AiStartResult.Started(MODEL, joined = false)
    }
    session.startModel(SuggestionEntryPoint.CURATED)
    runCurrent()

    assertThat(said.last()).isEqualTo(born)
    assertThat(said.count { it == born }).isEqualTo(1)
  }

  @Test
  fun `a model run closes the run it replaces, before it is followed`() = runTest {
    every { manager.observeRun(THING) } returns listener
    starting(curatedOnly = true, AiStartResult.Started(CURATED, joined = false))
    starting(curatedOnly = false, AiStartResult.Started(MODEL, joined = false))
    said()
    session.startCurated(SuggestionEntryPoint.CURATED)
    listener.emit(SuggestionRun.Ready(CURATED, curated))
    runCurrent()

    session.startModel(SuggestionEntryPoint.CURATED)

    coVerifyOrder {
      manager.start(THING, SuggestionEntryPoint.CURATED, curatedOnly = false, emptyList())
      manager.dismiss(CURATED)
    }
    assertThat(session.modelRequested).isTrue()
  }

  @Test
  fun `a refused model run leaves the run on screen alone`() = runTest {
    every { manager.observeRun(THING) } returns listener
    starting(curatedOnly = true, AiStartResult.Started(CURATED, joined = false))
    starting(curatedOnly = false, AiStartResult.Refused(AiErrorCode.DAILY_LIMIT, null))
    val said = said()
    session.startCurated(SuggestionEntryPoint.CURATED)
    val ours = SuggestionRun.Ready(CURATED, curated)
    listener.emit(ours)
    runCurrent()

    session.startModel(SuggestionEntryPoint.CURATED)
    runCurrent()

    coVerify(exactly = 0) { manager.dismiss(any()) }
    assertThat(session.modelRequested).isFalse()
    assertThat(said).containsExactly(ours)
  }

  @Test
  fun `leaving closes a run with nothing of the model's, and keeps one that has`() = runTest {
    every { manager.observeRun(THING) } returns listener
    starting(curatedOnly = true, AiStartResult.Started(CURATED, joined = false))
    starting(curatedOnly = false, AiStartResult.Started(MODEL, joined = false))
    said()
    session.startCurated(SuggestionEntryPoint.CURATED)
    listener.emit(SuggestionRun.Ready(CURATED, curated))
    runCurrent()

    session.leave()
    coVerify(exactly = 1) { manager.dismiss(CURATED) }

    session.startModel(SuggestionEntryPoint.CURATED)
    listener.emit(SuggestionRun.Ready(MODEL, answer))
    runCurrent()

    session.leave()
    coVerify(exactly = 0) { manager.dismiss(MODEL) }
  }

  @Test
  fun `accepting writes from the followed run, whatever its state`() = runTest {
    every { manager.observeRun(THING) } returns listener
    starting(curatedOnly = false, AiStartResult.Started(MODEL, joined = false))
    coEvery { manager.accept(THING, MODEL, "tasks-5", any()) } returns emptyList()
    said()
    // Nothing to accept from before a run is in.
    assertThat(session.accept(emptyList())).isEmpty()

    session.startModel(SuggestionEntryPoint.ADD)
    listener.emit(SuggestionRun.Working(MODEL, SuggestionStage.TAILORING, null, curated))
    runCurrent()
    val chosen = listOf(AcceptedSuggestion(curated.suggestions.single()))
    session.accept(chosen)

    coVerify(exactly = 1) { manager.accept(THING, MODEL, "tasks-5", chosen) }
  }

  private companion object {
    const val THING = "thing-1"
    val CURATED = AiJobId("job-curated")
    val MODEL = AiJobId("job-model")
  }
}
