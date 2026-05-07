package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AttachmentOpenerIos : AttachmentOpener {

  private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
  override val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

  override fun open(attachment: Attachment): Flow<OpenState> = flow {
    if (_downloadingIds.value.contains(attachment.id)) return@flow

    if (attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK) {
      _downloadingIds.update { it + attachment.id }
    }

    try {
      emit(OpenState.Downloading)
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
    } finally {
      if (attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK) {
        _downloadingIds.update { it - attachment.id }
      }
    }
  }
}
