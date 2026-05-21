package dev.fanfly.wingslog.core.firebase.data

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun ByteArray.toFirebaseData(): Data {
  val nsData: NSData = if (isEmpty()) {
    NSData()
  } else {
    usePinned { pinned ->
      NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
  }
  return Data(nsData)
}
