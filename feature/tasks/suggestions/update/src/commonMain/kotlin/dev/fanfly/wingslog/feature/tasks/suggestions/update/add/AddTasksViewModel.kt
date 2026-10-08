package dev.fanfly.wingslog.feature.tasks.suggestions.update.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.nav.SuggestionsMode
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.displayLabel
import dev.fanfly.wingslog.core.template.displaySubtitle
import dev.fanfly.wingslog.feature.attachment.datamanager.FileTooLargeException
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.model.toDocumentsArg
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.JobDocumentReleaser
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Add Tasks sheet, what the task tab's add button opens: the Thing's details the suggestions
 * are based on, manuals to add where the owner has Pro, and *Suggest* or *Create manually*.
 *
 * Asks whether a model run can start, and whether documents are allowed, as soon as *Suggest* can
 * be offered, so the slot between shows the right thing before the user reaches it. *Suggest*
 * hands the picked manuals to the suggestions screen's add mode, which starts the run at once.
 *
 * Picked files are stored and queued to upload as they are picked (design §8.1), through
 * [JobDocumentReleaser], which owns them from then on. No record holds them, so the sheet tells
 * it to let them go when it closes without handing them over.
 */
class AddTasksViewModel(
  fleetManager: FleetManager,
  templateRegistry: TemplateRegistry,
  private val suggestionManager: TaskSuggestionManager,
  suggestEntry: TaskSuggestionEntry,
  private val documents: JobDocumentReleaser,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val thingId: String = checkNotNull(savedStateHandle[Screen.THING_ID])

  private val _uiState = MutableStateFlow(AddTasksUiState())
  val uiState = _uiState.asStateFlow()

  /** The files went to the suggestions screen, whose run lets them go from then on. */
  private var handedOver = false

  init {
    viewModelScope.launch {
      val thing = fleetManager.loadThing(thingId)
        .filterNotNull()
        .first()
      val template = templateRegistry.forThingWithFallback(thing)
      _uiState.update {
        it.copy(
          lexicon = templateRegistry.lexiconFor(template),
          details = thing.displaySubtitle(template)
            .ifBlank { thing.displayLabel(template) },
        )
      }
    }
    viewModelScope.launch {
      var asked = false
      suggestEntry.observe(thingId)
        .collect { entry ->
          _uiState.update { it.copy(entry = entry) }
          if (entry == SuggestEntry.Available && !asked) {
            asked = true
            launch { askAccess() }
          }
        }
    }
  }

  private suspend fun askAccess() {
    // Without documents: that call still says whether they are allowed, where asking with them
    // refuses a free owner's run outright.
    val eligibility = suggestionManager.eligibility(thingId)
    val owner = suggestionManager.isOwner(thingId)
    updateSources {
      it.copy(
        isChecking = false,
        documentsAllowed = eligibility.documentsAllowed,
        isOwner = owner,
        blocked = eligibility.reason.takeIf { !eligibility.allowed },
        availableAt = eligibility.nextAvailableAt.takeIf { !eligibility.allowed },
      )
    }
  }

  /**
   * Files picked for the model to read: each stored on this device and queued to upload at once,
   * so it is likely in Storage by the time the run asks for it. PDFs and images only (PRD R7), up
   * to the per-run count and the AI document size.
   */
  fun onAddDocuments(files: List<PickedFile>) {
    if (!uiState.value.showsManuals || files.isEmpty()) return
    viewModelScope.launch {
      updateSources { it.copy(isAdding = true, problem = null) }
      var problem: DocumentProblem? = null
      for (file in files) {
        if (uiState.value.sources.atLimit) {
          problem = DocumentProblem.TOO_MANY
          break
        }
        if (!file.isReadableDocument()) {
          problem = DocumentProblem.UNSUPPORTED
          continue
        }
        // Stored on a scope that outlives the sheet, and let go of there if the sheet closes
        // meanwhile.
        val stored = documents.pick(thingId, file, QuotaChecker.MAX_AI_DOCUMENT_BYTES)
        stored.fold(
          onSuccess = { added -> updateSources { it.copy(documents = it.documents + added) } },
          onFailure = { e ->
            problem = if (e is FileTooLargeException) {
              DocumentProblem.TOO_LARGE
            } else {
              logger.w(e) { "A picked document was not added" }
              DocumentProblem.NOT_ADDED
            }
          },
        )
      }
      updateSources { it.copy(isAdding = false, problem = problem) }
    }
  }

  /** The picker could not read what was picked. */
  fun onPickFailed() {
    updateSources { it.copy(problem = DocumentProblem.NOT_ADDED) }
  }

  /** ✕ on a manual: out of the run, and its copy let go of (no record holds it). */
  fun onRemoveDocument(attachmentId: String) {
    val removed =
      uiState.value.sources.documents.firstOrNull { it.id == attachmentId }
        ?: return
    updateSources { it.copy(documents = it.documents - removed) }
    documents.letGo(listOf(removed))
  }

  /**
   * *Suggest* where a run can be offered: the suggestions screen's route, which takes the manuals
   * from here on. Where no model run can start today, the curated list, which says why. Null while
   * the answer or a file is still coming.
   */
  fun onSuggest(): String? {
    val state = uiState.value
    if (state.entry != SuggestEntry.Available || !state.canSuggest) return null
    if (state.sources.blocked != null) return Screen.Suggestions.createRoute(
      thingId
    )
    handedOver = true
    return Screen.Suggestions.createRoute(
      thingId,
      SuggestionsMode.ADD,
      state.sources.documents.takeIf { it.isNotEmpty() }
        ?.toDocumentsArg(),
    )
  }

  override fun onCleared() {
    if (!handedOver) documents.letGo(uiState.value.sources.documents)
    super.onCleared()
  }

  private fun updateSources(change: (SourcesState) -> SourcesState) {
    _uiState.update { it.copy(sources = change(it.sources)) }
  }

  private companion object {
    val logger = Logger.withTag("AddTasksViewModel")

    /** What the document reader takes: a PDF, or a photo of pages (PRD R7). */
    fun PickedFile.isReadableDocument(): Boolean =
      mimeType == "application/pdf" || mimeType.startsWith("image/")
  }
}
