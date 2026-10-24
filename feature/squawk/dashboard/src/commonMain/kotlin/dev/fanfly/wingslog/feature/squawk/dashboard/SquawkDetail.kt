package dev.fanfly.wingslog.feature.squawk.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.comments.dashboard.RecordCommentComposer
import dev.fanfly.wingslog.feature.comments.dashboard.RecordCommentThread
import dev.fanfly.wingslog.feature.comments.datamanager.CommentThreadController
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.dataLogIdOrNull
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkDetailSheet
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The open squawk's detail: a pane beside the list on wide tiers, a sheet on a phone. Null while no squawk is selected. */
@Composable
internal fun squawkDetailFor(
  thingId: String,
  isAnonymous: Boolean,
  actionsState: SquawkActionsUiState,
  onDismiss: () -> Unit,
  canMutate: Boolean,
  onFixedClick: (squawkId: String) -> Unit,
  onDismissClick: (squawkId: String) -> Unit,
  onReopenClick: (squawkId: String) -> Unit,
  onEditClick: (squawkId: String) -> Unit,
  onLogClick: ((logId: String) -> Unit)?,
  onOpenDataLog: ((DataLogId) -> Unit)?,
  commentThread: CommentThreadController?,
): (@Composable () -> Unit)? {
  val attachmentManager: AttachmentManager = koinInject()
  val attachmentOpener: AttachmentOpener = koinInject()
  val dataLogManager: DataLogManager = koinInject()
  val coroutineScope = rememberCoroutineScope()
  var openError by remember { mutableStateOf<String?>(null) }
  return actionsState.selectedSquawk?.let { selected ->
    {
      val syncStates by remember(
        thingId,
        attachmentManager,
        attachmentOpener
      ) {
        attachmentManager.observeThingSyncStates(thingId, attachmentOpener)
      }.collectAsStateWithLifecycle(emptyMap())
      val dataLogs by remember(thingId, dataLogManager) {
        dataLogManager.observeRowInfos(ThingId(thingId))
      }.collectAsStateWithLifecycle(null)
      SquawkDetailSheet(
        item = selected,
        addressingLog = actionsState.logForSelectedSquawk,
        onLogClick = onLogClick?.let { navigateToLog ->
          { logId ->
            onDismiss()
            navigateToLog(logId)
          }
        },
        onDismiss = {
          openError = null
          onDismiss()
        },
        onAttachmentTap = { attachment ->
          openError = null
          attachment.dataLogIdOrNull()
            ?.let { dataLogId ->
              onDismiss()
              onOpenDataLog?.invoke(dataLogId)
              return@SquawkDetailSheet
            }
          val openFlow = attachmentOpener.open(attachment)
          coroutineScope.launch {
            openFlow.collect { openState ->
              if (openState is OpenState.Failed) openError =
                openState.error.message
            }
          }
        },
        syncStates = syncStates,
        dataLogs = dataLogs,
        openError = openError,
        onFixedClick = if (canMutate) {
          {
            onDismiss()
            onFixedClick(selected.squawk.id)
          }
        } else null,
        onDismissNoWorkPlanned = if (canMutate) {
          {
            onDismiss()
            onDismissClick(selected.squawk.id)
          }
        } else null,
        comments = commentThread?.let { thread -> { RecordCommentThread(thread) } },
        commentComposer = commentThread?.let { thread ->
          { RecordCommentComposer(thread, isAnonymous) }
        },
        onReopenClick = if (canMutate) {
          {
            onDismiss()
            onReopenClick(selected.squawk.id)
          }
        } else null,
        onEditClick = if (canMutate) {
          {
            onDismiss()
            onEditClick(selected.squawk.id)
          }
        } else null,
      )
    }
  }
}
