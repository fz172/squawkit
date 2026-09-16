package dev.fanfly.wingslog.feature.datalog.datamanager.csv

/** A real UTF-8 decoder in the runtime already; 10 ms for 46 MB, so nothing to improve on. */
internal actual fun decodeText(bytes: ByteArray): String =
  bytes.decodeToString(throwOnInvalidSequence = false)
