package dev.fanfly.wingslog.core.attachments.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Opens an attachment using platform-native capabilities.
 *
 * - [Attachment.ATTACHMENT_TYPE_LINK] — opens [Attachment.url] in the default browser.
 * - All file types — downloads [Attachment.download_url] to local storage, then opens with
 *   the platform's native viewer (DownloadManager on Android, URLSession on iOS).
 */
interface AttachmentOpener {
  /** IDs of attachments currently being downloaded by this opener. */
  val downloadingIds: StateFlow<Set<String>>

  fun open(attachment: Attachment): Flow<OpenState>
}
