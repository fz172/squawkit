package dev.fanfly.wingslog.feature.attachment.datamanager

/**
 * SHA-256 of [bytes], lowercase hex (no separators). Used by [LocalBlobStore.put] to compute the
 * canonical digest that ends up in the proto's `Attachment.sha256` and by
 * [LocalBlobStore.installDownloaded] to verify integrity on download.
 */
expect fun sha256Hex(bytes: ByteArray): String
