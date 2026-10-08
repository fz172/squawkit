package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.feature.tasks.suggestions.model.WrittenSuggestion
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.thing.Attachment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * One open suggestions screen's hold on a Thing's runs (design §9.1, §9.2): which run it shows,
 * and what becomes of that run when another starts or the screen is left.
 *
 * A Thing can have several jobs at once (a curated-only one, then the model's), and the listener
 * only says the newest, so the screen *follows* one job: the answer it came back to ([resume]),
 * or the one it started last ([startCurated], [startModel]). [runs] says that job's states and no
 * other's.
 *
 * Held by one screen and used from its one dispatcher; not thread-safe.
 */
class SuggestionSession(
  private val manager: TaskSuggestionManager,
  private val thingId: String,
) {

  /** The job [runs] follows: the one this screen came back to, or started last. */
  private val followed = MutableStateFlow<AiJobId?>(null)

  /** The followed run as [runs] last said it; null until the first is in. */
  var run: SuggestionRun? = null
    private set

  /**
   * The user asked for the model, here or on the Add Tasks sheet, rather than the curated list
   * alone: only then can a run come back with nothing to say, and only its answer is reported.
   */
  var modelRequested: Boolean = false
    private set

  /**
   * The followed job's run, each time the listener says it, for as long as it is collected.
   *
   * The listener can hear of a job before the call that started it returns, and a job that is
   * written already finished (the model refused, the curated list alone) is never said twice. So
   * the newest run heard is kept, and said once the job it belongs to is the one followed.
   */
  val runs: Flow<SuggestionRun> = channelFlow {
    var latest: SuggestionRun? = null
    var said: SuggestionRun? = null
    suspend fun sayIfFollowed() {
      val current = latest ?: return
      if (current.jobId == null || current.jobId != followed.value || current === said) return
      said = current
      run = current
      send(current)
    }
    launch {
      manager.observeRun(thingId)
        .collect {
          latest = it
          sayIfFollowed()
        }
    }
    launch { followed.collect { sayIfFollowed() } }
  }
    // Never holds the listener up: every state is said, in order.
    .buffer(Channel.UNLIMITED)

  /**
   * The run this screen comes back to (PRD R19): the model's answer is held for a day until the
   * user acts on it, so opening the list again shows it rather than starting over. A new run would
   * hide it behind a newer job, and a second model run the same day is refused anyway (R49).
   *
   * Follows and returns that run; null when none holds a model answer, and something is to be
   * started instead. The listener answers at once, from cache if need be; a slow one is not worth
   * waiting on.
   */
  suspend fun resume(): SuggestionRun? {
    val earlier = withTimeoutOrNull(RESUME_WAIT) {
      manager.observeRun(thingId)
        .firstOrNull()
    }
    if (earlier == null || !earlier.holdsModelAnswer()) return null
    modelRequested = true
    followed.value = earlier.jobId
    return earlier
  }

  /** Starts a run for the curated suggestions alone, with no model call, and follows it. */
  suspend fun startCurated(entryPoint: SuggestionEntryPoint): AiStartResult {
    val started = manager.start(thingId, entryPoint, curatedOnly = true)
    if (started is AiStartResult.Started) followed.value = started.jobId
    return started
  }

  /**
   * Starts a model run, reading [documents], and follows it. The run that was on screen (a
   * curated-only one, or a failed one) is finished with: the new one carries the same curated
   * list, so it is closed first. A refusal leaves everything as it was.
   */
  suspend fun startModel(
    entryPoint: SuggestionEntryPoint,
    documents: List<Attachment> = emptyList(),
  ): AiStartResult {
    val replaced = run?.jobId
    val started = manager.start(thingId, entryPoint, curatedOnly = false, documents = documents)
    if (started !is AiStartResult.Started) return started
    if (replaced != null && replaced != started.jobId) manager.dismiss(replaced)
    modelRequested = true
    followed.value = started.jobId
    return started
  }

  /** [suggestion] as accepting it from the followed run would write it (PRD R28). */
  suspend fun draftOf(suggestion: TaskSuggestion): MaintenanceTask =
    manager.draftOf(thingId, suggestion, run?.result?.generation_version.orEmpty())

  /**
   * Writes [chosen] from the followed run, which ends it; a run still working gives up its answer
   * for the cards on screen. Empty when there is no run with cards, or nothing could be written.
   */
  suspend fun accept(chosen: List<AcceptedSuggestion>): List<WrittenSuggestion> {
    val current = run ?: return emptyList()
    val jobId = current.jobId ?: return emptyList()
    val result = current.result ?: return emptyList()
    return manager.accept(thingId, jobId, result.generation_version, chosen)
  }

  /**
   * The screen is left without accepting. A curated-only or failed run is closed. The model's
   * answer is kept for the day (R19), so coming back finds it; one still working carries on, and
   * its push brings the user back (R20). Accepting is what closes an answer.
   */
  suspend fun leave() {
    val current = run ?: return
    if (!current.holdsModelAnswer()) current.jobId?.let { manager.dismiss(it) }
  }

  /** Closes the followed run whatever it holds: the user has gone to do something else about it. */
  suspend fun dismiss() {
    run?.jobId?.let { manager.dismiss(it) }
  }

  private companion object {
    /** How long opening the list waits to learn whether a model answer is held (R19). */
    val RESUME_WAIT = 2.seconds
  }
}
