package dev.fanfly.wingslog.feature.dashboard.host

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.TemplateResolution
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewEvent
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.id.ThingId
import dev.gitlive.firebase.auth.FirebaseAuth
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.core.sharedassets.generated.resources.delete_failed

class ThingOverviewViewModel(
  private val fleetManager: FleetManager,
  private val sharingManager: SharingManager,
  private val templateRegistry: TemplateRegistry,
  private val auth: FirebaseAuth,
  private val thingId: ThingId,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow<ThingOverviewUiState>(ThingOverviewUiState.Loading)
  val uiState: StateFlow<ThingOverviewUiState> = _uiState.asStateFlow()

  private val _events = Channel<ThingOverviewEvent>(Channel.BUFFERED)

  /** Navigation and snackbar events; `ThingSectionContent` collects them (design §7). */
  val events: Flow<ThingOverviewEvent> = _events.receiveAsFlow()

  init {
    loadThing()
  }

  private fun loadThing() {
    viewModelScope.launch {
      _uiState.update { ThingOverviewUiState.Loading }
      combine(
        fleetManager.loadThing(thingId)
          .distinctUntilChanged(),
        // The caller's role on this thing, resolved locally (own ⇒ OWNER, shared ⇒ ref role).
        // Gates owner-only affordances in the UI; server rules remain the real enforcement (§6.3).
        sharingManager.observeMyRole(thingId.value)
          .distinctUntilChanged(),
        sharingManager.observeIsShared(thingId.value)
          .distinctUntilChanged(),
      ) { thing, myRole, isShared ->
        val degraded = thing?.let {
          templateRegistry.resolve(it) as? TemplateResolution.Degraded
        }
        if (degraded != null) {
          ThingOverviewUiState.Degraded(thing, degraded.reason)
        } else if (thing != null) {
          ThingOverviewUiState.Success(
            thing = thing,
            myRole = myRole,
            shared = isShared,
            isAnonymous = auth.currentUser?.isAnonymous ?: true,
          )
        } else {
          ThingOverviewUiState.Error
        }
      }.collect { state ->
        _uiState.update { state }
      }
    }
  }

  fun onAction(action: ThingOverviewAction) {
    when (action) {
      ThingOverviewAction.BackClick -> {
        viewModelScope.launch { _events.send(ThingOverviewEvent.NavigateBack) }
      }

      is ThingOverviewAction.EditClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToEditThing(
              action.thingId
            )
          )
        }
      }

      ThingOverviewAction.DeleteConfirm -> {
        deleteThing()
      }

      is ThingOverviewAction.ManageAccessClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToManageAccess(
              action.thingId
            )
          )
        }
      }

      is ThingOverviewAction.AddLogClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToAddLog(
              action.thingId
            )
          )
        }
      }

      is ThingOverviewAction.EditLogClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToEditLog(
              action.thingId,
              action.logId
            )
          )
        }
      }

      is ThingOverviewAction.AddTaskClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToAddTask(
              action.thingId
            )
          )
        }
      }

      // Navigation only — ThingSectionContent drives the navController and never forwards it.
      is ThingOverviewAction.BrowseSuggestionsClick -> Unit

      // Navigation only; ThingSectionContent routes it.
      is ThingOverviewAction.OpenDataLogClick -> Unit

      is ThingOverviewAction.EditTaskClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToEditTask(
              action.thingId,
              action.cardId
            )
          )
        }
      }

      is ThingOverviewAction.AddSquawkClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToAddSquawk(
              action.thingId
            )
          )
        }
      }

      is ThingOverviewAction.EditSquawkClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToEditSquawk(
              action.thingId,
              action.squawkId
            )
          )
        }
      }

      // Navigation only — ThingSectionContent drives the navController and never forwards it.
      is ThingOverviewAction.SquawkFixedClick -> Unit
      is ThingOverviewAction.TaskCreateLogClick -> Unit
    }
  }

  fun deleteThing() {
    viewModelScope.launch {
      fleetManager.deleteThing(thingId)
        .onSuccess {
          _events.send(ThingOverviewEvent.NavigateBack)
        }
        .onFailure { error ->
          _events.send(
            ThingOverviewEvent.ShowMessage(
              error.message?.let { UiText.DynamicString(it) }
                ?: UiText.StringRes(CoreRes.string.delete_failed)
            )
          )
        }
    }
  }
}
