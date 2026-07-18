package dev.fanfly.wingslog.feature.attachment.datamanager

/**
 * Web [ImageCompressor] — not implemented yet. Canvas-based re-encoding is a follow-up; until
 * then picked photos are stored at their original size on web. Returning null means "leave the
 * bytes as-is", so the attachment flow works unchanged.
 */
class ImageCompressorImpl : ImageCompressor {
  override fun compressToJpeg(bytes: ByteArray): ByteArray? = null
}
