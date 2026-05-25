package dev.fanfly.wingslog.feature.attachment.datamanager

// TODO(attachments-on-web): real SHA-256 (Web Crypto is async; needs a sync impl or a suspend
// rework). Attachments are gated off on web, so blob hashing is never invoked here today.
actual fun sha256Hex(bytes: ByteArray): String =
  throw NotImplementedError("sha256Hex is not supported on web yet (attachments are disabled)")
