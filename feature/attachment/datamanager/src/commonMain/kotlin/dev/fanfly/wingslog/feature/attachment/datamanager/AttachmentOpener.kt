package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.thing.Attachment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Opens an attachment using platform-native capabilities.
 *
 * - [Attachment#ATTACHMENT_TYPE_LINK] — opens [Attachment.url] in the default browser.
 * - All file types — downloads [Attachment.download_url] to local storage, then opens with
 *   the platform's native viewer (DownloadManager on Android, URLSession on iOS).
 */
interface AttachmentOpener {
  /** IDs of attachments currently being downloaded by this opener. */
  val downloadingIds: StateFlow<Set<String>>

  /**
   * [page], 1-based, asks for a PDF to open at that page, as an AI suggestion's citation does
   * (task population PRD R30); without it, the attachment's own `open_page`, the page its record
   * keeps for it. Only the web honours it, through the browser's PDF viewer: Android
   * and iOS hand the file to a viewer that takes no page, so they open it at the start (owner's
   * decision, 2026-10-04: no in-app viewer).
   */
  fun open(attachment: Attachment, page: Int? = null): Flow<OpenState>
}
