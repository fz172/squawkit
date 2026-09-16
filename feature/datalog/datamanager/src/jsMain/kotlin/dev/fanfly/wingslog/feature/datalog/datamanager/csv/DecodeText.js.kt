package dev.fanfly.wingslog.feature.datalog.datamanager.csv

import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/**
 * The browser's own decoder, which is native code rather than a loop over a `StringBuilder`.
 *
 * Non-fatal by default, which is the behaviour this wants: a malformed byte becomes a replacement
 * character instead of an exception.
 */
private external class TextDecoder(label: String = definedExternally) {
  fun decode(input: Uint8Array): String
}

internal actual fun decodeText(bytes: ByteArray): String {
  if (bytes.isEmpty()) return ""
  val signed = bytes.unsafeCast<Int8Array>()
  return TextDecoder("utf-8")
    .decode(Uint8Array(signed.buffer, signed.byteOffset, signed.length))
}
