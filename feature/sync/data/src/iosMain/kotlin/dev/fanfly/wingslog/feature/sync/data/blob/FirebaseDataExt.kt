package dev.fanfly.wingslog.feature.sync.data.blob

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData

@OptIn(ExperimentalForeignApi::class)
actual fun ByteArray.toFirebaseData(): Data {
  val nsData = if (isEmpty()) {
    NSData()
  } else {
    usePinned { pinned ->
      NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }
  }
  return Data(nsData)
}
