package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.SuggestedTasksAccepted
import dev.fanfly.wingslog.core.analytics.SuggestedTasksOffered
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsAccepted
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsFailed
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsRequested
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsShown
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.nav.SuggestionsMode
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.attachmentsFromDocumentsArg
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.model.taskFromDraftArg
import dev.fanfly.wingslog.feature.tasks.model.toDraftArg
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.RecentlyAddedTasks
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.feature.tasks.suggestions.model.SuggestionItem
import dev.fanfly.wingslog.feature.tasks.suggestions.model.WrittenSuggestion
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The empty task list's recommended tasks, and the Add Tasks sheet's *Suggest* (PRD R1, R2). Not a
 * step after creating a Thing since 2026-10-03.
 *
 * Every card comes from the suggestion RPC (design §6.8, PRD R1); the app's own list went with the
 * v1 flag (T25). The add mode starts the model run at once, with the documents picked on
 * the Add Tasks sheet. The curated mode asks for the curated suggestions alone first; the model run
 * starts from its AI button. Either way the model run's first result is the curated list.
 *
 * Both §13 events are emitted from here: `starter_tasks_offered` when cards are first
 * shown, the denominator that tells "declined" apart from "never offered", and
 * `starter_tasks_accepted` with how many survived.
 */
class SuggestionsViewModel(
  private val fleetManager: FleetManager,
  private val taskDataManager: TaskDataManager,
  private val templateRegistry: TemplateRegistry,
  private val analytics: AnalyticsManager,
  private val suggestionManager: TaskSuggestionManager,
  private val suggestEntry: TaskSuggestionEntry,
  private val attachmentManager: AttachmentManager,
  private val recentlyAdded: RecentlyAddedTasks,
  savedStateHandle: SavedStateHandle,
  private val clock: Clock = Clock.System,
) : ViewModel() {

  val thingId: String = checkNotNull(savedStateHandle[Screen.THING_ID])

  private val mode =
    SuggestionsMode.fromWire(savedStateHandle.get<String>(Screen.SUGGESTIONS_MODE))

  /**
   * The add mode's files, picked on the Add Tasks sheet and stored on this device, held by no
   * record.
   * The run they go to lets them go when it ends; anything else that drops them lets them go here.
   */
  private val pickedDocuments: List<Attachment> =
    savedStateHandle.get<String>(Screen.SUGGESTIONS_DOCUMENT)
      ?.takeIf { mode == SuggestionsMode.ADD }
      ?.let(::attachmentsFromDocumentsArg)
      .orEmpty()

  private val _uiState = MutableStateFlow(SuggestionsUiState(mode = mode))
  val uiState = _uiState.asStateFlow()

  /** The run the cards come from; null until the first one is in. */
  private var run: SuggestionRun? = null

  /** The job the cards follow: the one this screen started last. */
  private var followedJob: AiJobId? = null

  /**
   * The newest run the listener has said, followed or not. The listener can hear of a job before
   * the call that started it returns, and a job that is written already finished is never said
   * twice, so [followNow] shows this one once [followedJob] names it.
   */
  private var latestRun: SuggestionRun? = null

  /** `starter_tasks_offered` has been reported for this screen. */
  private var offeredCounted = false

  /** The user asked for the model, here or on the Add Tasks sheet, not the curated list alone. */
  private var modelRequested = false

  /**
   * The titles of the tasks the Thing has, normalized, read once on opening: a suggestion for one
   * of them is not shown (owner's decision, 2026-10-03; PRD R24).
   */
  private var trackedTitles: Set<String> = emptySet()

  /** When the model was last asked, for the shown event's latency; and which job it was. */
  private var requestedAt: Instant? = null
  private var reportedJob: AiJobId? = null

  init {
    viewModelScope.launch {
      val template = templateRegistry.forThingWithFallback(
        fleetManager.loadThing(thingId)
          .filterNotNull()
          .first()
      )
      _uiState.update {
        it.copy(
          template = template,
          lexicon = templateRegistry.lexiconFor(template)
        )
      }
      trackedTitles = taskDataManager.observeTasks(thingId)
        .first()
        .mapTo(mutableSetOf()) { normalizeTitle(it.title) }
      showRun(template)
    }
  }

  private suspend fun showRun(template: ThingTemplate?) {
    // R19: the model's answer is held for a day until the user acts on it. Opening the list again
    // shows it rather than starting over: a new run would hide it behind a newer job, and a second
    // model run the same day is refused anyway (R49).
    // The listener answers at once, from cache if need be; a slow one is not worth waiting on.
    val earlier = withTimeoutOrNull(RESUME_WAIT) {
      suggestionManager.observeRun(thingId)
        .firstOrNull()
    }
    if (earlier != null && earlier.holdsModelAnswer()) {
      // The answer held is what this screen shows; a new run would be refused for the day (R49),
      // so the files picked for one are not read.
      releaseAll(pickedDocuments)
      modelRequested = true
      followedJob = earlier.jobIdOrNull
      // Already reported when it first arrived.
      if (earlier !is SuggestionRun.Working) reportedJob = followedJob
      follow(template)
      return
    }
    if (mode == SuggestionsMode.ADD && startAdded()) {
      follow(template)
      return
    }
    showCurated(template)
  }

  /**
   * The add mode: the model run, at once, with the sheet's files. False when it was refused: the
   * files are let go of, the screen says why once, and the curated list follows, with its AI button
   * where a run can still start.
   */
  private suspend fun startAdded(): Boolean {
    modelRequested = true
    _uiState.update {
      it.copy(
        isSuggesting = true,
        readsDocuments = pickedDocuments.isNotEmpty()
      )
    }
    val started = suggestionManager.start(
      thingId,
      entryPoint = mode.wire,
      curatedOnly = false,
      documents = pickedDocuments,
    )
    if (started is AiStartResult.Started) {
      followedJob = started.jobId
      requested(mode.wire, pickedDocuments.size)
      return true
    }
    val reason = (started as AiStartResult.Refused).reason
    logger.i { "The model run did not start: $started" }
    failed(reason)
    releaseAll(pickedDocuments)
    modelRequested = false
    _uiState.update { it.copy(isSuggesting = false, notice = reason) }
    return false
  }

  /** The curated mode opens on the curated list; the model is asked from the AI button. */
  private suspend fun showCurated(template: ThingTemplate?) {
    val started = suggestionManager.start(
      thingId,
      entryPoint = mode.wire,
      curatedOnly = true
    )
    if (started !is AiStartResult.Started) {
      // Nothing to show: the screen closes, and the task tab says why (PRD R21, R51).
      logger.i { "No suggestions to show: $started" }
      val reason = (started as AiStartResult.Refused).reason
      _uiState.update {
        it.copy(
          isLoading = false,
          isDone = true,
          closingError = reason
        )
      }
      return
    }
    followedJob = started.jobId
    // The curated list came without the model; the user can ask for it here (PRD R1), where the
    // Thing is described well enough (R5).
    if (suggestEntry.observe(thingId)
        .first() == SuggestEntry.Available
    ) {
      // Beside the cards, which arrive meanwhile: the sheet must not open before this is known.
      viewModelScope.launch { checkAi() }
    }
    follow(template)
  }

  /** Follows [followedJob] for as long as the screen is open, into the cards and their states. */
  private suspend fun follow(template: ThingTemplate?) {
    suggestionManager.observeRun(thingId)
      .collect { latest ->
        latestRun = latest
        // Until the listener catches up with the job just started, the newest it knows is older.
        if (latest.jobIdOrNull != followedJob) return@collect
        show(latest, template)
      }
  }

  /**
   * [followedJob] has just changed: shows the run the listener already said, when it is that job.
   * Without this a run that reached the listener before its start call returned would be skipped,
   * and one that never changes again (the model refused, the curated list alone) never shown.
   */
  private fun followNow() {
    val latest = latestRun ?: return
    if (latest.jobIdOrNull == followedJob) show(latest, uiState.value.template)
  }

  /** [latest], the followed job's run, into the cards and their states. */
  private fun show(latest: SuggestionRun, template: ThingTemplate?) {
    run = latest
    val result = latest.resultOrNull
    val finished = latest !is SuggestionRun.Working
    val failure = (latest as? SuggestionRun.Failed)?.reason
    // A curated-only run is finished from the start, so only a model run reads as working.
    val working = latest as? SuggestionRun.Working
    // Only a model run can come back with nothing to say; a curated-only one that is empty is
    // a template with no list.
    val notEnough = latest is SuggestionRun.Empty && modelRequested
    if (finished && modelRequested) report(latest)
    if (notEnough && !askedAfterEmpty) {
      askedAfterEmpty = true
      viewModelScope.launch { checkManual() }
    }
    _uiState.update {
      it.copy(
        isSuggesting = !finished,
        readsDocuments = it.readsDocuments || working?.stage in DOCUMENT_STAGES,
        failure = failure,
        stage = working?.stage,
        stageArg = working?.stageArg,
        aiSkipped = (latest as? SuggestionRun.Ready)?.aiSkipped,
        notEnough = notEnough,
      )
    }
    if (result == null) {
      // A template with no curated list, and no model answer (yet). A failure with no cards
      // to fall back on closes the screen, and the task tab says why.
      // An empty model run stays, to offer *Add details* (R21a; for the custom template that
      // message is the whole screen).
      if (finished) _uiState.update {
        val close = it.items.isEmpty() && !notEnough
        it.copy(
          isLoading = false,
          isDone = close,
          closingError = failure.takeIf { _ -> close },
        )
      }
      return
    }
    _uiState.update { state ->
      state.copy(
        isLoading = false,
        documents = result.documents,
        items = itemsOf(
          result,
          state.items
        ).filter { it.isShown() }
      )
    }
    if (!offeredCounted) {
      offeredCounted = true
      offered(template, uiState.value.items.size)
    }
  }

  /** [checkManual] has been asked for this screen's empty run. */
  private var askedAfterEmpty = false

  /**
   * Asks, once a model run has come back empty, whether another can start: *Use a manual* opens
   * the Add Tasks sheet, which is a dead end while the day's run is spent (R49). When none can,
   * the banner says when instead of offering it.
   */
  private suspend fun checkManual() {
    val eligibility = suggestionManager.eligibility(thingId)
    _uiState.update {
      if (eligibility.allowed) {
        it.copy(canUseManual = true, manualBlocked = null)
      } else {
        it.copy(
          canUseManual = false,
          manualBlocked = AiSkipped(
            eligibility.reason ?: AiErrorCode.UNKNOWN,
            eligibility.nextAvailableAt,
          ),
        )
      }
    }
  }

  /**
   * Asks, once the curated list is up, whether a model run can start, before offering the AI button.
   * When it cannot (the daily limit, another member's run, …) the screen says why and when, and
   * offers no button.
   */
  private suspend fun checkAi() {
    _uiState.update { it.copy(isCheckingAi = true) }
    val eligibility = suggestionManager.eligibility(thingId)
    _uiState.update {
      if (eligibility.allowed) {
        it.copy(isCheckingAi = false, canSuggest = true)
      } else {
        it.copy(
          isCheckingAi = false,
          aiUnavailable = AiSkipped(
            eligibility.reason ?: AiErrorCode.UNKNOWN,
            eligibility.nextAvailableAt,
          ),
        )
      }
    }
  }

  /**
   * The AI button on the curated list: starts the model run, whose first result is the same
   * curated list, so the cards stay while it works and its answer replaces them (design §9.2).
   * Manuals are added on the Add Tasks sheet, not here.
   */
  fun onSuggest() {
    if (!uiState.value.canSuggest) return
    startModelRun(onRefused = { it.copy(canSuggest = true) })
  }

  /** *Try again* after a failed model run: starts a new one over the cards still on screen. */
  fun onRetry() {
    if (uiState.value.failure == null) return
    startModelRun(onRefused = { it })
  }

  /** The refusal of *Suggest more* or *Try again*, once shown. */
  fun onNoticeShown() {
    _uiState.update { it.copy(notice = null) }
  }

  /** That nothing was added, once shown. */
  fun onSaveFailedShown() {
    _uiState.update { it.copy(saveFailed = false) }
  }

  /**
   * Shown unless the Thing already has it: the server says so (the model by meaning, a curated item
   * by title), or a task with the same title, ignoring case and spacing, is on the Thing: the
   * second catches a task added since the run started, which the server could not see.
   */
  private fun SuggestionItem.isShown(): Boolean =
    !isAlreadyTracked && normalizeTitle(suggestion.title) !in trackedTitles

  /** *Add details* after an empty run: the run is done with; the screen gives way to the Thing's edit form. */
  fun onAddDetails() {
    run?.jobIdOrNull?.let { viewModelScope.launch { suggestionManager.dismiss(it) } }
  }

  private fun startModelRun(onRefused: (SuggestionsUiState) -> SuggestionsUiState) {
    modelRequested = true
    _uiState.update {
      it.copy(
        canSuggest = false,
        isSuggesting = true,
        failure = null,
        notEnough = false
      )
    }
    viewModelScope.launch {
      val curatedRun = run
      val started = suggestionManager.start(
        thingId,
        entryPoint = SuggestionsMode.CURATED.wire,
        curatedOnly = false,
      )
      if (started !is AiStartResult.Started) {
        logger.i { "The model run did not start: $started" }
        val reason = (started as AiStartResult.Refused).reason
        failed(reason)
        _uiState.update {
          onRefused(
            it.copy(
              isSuggesting = false,
              notice = reason
            )
          )
        }
        return@launch
      }
      followedJob = started.jobId
      followNow()
      requested(SUGGEST_MORE, documentCount = 0)
      // The run on screen (curated-only, or failed) is finished with; the new one carries the
      // same curated list.
      curatedRun?.jobIdOrNull?.takeIf { it != started.jobId }
        ?.let { suggestionManager.dismiss(it) }
    }
  }

  /** The card the task form is open for, until its edit comes back (PRD R28). */
  private var editingId: String? = null

  /**
   * The draft argument for changing card [index] in the task form before adding it (PRD R28):
   * the user's earlier edit, or the suggestion as accepting would write it. Null for a card with
   * no id to come back to.
   */
  suspend fun draftFor(index: Int): String? {
    val item = uiState.value.items.getOrNull(index) ?: return null
    editingId = item.suggestion.suggestion_id?.value_ ?: return null
    val draft = item.edited ?: suggestionManager.draftOf(
      thingId,
      item.suggestion,
      generationVersion = run?.resultOrNull?.generation_version.orEmpty(),
    )
    return draft.toDraftArg()
  }

  /** The task form handed back [draftArg]: that card is now the user's version, and checked. */
  fun onEdited(draftArg: String) {
    val edited = taskFromDraftArg(draftArg) ?: return
    val id = editingId ?: return
    editingId = null
    _uiState.update { state ->
      state.copy(
        items = state.items.map { item ->
          if (item.suggestion.suggestion_id?.value_ == id) item.copy(
            edited = edited,
            selected = true
          ) else item
        },
      )
    }
  }

  /** One inline edit at a time: each starts from the one before it, never from a stale card. */
  private val editing = Mutex()

  /** The row's meter interval, changed in place (1d): "every 50 hrs" becomes [interval]. */
  fun onMeterIntervalChange(index: Int, interval: Float) {
    if (interval <= 0f) return
    editRules(index) { rule ->
      rule.meter_rule?.let { rule.copy(meter_rule = it.copy(interval = interval)) }
        ?: rule
    }
  }

  /** The row's calendar interval, changed in place, in months; years fold into them. */
  fun onMonthsChange(index: Int, months: Int) {
    if (months <= 0) return
    editRules(index) { rule ->
      rule.time_rule?.takeIf { it.interval_days == 0 }
        ?.let {
          rule.copy(
            time_rule = it.copy(
              interval_months = months,
              interval_years = 0
            )
          )
        }
        ?: rule
    }
  }

  /** The row's calendar interval, changed in place, for a rule kept in days. */
  fun onDaysChange(index: Int, days: Int) {
    if (days <= 0) return
    editRules(index) { rule ->
      rule.time_rule?.takeIf { it.interval_days > 0 }
        ?.let { rule.copy(time_rule = it.copy(interval_days = days)) }
        ?: rule
    }
  }

  /**
   * Changes card [index]'s rules in place, as the task form's draft mode would (PRD R28): the user's
   * earlier edit, or the suggestion as accepting would write it, with [change] applied to each
   * rule. A changed card is checked, as one edited in the form is.
   */
  private fun editRules(
    index: Int,
    change: (InspectionRule) -> InspectionRule
  ) {
    val id =
      uiState.value.items.getOrNull(index)?.suggestion?.suggestion_id?.value_
        ?: return
    viewModelScope.launch {
      editing.withLock {
        val item =
          uiState.value.items.firstOrNull { it.suggestion.suggestion_id?.value_ == id }
            ?: return@withLock
        val base = item.edited ?: suggestionManager.draftOf(
          thingId,
          item.suggestion,
          generationVersion = run?.resultOrNull?.generation_version.orEmpty(),
        )
        val edited = base.copy(rules = base.rules.map(change))
        _uiState.update { state ->
          state.copy(
            items = state.items.map {
              if (it.suggestion.suggestion_id?.value_ == id) {
                it.copy(edited = edited, selected = true)
              } else {
                it
              }
            },
          )
        }
      }
    }
  }

  fun onToggle(index: Int) {
    _uiState.update { state ->
      state.copy(
        items = state.items.mapIndexed { i, item ->
          if (i == index) item.copy(selected = !item.selected) else item
        }
      )
    }
  }

  /**
   * *Select all* on a section: every row in it picked, or, once they all are, *Clear*: none.
   * [indices] are the section's rows ([CardGroup.cards]).
   */
  fun onToggleGroup(indices: List<Int>) {
    _uiState.update { state ->
      val select = indices.any { state.items.getOrNull(it)?.selected == false }
      state.copy(
        items = state.items.mapIndexed { i, item ->
          if (i in indices) item.copy(selected = select) else item
        },
      )
    }
  }

  fun onAccept() {
    val state = uiState.value
    val chosen = state.items.filter { it.selected }
    if (chosen.isEmpty() || state.isSaving) return
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true) }
      val current = run
      val written = current?.let { writeRun(it, chosen) }
        .orEmpty()
      if (written.isEmpty()) {
        // Nothing went in, and the run is still open: the cards stay, checked, to try again.
        _uiState.update { it.copy(isSaving = false, saveFailed = true) }
        return@launch
      }
      analytics.log(
        SuggestedTasksAccepted(
          templateId = state.template?.id.orEmpty(),
          taskCount = written.size
        )
      )
      // The task tab says how many, with *Undo* (1f).
      recentlyAdded.record(thingId, written.map { it.taskId })
      _uiState.update {
        it.copy(
          isSaving = false,
          isDone = true,
          acceptedCount = written.size
        )
      }
    }
  }

  /** "Skip" is a first-class answer (PRD §8.1), and it leaves no trace but the offered event. */
  fun onSkip() {
    // A curated-only or failed run is closed. The model's answer is kept for the day (R19), so
    // leaving and coming back finds it; one still working carries on, and its push brings the
    // user back (R20). Accepting is what closes an answer.
    val current = run
    if (current != null && !current.holdsModelAnswer()) {
      viewModelScope.launch {
        current.jobIdOrNull?.let {
          suggestionManager.dismiss(
            it
          )
        }
      }
    }
    _uiState.update { it.copy(isDone = true) }
  }

  /**
   * Through the manager, which maps each as the server describes it and closes the run. Accepting
   * while the model still works takes the cards on screen and ends the run.
   */
  private suspend fun writeRun(
    current: SuggestionRun,
    chosen: List<SuggestionItem>
  ): List<WrittenSuggestion> {
    val jobId = current.jobIdOrNull ?: return emptyList()
    val result = current.resultOrNull ?: return emptyList()
    val ready =
      current as? SuggestionRun.Ready ?: SuggestionRun.Ready(jobId, result)
    val written = suggestionManager.accept(
      thingId,
      ready,
      chosen.map { AcceptedSuggestion(it.suggestion, it.edited) })
    if (written.isNotEmpty()) {
      // What was written, not what was chosen: a card whose write failed is not counted.
      val accepted = written.map { it.accepted }
      analytics.log(
        TaskSuggestionsAccepted(
          templateId = uiState.value.template?.id.orEmpty(),
          curatedCount = accepted.count { !it.suggestion.isFromModel() },
          aiCount = accepted.count { it.suggestion.isFromModel() },
          editedCount = accepted.count { it.edited != null },
        ),
      )
    }
    return written
  }

  private fun releaseAll(documents: List<Attachment>) {
    if (documents.isEmpty()) return
    viewModelScope.launch {
      documents.forEach {
        attachmentManager.release(
          it,
          owner = null
        )
      }
    }
  }

  /** R50: the model was asked; its answer or failure is reported once per job by [report]. */
  private fun requested(entryPoint: String, documentCount: Int) {
    requestedAt = clock.now()
    analytics.log(
      TaskSuggestionsRequested(
        templateId = uiState.value.template?.id.orEmpty(),
        entryPoint = entryPoint,
        documentCount = documentCount,
      ),
    )
  }

  private fun report(finished: SuggestionRun) {
    val jobId = finished.jobIdOrNull ?: return
    if (jobId == reportedJob) return
    reportedJob = jobId
    when (finished) {
      is SuggestionRun.Failed -> failed(finished.reason)
      else -> {
        val cards = finished.resultOrNull?.suggestions.orEmpty()
        val latency =
          requestedAt?.let { (clock.now() - it).inWholeSeconds } ?: 0L
        analytics.log(
          TaskSuggestionsShown(
            templateId = uiState.value.template?.id.orEmpty(),
            curatedCount = cards.count { !it.isFromModel() },
            aiCount = cards.count { it.isFromModel() },
            latencySeconds = latency,
          ),
        )
      }
    }
  }

  private fun failed(reason: AiErrorCode) {
    analytics.log(
      TaskSuggestionsFailed(
        templateId = uiState.value.template?.id.orEmpty(),
        reason = reason.name.lowercase()
      ),
    )
  }

  private fun offered(template: ThingTemplate?, count: Int) {
    analytics.log(
      SuggestedTasksOffered(
        templateId = template?.id.orEmpty(),
        taskCount = count
      )
    )
  }

  private companion object {
    val logger = Logger.withTag("SuggestionsViewModel")

    /** How long opening the list waits to learn whether a model answer is held (R19). */
    val RESUME_WAIT = 2.seconds

    /** The entry point a model run asked from the curated list reports (R50). */
    const val SUGGEST_MORE = "suggest_more"

    /** The pipeline stages that work on a document, whose argument names it (see `stageText`). */
    val DOCUMENT_STAGES = setOf("reading_document", "finding_schedule", "extracting_schedule")

    /**
     * Cards for [result], none checked to start (PRD R27, revised 2026-10-03: the user checks what
     * they need, and the server's `preselect` is not read). A card already on screen keeps the
     * user's check when the model's answer replaces the curated list.
     */
    fun itemsOf(
      result: SuggestTasksResult,
      shown: List<SuggestionItem>
    ): List<SuggestionItem> {
      val chosenIds = shown.filter { it.selected }
        .mapTo(mutableSetOf()) { it.suggestion.suggestion_id?.value_ }
      return result.suggestions.map { suggestion ->
        val id = suggestion.suggestion_id?.value_
        SuggestionItem(
          suggestion = suggestion,
          selected = id in chosenIds,
          // The user's edit stays with its card when the model's answer replaces the list (R28).
          edited = shown.firstOrNull { it.suggestion.suggestion_id?.value_ == id }?.edited,
        )
      }
    }

    /**
     * A model run working, or one whose answer has the model's cards in it: what the user would
     * lose by closing it or by starting another. A curated-only run, an empty or failed model run,
     * and no run at all hold nothing of the kind.
     */
    fun SuggestionRun.holdsModelAnswer(): Boolean =
      this is SuggestionRun.Working ||
        resultOrNull?.suggestions.orEmpty()
          .any { it.isFromModel() }

    /** A title as the tracked check compares it: trimmed, single-spaced, lower case. */
    fun normalizeTitle(title: String): String = title.trim()
      .replace(Regex("\\s+"), " ")
      .lowercase()

    /** The run's job, whatever its state; null when there is no run. */
    val SuggestionRun.jobIdOrNull: AiJobId?
      get() = when (this) {
        SuggestionRun.Idle -> null
        is SuggestionRun.Working -> jobId
        is SuggestionRun.Ready -> jobId
        is SuggestionRun.Empty -> jobId
        is SuggestionRun.Failed -> jobId
      }

    /** The suggestions the run holds now: the curated list, or the merged answer (design §6.8). */
    val SuggestionRun.resultOrNull: SuggestTasksResult?
      get() = when (this) {
        SuggestionRun.Idle -> null
        is SuggestionRun.Working -> result
        is SuggestionRun.Ready -> result
        is SuggestionRun.Empty -> result
        is SuggestionRun.Failed -> result
      }
  }
}
