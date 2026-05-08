package dev.fanfly.wingslog.feature.attachment.datamanager

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun sha256Hex(bytes: ByteArray): String {
  val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
  digest.usePinned { dPinned ->
    if (bytes.isEmpty()) {
      CC_SHA256(null, 0u, dPinned.addressOf(0))
    } else {
      bytes.usePinned { pinned ->
        CC_SHA256(pinned.addressOf(0), bytes.size.toUInt(), dPinned.addressOf(0))
      }
    }
  }
  val sb = StringBuilder(digest.size * 2)
  for (b in digest) {
    val v = b.toInt() and 0xff
    sb.append(HEX[v ushr 4])
    sb.append(HEX[v and 0x0f])
  }
  return sb.toString()
}

private val HEX = "0123456789abcdef".toCharArray()
