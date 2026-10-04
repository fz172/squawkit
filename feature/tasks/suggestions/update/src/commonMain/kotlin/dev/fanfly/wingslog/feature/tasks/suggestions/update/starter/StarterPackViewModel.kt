package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.StarterTasksAccepted
import dev.fanfly.wingslog.core.analytics.StarterTasksOffered
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsAccepted
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsFailed
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsRequested
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsShown
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.FileTooLargeException
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.model.attachmentFromDocumentArg
import dev.fanfly.wingslog.feature.attachment.model.isReadableDocument
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.toMaintenanceTask
import dev.fanfly.wingslog.feature.tasks.model.taskFromDraftArg
import dev.fanfly.wingslog.feature.tasks.model.toDraftArg
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.feature.tasks.suggestions.model.StarterPackItem
import dev.fanfly.wingslog.feature.tasks.suggestions.model.toSuggestion
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The empty task list's recommended tasks, and the task list's *Suggest tasks* (PRD R1, R2). Not a
 * step after creating a Thing since 2026-10-03.
 *
 * Two sources until the v1 flag removal (T25):
 * - **The app's starter pack**, off the Thing's own DNA, where suggestions are not supported yet
 *   (production). The DNA is what the Thing was created from, and what an empty Tasks tab
 *   re-offers later.
 * - **The suggestion RPC**, where they are (developer builds; design §6.8, PRD R1). Every mode
 *   asks for the curated suggestions alone first; the model run starts from the sources sheet
 *   (design §9.3), which the suggest mode opens at once, and its first result is the same list.
 *
 * Both §13 events are emitted from here either way: `starter_tasks_offered` when cards are first
 * shown, the denominator that tells "declined" apart from "never offered", and
 * `starter_tasks_accepted` with how many survived.
 */
class StarterPackViewModel(
  private val fleetManager: FleetManager,
  private val taskDataManager: TaskDataManager,
  private val templateRegistry: TemplateRegistry,
  private val analytics: AnalyticsManager,
  private val capability: AppCapability,
  private val suggestionManager: TaskSuggestionManager,
  private val suggestEntry: TaskSuggestionEntry,
  private val attachmentManager: AttachmentManager,
  savedStateHandle: SavedStateHandle,
  private val clock: Clock = Clock.System,
) : ViewModel() {

  val thingId: String = checkNotNull(savedStateHandle[Screen.THING_ID])

  /**
   * A file already on one of the Thing's records, from *Find tasks in this document* (PRD R4): the
   * sheet starts with it rather than the picker, and the run reads the stored blob, uploading
   * nothing. Its record keeps holding it, so ending the run never lets it go (design §8.3).
   */
  private val presetDocument: Attachment? =
    savedStateHandle.get<String>(Screen.SUGGESTIONS_DOCUMENT)
      ?.let(::attachmentFromDocumentArg)
      ?.takeIf { it.isReadableDocument() }

  private val _uiState = MutableStateFlow(
    StarterPackUiState(
      mode = savedStateHandle.get<String>(Screen.SUGGESTIONS_MODE)
        ?: Screen.StarterPack.MODE_STARTER,
    ),
  )
  val uiState = _uiState.asStateFlow()

  /** The run the cards come from, on the server source; null on the app's pack. */
  private var run: SuggestionRun? = null

  /** The job the cards follow: the one this screen started last. */
  private var followedJob: AiJobId? = null

  /** The user asked for the model, here or from *Suggest tasks*, rather than the curated list alone. */
  private var modelRequested = false

  /**
   * The titles of the tasks the Thing has, normalized, read once on opening: a suggestion for one
   * of them is not shown (owner's decision, 2026-10-03; PRD R24).
   */
  private var trackedTitles: Set<String> = emptySet()

  /** The sheet has been opened once; only that first opening picks by itself. */
  private var sheetOpened = false

  /** What the next model run reports as its entry point (R50): the mode that opened the sheet, then *Suggest more*. */
  private var entryPoint: String = SUGGEST_MORE

  /** When the model was last asked, for the shown event's latency; and which job it was. */
  private var requestedAt: Instant? = null
  private var reportedJob: AiJobId? = null

  init {
    viewModelScope.launch {
      val template = fleetManager.loadThing(thingId)
        .filterNotNull()
        .first()
        .template
      _uiState.update {
        it.copy(
          template = template,
          lexicon = templateRegistry.lexiconFor(template)
        )
      }
      trackedTitles = taskDataManager.observeTasks(thingId)
        .first()
        .mapTo(mutableSetOf()) { normalizeTitle(it.title) }
      if (capability.isTaskSuggestionsSupported) showRun(template) else showPack(
        template
      )
    }
  }

  private fun showPack(template: ThingTemplate?) {
    val items = template?.starter_tasks.orEmpty()
      .mapIndexed { index, task ->
        StarterPackItem(
          suggestion = task.toSuggestion(index),
          // Nothing is checked to start (PRD R27, revised 2026-10-03).
          selected = false,
          starterTask = task
        )
      }
      .filter { it.isShown() }
    // Nothing to offer — a stale route, or a pack removed by a DNA refresh. Not an offer, so not
    // counted as one.
    _uiState.update {
      it.copy(
        isLoading = false,
        items = items,
        isDone = items.isEmpty()
      )
    }
    if (items.isNotEmpty()) offered(template, items.size)
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
      modelRequested = true
      followedJob = earlier.jobIdOrNull
      // Already reported when it first arrived.
      if (earlier !is SuggestionRun.Working) reportedJob = followedJob
      follow(template)
      return
    }
    // Every mode opens on the curated list; the model is asked from the sources sheet, which the
    // suggest mode opens at once (design §9.1, §9.3).
    val started = suggestionManager.start(
      thingId,
      entryPoint = uiState.value.mode,
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
    var counted = false
    suggestionManager.observeRun(thingId)
      .collect { latest ->
        // Until the listener catches up with the job just started, the newest it knows is older.
        if (latest.jobIdOrNull != followedJob) return@collect
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
        _uiState.update {
          it.copy(
            isSuggesting = !finished,
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
          // message is the whole screen). So does an empty curated list in the suggest mode, under
          // the sources sheet it opened.
          if (finished) _uiState.update {
            val close = it.items.isEmpty() && !notEnough &&
              (modelRequested || it.mode == Screen.StarterPack.MODE_STARTER)
            it.copy(
              isLoading = false,
              isDone = close,
              closingError = failure.takeIf { _ -> close },
            )
          }
          return@collect
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
        if (!counted) {
          counted = true
          offered(template, uiState.value.items.size)
        }
      }
  }

  /**
   * Asks, once the curated list is up, whether a model run can start and whether documents are
   * allowed, before offering either. When it cannot (the daily limit, another member's run, …)
   * the screen says why and when, and offers neither the button nor the sheet. When it can, the
   * button is offered; only the document mode, which the user opened to read a document, opens
   * the sheet by itself (owner's decision, 2026-10-04: nothing pops up unasked).
   */
  private suspend fun checkAi() {
    _uiState.update { it.copy(isCheckingAi = true) }
    val access = askAccess()
    if (!access.eligibility.allowed) {
      _uiState.update {
        it.copy(
          isCheckingAi = false,
          aiUnavailable = AiSkipped(
            access.eligibility.reason ?: AiErrorCode.UNKNOWN,
            access.eligibility.nextAvailableAt,
          ),
        )
      }
      return
    }
    _uiState.update { it.copy(isCheckingAi = false, canSuggest = true) }
    if (uiState.value.mode != Screen.StarterPack.MODE_STARTER) entryPoint =
      uiState.value.mode
    if (uiState.value.mode == Screen.StarterPack.MODE_DOCUMENT) openSheet()
  }

  /** What the server said when the screen opened; the sheet opens from it with no wait. */
  private var access: SourceAccess? = null

  private suspend fun askAccess(): SourceAccess {
    // Without documents: that call still says whether they are allowed, where asking with them
    // refuses a free owner's run outright.
    val eligibility = suggestionManager.eligibility(thingId)
    val owner = suggestionManager.isOwner(thingId)
    return SourceAccess(eligibility, owner).also { access = it }
  }

  /**
   * The AI button on the curated list. Where documents are allowed (the owner's Pro), the sheet
   * asks for them first, with *Skip* and *Add documents*; otherwise there is nothing to ask, and
   * the run starts at once (owner's decision, 2026-10-04).
   */
  fun onOpenSources() {
    val state = uiState.value
    if (!state.canSuggest || state.sources != null) return
    val known = access
    if (known != null && !known.eligibility.documentsAllowed) {
      onSuggest()
      return
    }
    openSheet()
  }

  /** Opens the sources sheet (design §9.3), from what the server said if that is in. */
  private fun openSheet() {
    val state = uiState.value
    if (!state.canSuggest || state.sources != null) return
    val firstOpening = !sheetOpened
    val pickOnOpen =
      state.mode == Screen.StarterPack.MODE_DOCUMENT && firstOpening
    sheetOpened = true
    val known = access
    if (known != null) {
      _uiState.update {
        it.copy(
          sources = SourcesState(pickOnOpen = pickOnOpen).with(known)
            .withPreset(firstOpening)
        )
      }
      return
    }
    // Not asked yet: the sheet shows that it is checking until the answer is in.
    _uiState.update { it.copy(sources = SourcesState(pickOnOpen = pickOnOpen)) }
    viewModelScope.launch {
      val asked = askAccess()
      updateSources {
        it.with(asked)
          .withPreset(firstOpening)
      }
    }
  }

  private fun SourcesState.with(access: SourceAccess): SourcesState {
    val eligibility = access.eligibility
    return copy(
      isChecking = false,
      documentsAllowed = eligibility.documentsAllowed,
      isOwner = access.isOwner,
      blocked = eligibility.reason.takeIf { !eligibility.allowed },
      availableAt = eligibility.nextAvailableAt.takeIf { !eligibility.allowed },
    )
  }

  private data class SourceAccess(
    val eligibility: AiEligibility,
    val isOwner: Boolean
  )

  /**
   * The first opening's [presetDocument], where documents are allowed, in place of the picker. A
   * free owner still gets the upsell from [SourcesState.pickOnOpen].
   */
  private fun SourcesState.withPreset(firstOpening: Boolean): SourcesState {
    val preset = presetDocument ?: return this
    if (!firstOpening || !documentsAllowed) return this
    return copy(documents = listOf(preset), pickOnOpen = false)
  }

  /**
   * Files picked in the sheet: each stored on this device and queued to upload at once, so it is
   * likely in Storage by the time the user taps *Suggest* (design §8.1). PDFs and images only
   * (PRD R7), up to the per-run count and the AI document size.
   */
  fun onAddDocuments(files: List<PickedFile>) {
    val sources = uiState.value.sources ?: return
    if (!sources.documentsAllowed || files.isEmpty()) return
    viewModelScope.launch {
      updateSources { it.copy(isAdding = true, problem = null) }
      var problem: DocumentProblem? = null
      for (file in files) {
        val current = uiState.value.sources ?: break
        if (current.atLimit) {
          problem = DocumentProblem.TOO_MANY
          break
        }
        if (!file.isReadableDocument()) {
          problem = DocumentProblem.UNSUPPORTED
          continue
        }
        try {
          val added = attachmentManager.addPickedFile(
            thingId,
            file,
            displayName = file.name,
            maxBytes = QuotaChecker.MAX_AI_DOCUMENT_BYTES,
          )
          updateSources { it.copy(documents = it.documents + added) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: FileTooLargeException) {
          problem = DocumentProblem.TOO_LARGE
        } catch (e: Exception) {
          logger.w(e) { "A picked document was not added" }
          problem = DocumentProblem.NOT_ADDED
        }
      }
      updateSources { it.copy(isAdding = false, problem = problem) }
    }
  }

  /** The picker could not read what was picked. */
  fun onPickFailed() {
    updateSources { it.copy(problem = DocumentProblem.NOT_ADDED) }
  }

  /** The screen opened the picker (or the upsell) for [SourcesState.pickOnOpen]. */
  fun onPickOnOpenHandled() {
    updateSources { it.copy(pickOnOpen = false) }
  }

  /** ✕ on a document: out of the run, and its copy let go of (no record holds it). */
  fun onRemoveDocument(attachmentId: String) {
    val removed =
      uiState.value.sources?.documents?.firstOrNull { it.id == attachmentId }
        ?: return
    updateSources { it.copy(documents = it.documents - removed) }
    viewModelScope.launch { attachmentManager.release(removed, owner = null) }
  }

  /**
   * The sheet closed without *Suggest*: its documents are let go of, and the list stays if any.
   * [closeIfEmpty] is false when the sheet gives way to the Pro upsell, which needs the screen.
   */
  fun onSourcesDismissed(closeIfEmpty: Boolean = true) {
    val documents = uiState.value.sources?.documents.orEmpty()
    // With no cards behind it (a template with no curated list) there is nothing left to show.
    _uiState.update {
      it.copy(
        sources = null,
        isDone = closeIfEmpty && it.items.isEmpty() && !it.isSuggesting
      )
    }
    releaseAll(documents)
  }

  /**
   * *Suggest* on the sources sheet: starts the model run with the documents picked, whose first
   * result is the same curated list, so the cards stay while it works and its answer replaces them
   * (design §9.2).
   */
  fun onSuggest() {
    val state = uiState.value
    if (!state.canSuggest) return
    val sources = state.sources
    if (sources != null && !sources.canSuggest) return
    _uiState.update { it.copy(sources = null) }
    startModelRun(
      documents = sources?.documents.orEmpty(),
      onRefused = { it.copy(canSuggest = true) },
    )
  }

  /** *Try again* after a failed model run: starts a new one over the cards still on screen. */
  fun onRetry() {
    if (uiState.value.failure == null) return
    startModelRun(documents = emptyList(), onRefused = { it })
  }

  /** The refusal of *Suggest more* or *Try again*, once shown. */
  fun onNoticeShown() {
    _uiState.update { it.copy(notice = null) }
  }

  /**
   * Shown unless the Thing already has it: the server says so (the model by meaning, a curated item
   * by title), or a task with the same title, ignoring case and spacing, is on the Thing. The second
   * check also covers the app's own pack, which the server never sees.
   */
  private fun StarterPackItem.isShown(): Boolean =
    !isAlreadyTracked && normalizeTitle(suggestion.title) !in trackedTitles

  /** *Add details* after an empty run: the run is done with; the screen gives way to the Thing's edit form. */
  fun onAddDetails() {
    run?.jobIdOrNull?.let { viewModelScope.launch { suggestionManager.dismiss(it) } }
  }

  private fun startModelRun(
    documents: List<Attachment>,
    onRefused: (StarterPackUiState) -> StarterPackUiState,
  ) {
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
        entryPoint = Screen.StarterPack.MODE_STARTER,
        curatedOnly = false,
        documents = documents,
      )
      if (started !is AiStartResult.Started) {
        logger.i { "The model run did not start: $started" }
        val reason = (started as AiStartResult.Refused).reason
        failed(reason)
        // No run took them, so nothing else will let them go.
        releaseAll(documents)
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
      requested(entryPoint, documents.size)
      entryPoint = SUGGEST_MORE
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
   * the user's earlier edit, or the suggestion as accepting would write it. Null for a card that
   * cannot be changed: one from the app's own pack, or one already tracked.
   */
  suspend fun draftFor(index: Int): String? {
    val item = uiState.value.items.getOrNull(index) ?: return null
    if (item.starterTask != null) return null
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

  fun onToggle(index: Int) {
    _uiState.update { state ->
      state.copy(
        items = state.items.mapIndexed { i, item ->
          if (i == index) item.copy(selected = !item.selected) else item
        }
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
      val written =
        if (current == null) writePack(chosen, state.template) else writeRun(
          current,
          chosen
        )
      if (written > 0) {
        analytics.log(
          StarterTasksAccepted(
            templateId = state.template?.id.orEmpty(),
            taskCount = written
          )
        )
      }
      _uiState.update {
        it.copy(
          isSaving = false,
          isDone = true,
          acceptedCount = written
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
   * One write per card, and a failure drops only its own card: the pack is a convenience, not a
   * transaction, and a half-written pack is still a better Tasks tab than an empty one.
   */
  private suspend fun writePack(
    chosen: List<StarterPackItem>,
    template: ThingTemplate?
  ): Int {
    val now = Clock.System.now()
    val createdAt = toWireInstant(now.epochSeconds, now.nanosecondsOfSecond)
    return chosen.mapNotNull { it.starterTask }
      .count { task ->
        taskDataManager.addTask(
          thingId,
          task.toMaintenanceTask(template, createdAt)
        )
          .onFailure { logger.w(it) { "Starter task '${task.title}' was not written" } }
          .isSuccess
      }
  }

  /**
   * Through the manager, which maps each as the server describes it and closes the run. Accepting
   * while the model still works takes the cards on screen and ends the run.
   */
  private suspend fun writeRun(
    current: SuggestionRun,
    chosen: List<StarterPackItem>
  ): Int {
    val jobId = current.jobIdOrNull ?: return 0
    val result = current.resultOrNull ?: return 0
    val ready =
      current as? SuggestionRun.Ready ?: SuggestionRun.Ready(jobId, result)
    val written = suggestionManager.accept(
      thingId,
      ready,
      chosen.map { AcceptedSuggestion(it.suggestion, it.edited) })
    if (written > 0) {
      analytics.log(
        TaskSuggestionsAccepted(
          templateId = uiState.value.template?.id.orEmpty(),
          curatedCount = chosen.count { !it.suggestion.isFromModel() },
          aiCount = chosen.count { it.suggestion.isFromModel() },
          editedCount = chosen.count { it.edited != null },
        ),
      )
    }
    return written
  }

  private fun updateSources(change: (SourcesState) -> SourcesState) {
    _uiState.update { state ->
      state.sources?.let {
        state.copy(
          sources = change(
            it
          )
        )
      } ?: state
    }
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
      StarterTasksOffered(
        templateId = template?.id.orEmpty(),
        taskCount = count
      )
    )
  }

  private companion object {
    val logger = Logger.withTag("StarterPackViewModel")

    /** How long opening the list waits to learn whether a model answer is held (R19). */
    val RESUME_WAIT = 2.seconds

    /** The entry point a model run asked from the curated list reports (R50). */
    const val SUGGEST_MORE = "suggest_more"

    /** What the document reader takes: a PDF, or a photo of pages (PRD R7). */
    fun PickedFile.isReadableDocument(): Boolean =
      mimeType == "application/pdf" || mimeType.startsWith("image/")

    /**
     * Cards for [result], none checked to start (PRD R27, revised 2026-10-03: the user checks what
     * they need, and the server's `preselect` is not read). A card already on screen keeps the
     * user's check when the model's answer replaces the curated list.
     */
    fun itemsOf(
      result: SuggestTasksResult,
      shown: List<StarterPackItem>
    ): List<StarterPackItem> {
      val chosenIds = shown.filter { it.selected }
        .mapTo(mutableSetOf()) { it.suggestion.suggestion_id?.value_ }
      return result.suggestions.map { suggestion ->
        val id = suggestion.suggestion_id?.value_
        StarterPackItem(
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
