package dev.fanfly.wingslog.feature.attachment.viewing

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberDroppedFileReader(): DroppedFileReader {
  val activity = LocalActivity.current
  val context = LocalContext.current
  return remember(activity, context) { AndroidDroppedFileReader(activity, context) }
}

// The platform ends the drag session itself when a drag leaves or is cancelled.
@Composable
internal actual fun DragLeftWindowEffect(onLeft: () -> Unit) = Unit

private class AndroidDroppedFileReader(
  private val activity: Activity?,
  private val context: Context,
) : DroppedFileReader {
  // Only the description exists before the drop, and a dragged CSV and dragged text can share
  // text/plain, so any drag with content is taken; claim() ignores a drop with no URIs.
  override fun carriesFiles(event: DragAndDropEvent): Boolean =
    event.toAndroidDragEvent().clipDescription != null

  override fun claim(event: DragAndDropEvent): (suspend () -> DroppedFiles)? {
    val dragEvent = event.toAndroidDragEvent()
    val clip = dragEvent.clipData ?: return null
    val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    if (uris.isEmpty()) return null
    // Another app's URIs are readable only after asking during the drop; the grant lasts until the
    // activity is destroyed, which outlives the attachment copy.
    activity?.requestDragAndDropPermissions(dragEvent)
    return { context.toPickedFiles(uris) }
  }
}
