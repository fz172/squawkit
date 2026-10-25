package dev.fanfly.wingslog.feature.tasks.dashboard.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.QuickActionKind
import dev.fanfly.wingslog.core.analytics.QuickActionSource
import dev.fanfly.wingslog.core.analytics.QuickActionSurface
import dev.fanfly.wingslog.core.analytics.RecordQuickAction
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.template.CurrentReading
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.comments.datamanager.CommentThreadController
import dev.fanfly.wingslog.feature.comments.datamanager.RecordCommentHost
import dev.fanfly.wingslog.feature.comments.model.CommentAction
import dev.fanfly.wingslog.feature.comments.model.CommentParentKind
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.datamanager.defaultMeterKey
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.feature.comments.sharedassets.generated.resources.comment_delete_failed
import wingslog.feature.comments.sharedassets.generated.resources.comment_edit_failed
import wingslog.feature.comments.sharedassets.generated.resources.comment_post_failed
import wingslog.feature.tasks.sharedassets.generated.resources.task_deleted
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.comments.sharedassets.generated.resources.Res as CommentsRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as TasksRes

/**
 * Owns task detail-sheet selection, comment thread hosting, swipe quick-actions, and skip/delete
 * confirmations for a Thing's dashboard and Tasks tab.
 */
class TaskDetailViewModel(
  private val taskDataManager: TaskDataManager,
  private val taskStatusManager: TaskStatusManager,
  private val logManager: MaintenanceLogManager,
  commentManager: CommentManager,
  private val templateRegistry: TemplateRegistry,
  private val analytics: AnalyticsManager,
  private val thingId: String,
  private val templateId: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow(TaskDetailUiState())
  val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

  private val commentHost = RecordCommentHost(commentManager, viewModelScope)

  /** The open detail sheet's comment thread; null while no task sheet is showing. */
  val commentThread: StateFlow<CommentThreadController?> = commentHost.thread

  private val _events = Channel<UiText>(Channel.BUFFERED)
  val events: Flow<UiText> = _events.receiveAsFlow()

  private var cachedTasks: List<MaintenanceTaskWithStatus> = emptyList()
  private var cachedLogs: List<MaintenanceLog> = emptyList()
  private var cachedReadings: List<CurrentReading> = emptyList()
  private var pendingSkipCard: MaintenanceTask? = null

  /** Due status depends on the clock, not only on stored data; re-evaluate when the screen returns. */
  fun onResumed() {
    taskStatusManager.refreshDueStatus()
  }

  init {
    viewModelScope.launch {
      combine(
        taskStatusManager.observeTasksWithStatus(thingId)
          .distinctUntilChanged()
          .catch { emit(emptyList()) },
        logManager.observeLogs(thingId)
          .distinctUntilChanged()
          .catch { emit(emptyList()) },
      ) { tasks, logs ->
        cachedTasks = tasks
        cachedLogs = logs
        _uiState.update { state ->
          val currentSelected = state.selectedTask ?: return@update state
          val refreshed = tasks.find { it.card.id == currentSelected.card.id }
          val relevantLogs = refreshed?.let { sel ->
            logs.filter { sel.card.id in it.inspection_ids }
              .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
          } ?: emptyList()
          state.copy(
            selectedTask = refreshed,
            logsForSelectedTask = relevantLogs,
          )
        }
      }.collect {}
    }
    viewModelScope.launch {
      logManager.observeCurrentReadings(thingId)
        .distinctUntilChanged()
        .catch { emit(emptyList()) }
        .collect { readings ->
          cachedReadings = readings
        }
    }
    viewModelScope.launch {
      _uiState
        .map { state ->
          state.selectedTask?.let {
            CommentTarget(thingId, it.card.id, CommentParentKind.MAINTENANCE_TASK)
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

  fun showDetail(cardWithStatus: MaintenanceTaskWithStatus) {
    val relevantLogs = cachedLogs
      .filter { cardWithStatus.card.id in it.inspection_ids }
      .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
    _uiState.update {
      it.copy(
        selectedTask = cardWithStatus,
        logsForSelectedTask = relevantLogs,
      )
    }
  }

  fun dismissDetail() {
    _uiState.update {
      it.copy(
        selectedTask = null,
        logsForSelectedTask = emptyList(),
      )
    }
  }

  fun onResolveClick(task: MaintenanceTaskWithStatus) {
    _uiState.update { it.copy(resolvingTaskId = task.card.id) }
  }

  fun dismissResolveMenu() {
    _uiState.update { it.copy(resolvingTaskId = null) }
  }

  fun onCreateLogClick() {
    logQuickAction(QuickActionKind.RESOLVE)
    _uiState.update { it.copy(resolvingTaskId = null) }
  }

  fun onSkipClick(task: MaintenanceTaskWithStatus) {
    pendingSkipCard = task.card
    _uiState.update {
      it.copy(
        resolvingTaskId = null,
        skippingTaskId = task.card.id,
      )
    }
  }

  fun cancelSkip() {
    pendingSkipCard = null
    _uiState.update { it.copy(skippingTaskId = null) }
  }

  fun confirmSkip() {
    val cardId = _uiState.value.skippingTaskId ?: return
    val card = cachedTasks.find { it.card.id == cardId }?.card ?: pendingSkipCard ?: return
    viewModelScope.launch {
      val template = templateRegistry.canonicalById(templateId)
      val meterKey = card.defaultMeterKey()
      val reading = if (template == null || template.meters.any { it.key == meterKey }) {
        cachedReadings.firstOrNull { it.meterKey == meterKey }?.value?.toFloat() ?: 0f
      } else {
        0f
      }
      taskDataManager.skipCycle(thingId, card, reading)
        .onSuccess {
          logQuickAction(QuickActionKind.SKIP)
        }
      pendingSkipCard = null
      _uiState.update { it.copy(skippingTaskId = null) }
    }
  }

  fun onDeleteClick(task: MaintenanceTaskWithStatus) {
    _uiState.update {
      it.copy(
        resolvingTaskId = null,
        deletingTaskId = task.card.id,
        deletingTaskTitle = task.card.title,
      )
    }
  }

  fun cancelDelete() {
    _uiState.update {
      it.copy(
        deletingTaskId = null,
        deletingTaskTitle = "",
      )
    }
  }

  fun confirmDelete() {
    val cardId = _uiState.value.deletingTaskId ?: return
    viewModelScope.launch {
      taskDataManager.deleteTask(thingId, cardId)
        .onSuccess {
          logQuickAction(QuickActionKind.DELETE)
          _uiState.update {
            it.copy(
              deletingTaskId = null,
              deletingTaskTitle = "",
              selectedTask = null,
              logsForSelectedTask = emptyList(),
            )
          }
          _events.send(
            UiText.StringRes(
              TasksRes.string.task_deleted,
              listOf(taskWord()),
            )
          )
        }
        .onFailure {
          _uiState.update {
            it.copy(
              deletingTaskId = null,
              deletingTaskTitle = "",
            )
          }
          _events.send(UiText.StringRes(CoreRes.string.delete_failed))
        }
    }
  }

  private fun logQuickAction(action: QuickActionKind) {
    analytics.log(
      RecordQuickAction(
        templateId = templateId,
        surface = QuickActionSurface.TASKS,
        action = action,
        source = QuickActionSource.SWIPE,
      )
    )
  }

  private fun taskWord(): String = LexiconFormatter.sentenceCase(
    templateRegistry.lexiconFor(templateRegistry.canonicalById(templateId)).taskNoun
  )
}
