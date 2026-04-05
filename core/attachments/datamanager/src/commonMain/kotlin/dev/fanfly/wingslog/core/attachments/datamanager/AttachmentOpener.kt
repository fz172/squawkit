package dev.fanfly.wingslog.core.attachments.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import kotlinx.coroutines.flow.Flow

/**
 * Opens an attachment using platform-native capabilities.
 *
 * - [Attachment.ATTACHMENT_TYPE_LINK] — opens [Attachment.url] in the default browser.
 * - All file types — downloads [Attachment.download_url] to local storage, then opens with
 *   the platform's native viewer (DownloadManager on Android, URLSession on iOS).
 */
interface AttachmentOpener {
  fun open(attachment: Attachment): Flow<OpenState>
}
