package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.MaintenanceTask
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
   *
   * [curatedOnly] asks for the Thing's curated suggestions alone, with no model call: what creation
   * and the empty task list show before the user asks for AI (design §9.1). Such a run ends at once
   * and never uses up the day.
   */
  suspend fun start(thingId: String, entryPoint: String, curatedOnly: Boolean = false): AiStartResult

  /** The caller's latest run on [thingId]; [SuggestionRun.Idle] when there is none. */
  fun observeRun(thingId: String): Flow<SuggestionRun>

  /**
   * [suggestion] as the task [accept] would write, for the task form to start from when the user
   * changes it first (PRD R28). The form hands back the edited task, which [accept] then writes as
   * it is.
   */
  suspend fun draftOf(thingId: String, suggestion: TaskSuggestion, generationVersion: String): MaintenanceTask

  /**
   * Writes [chosen] as tasks, one write each like the starter pack (a failure drops only its own
   * card), then closes the run. Returns how many were written.
   */
  suspend fun accept(thingId: String, run: SuggestionRun.Ready, chosen: List<AcceptedSuggestion>): Int

  /** Closes the run without writing anything. */
  suspend fun dismiss(jobId: AiJobId)
}

/**
 * A run as the screen sees it. Every state but [Idle] can hold suggestions: a task run carries the
 * Thing's curated suggestions from the moment it starts, keeps them when the model has nothing or
 * fails, and ends with the model's merged in (design §6.8). Null where there are none, such as the
 * custom template's.
 */
sealed interface SuggestionRun {
  data object Idle : SuggestionRun

  /**
   * [stage] is the pipeline's progress key ("recalling_schedule", …), null before it reports.
   * [result] is the curated suggestions, shown while the model works.
   */
  data class Working(
    val jobId: AiJobId,
    val stage: String?,
    val stageArg: String?,
    val result: SuggestTasksResult? = null,
  ) : SuggestionRun

  /**
   * [aiSkipped] is set when the run returned its curated suggestions alone because the model was
   * refused (the daily limit, a spending ceiling, the kill switch), and says when it is back.
   */
  data class Ready(
    val jobId: AiJobId,
    val result: SuggestTasksResult,
    val aiSkipped: AiSkipped? = null,
  ) : SuggestionRun

  /**
   * The model had nothing confident to say (PRD R21a). It does not use up the day. [result] holds
   * the curated suggestions, if the template has any.
   */
  data class Empty(val jobId: AiJobId, val result: SuggestTasksResult? = null) : SuggestionRun

  /** [result] holds the curated suggestions the run started with, if any. */
  data class Failed(
    val jobId: AiJobId,
    val reason: AiErrorCode,
    val result: SuggestTasksResult? = null,
  ) : SuggestionRun
}
