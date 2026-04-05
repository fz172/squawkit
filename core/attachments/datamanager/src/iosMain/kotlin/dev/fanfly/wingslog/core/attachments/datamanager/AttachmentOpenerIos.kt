package dev.fanfly.wingslog.core.attachments.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class AttachmentOpenerIos : AttachmentOpener {
  override fun open(attachment: Attachment): Flow<OpenState> = flow {
    emit(OpenState.Downloading)
    try {
      val urlString = when (attachment.type) {
        AttachmentType.ATTACHMENT_TYPE_LINK -> attachment.url
        else -> attachment.download_url
      }
      val url = NSURL.URLWithString(urlString)
        ?: throw Exception("Invalid URL: $urlString")
      UIApplication.sharedApplication.openURL(url)
      emit(OpenState.Done)
    } catch (e: Exception) {
      emit(OpenState.Failed(e))
    }
  }
}
