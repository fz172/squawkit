package dev.fanfly.wingslog.feature.attachment.datamanager

/**
 * Re-encodes photo bytes to a smaller JPEG. One implementation per platform (Android `Bitmap`,
 * iOS `UIImage`, web `OffscreenCanvas`).
 *
 * Both the camera-capture and file-picker flows funnel through
 * [AttachmentManager.addPickedFile], so that manager is the single place attachment photos are
 * compressed — the platform capture code no longer compresses on its own.
 */
interface ImageCompressor {
  /**
   * Downscale [bytes] to at most [MAX_IMAGE_DIMENSION] on the longest side and re-encode as JPEG
   * under [MAX_IMAGE_BYTES], stepping quality/scale down as needed.
   *
   * Returns `null` when compression was **not** applied — the bytes could not be decoded, the
   * image is already within budget, or the result would not be smaller — and the caller keeps
   * the original bytes. Never throws for a decode/encode failure; a photo we cannot process is
   * simply stored as-is.
   *
   * `suspend` because the browser has no synchronous image encoder: web decodes via
   * `createImageBitmap` and encodes via `OffscreenCanvas.convertToBlob`, both async. Android and
   * iOS implement it as ordinary CPU-bound work with no suspension point.
   */
  suspend fun compressToJpeg(bytes: ByteArray): ByteArray?
}

/** ~1 MB target and 2048 px longest side — the limits the camera capture path used to enforce. */
const val MAX_IMAGE_BYTES: Long = 1_000_000L
const val MAX_IMAGE_DIMENSION: Int = 2048

/**
 * Mime types we are confident name a photo and can re-encode to JPEG without surprising the user.
 *
 * Deliberately narrow: `image/gif` is excluded because re-encoding to JPEG drops animation, and
 * `image/png` is excluded because flattening its alpha channel onto a background is lossy in a
 * way that hurts screenshots and diagrams — the very things people attach as PNGs. JPEG and HEIC
 * are camera-photo formats where re-encoding is exactly what the user wants.
 */
fun isCompressiblePhotoMime(mimeType: String): Boolean =
  when (mimeType.lowercase()) {
    "image/jpeg", "image/jpg", "image/heic", "image/heif" -> true
    else -> false
  }
