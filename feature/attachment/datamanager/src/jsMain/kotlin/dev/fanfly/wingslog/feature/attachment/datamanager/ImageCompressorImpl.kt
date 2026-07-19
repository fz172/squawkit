package dev.fanfly.wingslog.feature.attachment.datamanager

import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob

private const val MAX_DOWNSCALE_ATTEMPTS = 3

/**
 * Web [ImageCompressor]. Decodes with `createImageBitmap`, draws the bitmap onto an
 * `OffscreenCanvas` scaled to [MAX_IMAGE_DIMENSION] on the longest side, then JPEG-encodes via
 * `convertToBlob`, lowering quality (then scale) until the result fits [MAX_IMAGE_BYTES] — the same
 * ladder the Android and iOS implementations use.
 *
 * The browser exposes no synchronous image encoder, so decode and encode are both async; that is
 * why [ImageCompressor.compressToJpeg] is `suspend`. Any failure (undecodable bytes, no
 * `OffscreenCanvas`, a result that isn't smaller) returns `null`, and the caller stores the
 * original bytes unchanged.
 *
 * `image/heic`/`image/heif` reach here (they are compressible mimes) but most browsers cannot
 * decode them; `createImageBitmap` rejects, we catch, and the original HEIC is stored as-is.
 */
internal class ImageCompressorImpl : ImageCompressor {

  override suspend fun compressToJpeg(bytes: ByteArray): ByteArray? {
    // Already within budget: re-encoding could only lose quality for no size win. Matches mobile.
    if (bytes.size <= MAX_IMAGE_BYTES) return null
    return try {
      val sourceBlob = Blob(arrayOf<dynamic>(bytes.toUint8Array()))
      val bitmapPromise = createImageBitmap(sourceBlob)
      val bitmap = bitmapPromise.await()
      try {
        val srcW = (bitmap.width as Int)
        val srcH = (bitmap.height as Int)
        var maxDimension = MAX_IMAGE_DIMENSION
        var out: ByteArray? = null
        var attempt = 0
        while (true) {
          out = encodeScaled(bitmap, srcW, srcH, maxDimension) ?: return null
          if (out.size <= MAX_IMAGE_BYTES || attempt >= MAX_DOWNSCALE_ATTEMPTS) break
          maxDimension = (maxDimension * 0.7f).toInt()
          attempt++
        }
        val result = out ?: return null
        // Defensive: if we somehow produced a larger file, keep the original.
        if (result.size >= bytes.size) null else result
      } finally {
        // Release the decoded bitmap's off-heap memory promptly rather than waiting for GC.
        bitmap.close()
      }
    } catch (e: Throwable) {
      // Undecodable bytes, missing OffscreenCanvas, HEIC the browser can't read: store as-is.
      null
    }
  }

  /**
   * Draws [bitmap] scaled so its longest side is at most [maxDimension] onto an `OffscreenCanvas`,
   * then re-encodes JPEG at descending quality until the bytes fit [MAX_IMAGE_BYTES] (or quality
   * bottoms out). Returns `null` if a 2D context is unavailable.
   */
  private suspend fun encodeScaled(
    bitmap: dynamic,
    srcW: Int,
    srcH: Int,
    maxDimension: Int,
  ): ByteArray? {
    val longestSide = maxOf(srcW, srcH)
    val scale = if (longestSide > maxDimension) maxDimension.toDouble() / longestSide else 1.0
    val targetW = maxOf(1, (srcW * scale).toInt())
    val targetH = maxOf(1, (srcH * scale).toInt())

    val canvas = OffscreenCanvas(targetW, targetH)
    val context = canvas.getContext("2d") ?: return null
    context.drawImage(bitmap, 0, 0, targetW, targetH)

    var quality = 90
    var best: ByteArray
    while (true) {
      val encodedPromise = canvas.convertToBlob(jpegOptions(quality))
      val encoded = encodedPromise.await()
      best = encoded.readBytes()
      if (best.size <= MAX_IMAGE_BYTES || quality <= 40) break
      quality -= 15
    }
    return best
  }

  private fun jpegOptions(quality: Int): dynamic {
    val options = js("({})")
    options.type = "image/jpeg"
    // convertToBlob quality is 0..1; our ladder tracks 40..90 like the mobile encoders.
    options.quality = quality.toDouble() / 100.0
    return options
  }

  private suspend fun Blob.readBytes(): ByteArray {
    val bufferPromise = asDynamic().arrayBuffer()
      .unsafeCast<Promise<ArrayBuffer>>()
    val buffer = bufferPromise.await()
    return Uint8Array(buffer).toByteArray()
  }

  private fun ByteArray.toUint8Array(): Uint8Array =
    Uint8Array(toTypedArray())

  private fun Uint8Array.toByteArray(): ByteArray =
    ByteArray(length) { index ->
      asDynamic()[index].unsafeCast<Int>()
        .toByte()
    }
}

/** Global `createImageBitmap(Blob)` — available on both `Window` and worker global scopes. */
private external fun createImageBitmap(image: Blob): Promise<dynamic>

private external class OffscreenCanvas(width: Int, height: Int) {
  fun getContext(contextId: String): dynamic
  fun convertToBlob(options: dynamic = definedExternally): Promise<Blob>
}
