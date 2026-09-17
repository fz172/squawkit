package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.attachment.sharedassets.generated.resources.file_drop_hint
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

/**
 * Accepts files dragged in from outside the app (design §13.3, PRD R2c): the browser, an iPad, or
 * an Android tablet. Dropped files arrive as [PickedFile]s in the same shape the file picker
 * returns, so callers hand them to the handler they already give [rememberFilePicker].
 */
@Composable
fun FileDropTarget(
  enabled: Boolean,
  onDrop: (List<PickedFile>) -> Unit,
  modifier: Modifier = Modifier,
  onReadError: () -> Unit = {},
  content: @Composable BoxScope.() -> Unit,
) {
  val reader = rememberDroppedFileReader()
  val scope = rememberCoroutineScope()
  val currentOnDrop by rememberUpdatedState(onDrop)
  val currentOnReadError by rememberUpdatedState(onReadError)
  var hovering by remember { mutableStateOf(false) }
  LaunchedEffect(enabled) { if (!enabled) hovering = false }
  if (hovering) DragLeftWindowEffect { hovering = false }

  val target = remember(reader, scope) {
    object : DragAndDropTarget {
      override fun onEntered(event: DragAndDropEvent) {
        hovering = true
      }

      override fun onExited(event: DragAndDropEvent) {
        hovering = false
      }

      override fun onEnded(event: DragAndDropEvent) {
        hovering = false
      }

      override fun onDrop(event: DragAndDropEvent): Boolean {
        hovering = false
        val read = reader.claim(event) ?: return false
        scope.launch {
          val dropped = read()
          if (dropped.anyFailed) currentOnReadError()
          if (dropped.files.isNotEmpty()) currentOnDrop(dropped.files)
        }
        return true
      }
    }
  }

  val dropModifier = if (enabled) {
    Modifier.dragAndDropTarget(
      shouldStartDragAndDrop = reader::carriesFiles,
      target = target,
    )
  } else {
    Modifier
  }
  Box(modifier = modifier.then(dropModifier), propagateMinConstraints = true) {
    content()
    if (hovering) DropHighlight()
  }
}

@Composable
private fun BoxScope.DropHighlight() {
  val accent = MaterialTheme.colorScheme.primary
  val shape = RoundedCornerShape(Spacing.cardCornerRadius)
  Box(
    modifier = Modifier
      .matchParentSize()
      .background(accent.copy(alpha = 0.12f), shape)
      .border(Spacing.hairline, accent, shape),
    contentAlignment = Alignment.Center,
  ) {
    Surface(shape = shape, color = accent) {
      Text(
        text = stringResource(AttachRes.string.file_drop_hint),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
      )
    }
  }
}

/** What a drop yielded: the files that could be read, and whether any could not. */
internal class DroppedFiles(val files: List<PickedFile>, val anyFailed: Boolean)

/** The platform half of [FileDropTarget]: recognizing a file drag and reading what it carries. */
internal interface DroppedFileReader {
  /** Whether a drag that just entered carries files, rather than text or an in-app payload. */
  fun carriesFiles(event: DragAndDropEvent): Boolean

  /**
   * Takes what the platform only hands out while the drop event is being dispatched, and returns
   * the read to run afterwards; null when the drop carries nothing readable.
   */
  fun claim(event: DragAndDropEvent): (suspend () -> DroppedFiles)?
}

@Composable
internal expect fun rememberDroppedFileReader(): DroppedFileReader

/** Calls [onLeft] when a drag leaves the window without dropping, where the platform sends no end. */
@Composable
internal expect fun DragLeftWindowEffect(onLeft: () -> Unit)
