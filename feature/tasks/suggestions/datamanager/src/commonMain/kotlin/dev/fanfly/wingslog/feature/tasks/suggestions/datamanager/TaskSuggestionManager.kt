package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import kotlinx.coroutines.flow.Flow

/**
 * AI task suggestions for one Thing (docs/ai/task_population_design.md §7.3): ask whether a run is
 * open, start one, follow it, preview a suggestion's first due date, and accept or dismiss the
 * result. Documents arrive with T22; a run here describes the Thing alone (PRD R9).
 */
interface TaskSuggestionManager {

  /** Whether an entry point can offer a run right now, and why not. Never throws. */
  suspend fun eligibility(thingId: String): AiEligibility

  /**
   * Starts a run, or joins the caller's own run in flight. Waits for the Thing to reach the server
   * first, because the server refuses one it cannot find (§5.3). [entryPoint] is for analytics.
   */
  suspend fun start(thingId: String, entryPoint: String): AiStartResult

  /** The caller's latest run on [thingId]; [SuggestionRun.Idle] when there is none. */
  fun observeRun(thingId: String): Flow<SuggestionRun>

  /**
   * When [suggestion] would first fall due if accepted now, from the due engine itself (R29): the
   * suggestion is mapped exactly as [accept] maps it, so the preview cannot disagree with the task.
   * No log counts as its last compliance, so the schedule runs from now.
   */
  suspend fun firstDue(thingId: String, suggestion: TaskSuggestion): DueMetadata

  /**
   * Writes [chosen] as tasks, one write each like the starter pack (a failure drops only its own
   * card), then closes the run. Returns how many were written.
   */
  suspend fun accept(thingId: String, run: SuggestionRun.Ready, chosen: List<AcceptedSuggestion>): Int

  /** Closes the run without writing anything. */
  suspend fun dismiss(jobId: AiJobId)
}

sealed interface SuggestionRun {
  data object Idle : SuggestionRun

  /** [stage] is the pipeline's progress key ("recalling_schedule", …), null before it reports. */
  data class Working(val jobId: AiJobId, val stage: String?, val stageArg: String?) : SuggestionRun

  data class Ready(val jobId: AiJobId, val result: SuggestTasksResult) : SuggestionRun

  /** The run had nothing confident to say (PRD R21a). It does not use up the day. */
  data class Empty(val jobId: AiJobId) : SuggestionRun

  data class Failed(val jobId: AiJobId, val reason: AiErrorCode) : SuggestionRun
}
