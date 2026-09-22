package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.domDataTransferOrNull
import kotlinx.browser.document
import org.w3c.dom.DragEvent
import org.w3c.dom.events.Event

@Composable
internal actual fun rememberDroppedFileReader(): DroppedFileReader =
  WebDroppedFileReader

// Compose for web handles no dragleave, so a drag carried out of the window or cancelled with Esc
// never ends the session. Leaving the page is the dragleave with no element to go to.
@Composable
internal actual fun DragLeftWindowEffect(onLeft: () -> Unit) {
  val currentOnLeft by rememberUpdatedState(onLeft)
  DisposableEffect(Unit) {
    val listener: (Event) -> Unit = { event ->
      if (event.unsafeCast<DragEvent>().relatedTarget == null) currentOnLeft()
    }
    document.addEventListener("dragleave", listener)
    onDispose { document.removeEventListener("dragleave", listener) }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
private object WebDroppedFileReader : DroppedFileReader {
  // A file drag lists "Files" among its types from dragenter on; the files themselves stay hidden
  // until the drop.
  override fun carriesFiles(event: DragAndDropEvent): Boolean =
    event.transferData?.domDataTransferOrNull?.types?.contains("Files") == true

  override fun claim(event: DragAndDropEvent): (suspend () -> DroppedFiles)? {
    val transfer = event.transferData?.domDataTransferOrNull ?: return null
    // The browser empties the DataTransfer once the drop handler returns; the File handles survive.
    val handles = fileHandles(transfer.asDynamic().files)
    if (handles.isEmpty()) return null
    return { readBrowserFiles(handles) }
  }
}
