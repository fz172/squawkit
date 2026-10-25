package dev.fanfly.wingslog.feature.squawk.dashboard.detail

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.QuickActionKind
import dev.fanfly.wingslog.core.analytics.QuickActionSource
import dev.fanfly.wingslog.core.analytics.QuickActionSurface
import dev.fanfly.wingslog.core.analytics.RecordQuickAction
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.comments.datamanager.CommentThreadController
import dev.fanfly.wingslog.feature.comments.datamanager.RecordCommentHost
import dev.fanfly.wingslog.feature.comments.model.CommentAction
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.SquawkDismissReason
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.core.sharedassets.generated.resources.save_failed
import wingslog.feature.comments.sharedassets.generated.resources.comment_delete_failed
import wingslog.feature.comments.sharedassets.generated.resources.comment_edit_failed
import wingslog.feature.comments.sharedassets.generated.resources.comment_post_failed
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_deleted
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.comments.sharedassets.generated.resources.Res as CommentsRes
import wingslog.feature.squawk.sharedassets.generated.resources.Res as SquawkRes

/**
 * Owns squawk detail-sheet selection, comment thread hosting, swipe quick-actions, and
 * dismiss/delete confirmations for a Thing's squawk tab.
 */
class SquawkDetailViewModel(
  private val squawkManager: SquawkManager,
  private val logManager: MaintenanceLogManager,
  commentManager: CommentManager,
  private val templateRegistry: TemplateRegistry,
  private val analytics: AnalyticsManager,
  val thingId: ThingId,
  private val templateId: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SquawkDetailUiState())
  val uiState: StateFlow<SquawkDetailUiState> = _uiState.asStateFlow()

  private val commentHost = RecordCommentHost(commentManager, viewModelScope)

  /** The open detail sheet's comment thread; null while no squawk sheet is showing. */
  val commentThread: StateFlow<CommentThreadController?> = commentHost.thread

  private val _events = Channel<UiText>(Channel.BUFFERED)
  val events: Flow<UiText> = _events.receiveAsFlow()

  private var cachedLogs: List<MaintenanceLog> = emptyList()

  init {
    viewModelScope.launch {
      logManager.observeLogs(thingId.value)
        .distinctUntilChanged()
        .catch { emit(emptyList()) }
        .collect { logs ->
          cachedLogs = logs
          _uiState.update { state ->
            val selected = state.selectedSquawk ?: return@update state
            state.copy(
              logForSelectedSquawk = logs.firstOrNull {
                it.id == selected.squawk.addressed_by_log_id
              },
            )
          }
        }
    }
    viewModelScope.launch {
      _uiState
        .map { state ->
          state.selectedSquawk?.let {
            CommentTarget.squawk(thingId, it.id)
          }
        }
        .distinctUntilChanged()
        .collect(commentHost::show)
    }
    viewModelScope.launch {
      commentHost.errors.collect { action ->
        val message = when (action) {
          CommentAction.POST -> CommentsRes.string.comment_post_failed
          CommentAction.EDIT -> CommentsRes.string.comment_edit_failed
          CommentAction.DELETE -> CommentsRes.string.comment_delete_failed
        }
        _events.send(UiText.StringRes(message))
      }
    }
  }

  fun showDetail(squawk: SquawkWithStatus) {
    val log =
      cachedLogs.firstOrNull { it.id == squawk.squawk.addressed_by_log_id }
    _uiState.update {
      it.copy(
        selectedSquawk = squawk,
        logForSelectedSquawk = log,
      )
    }
  }

  fun dismissDetail() {
    _uiState.update {
      it.copy(
        selectedSquawk = null,
        logForSelectedSquawk = null,
      )
    }
  }

  fun onResolveClick(squawk: SquawkWithStatus) {
    _uiState.update { it.copy(resolvingSquawkId = squawk.id) }
  }

  fun dismissResolveMenu() {
    _uiState.update { it.copy(resolvingSquawkId = null) }
  }

  fun onFixedClick() {
    logQuickAction(QuickActionKind.RESOLVE)
    _uiState.update { it.copy(resolvingSquawkId = null) }
  }

  fun onDismissClick(squawk: SquawkWithStatus) {
    onDismissClick(squawk.id)
  }

  fun onDismissClick(squawkId: SquawkId) {
    _uiState.update {
      it.copy(
        resolvingSquawkId = null,
        dismissingSquawkId = squawkId,
      )
    }
  }

  fun onDismissClick(squawkId: String) = onDismissClick(SquawkId(squawkId))

  fun cancelDismiss() {
    _uiState.update { it.copy(dismissingSquawkId = null) }
  }

  fun confirmDismiss(reason: SquawkDismissReason) {
    val squawkId = _uiState.value.dismissingSquawkId ?: return
    viewModelScope.launch {
      // The manager, and nothing lower: the tombstone it writes is what fans the collaborator
      // notification out (design §8).
      squawkManager.dismissSquawk(thingId, squawkId, reason)
        .onSuccess {
          logQuickAction(QuickActionKind.RESOLVE)
        }
        .onFailure {
          _events.send(UiText.StringRes(CoreRes.string.save_failed))
        }
      _uiState.update { it.copy(dismissingSquawkId = null) }
    }
  }

  fun reopenSquawk(squawkId: SquawkId) {
    viewModelScope.launch {
      squawkManager.reopenSquawk(thingId, squawkId)
        .onFailure {
          _events.send(UiText.StringRes(CoreRes.string.save_failed))
        }
    }
  }

  fun reopenSquawk(squawkId: String) = reopenSquawk(SquawkId(squawkId))

  fun onDeleteClick(squawk: SquawkWithStatus) {
    onDeleteClick(squawk.id)
  }

  fun onDeleteClick(squawkId: SquawkId) {
    _uiState.update {
      it.copy(
        resolvingSquawkId = null,
        deletingSquawkId = squawkId,
      )
    }
  }

  fun onDeleteClick(squawkId: String) = onDeleteClick(SquawkId(squawkId))

  fun cancelDelete() {
    _uiState.update { it.copy(deletingSquawkId = null) }
  }

  fun confirmDelete() {
    val squawkId = _uiState.value.deletingSquawkId ?: return
    viewModelScope.launch {
      squawkManager.deleteSquawk(thingId, squawkId)
        .onSuccess {
          logQuickAction(QuickActionKind.DELETE)
          _uiState.update {
            it.copy(
              deletingSquawkId = null,
              resolvingSquawkId = null,
              selectedSquawk = null,
              logForSelectedSquawk = null,
            )
          }
          _events.send(
            UiText.StringRes(
              SquawkRes.string.squawk_deleted,
              listOf(squawkWord()),
            )
          )
        }
        // The card stays and the dialog closes: the record is still there to try again on (R14).
        .onFailure {
          _uiState.update { it.copy(deletingSquawkId = null) }
          _events.send(UiText.StringRes(CoreRes.string.delete_failed))
        }
    }
  }

  private fun logQuickAction(action: QuickActionKind) {
    analytics.log(
      RecordQuickAction(
        templateId = templateId,
        surface = QuickActionSurface.SQUAWKS,
        action = action,
        source = QuickActionSource.SWIPE,
      )
    )
  }

  private fun squawkWord(): String = LexiconFormatter.sentenceCase(
    templateRegistry.lexiconFor(templateRegistry.canonicalById(templateId)).squawkNoun
  )
}
