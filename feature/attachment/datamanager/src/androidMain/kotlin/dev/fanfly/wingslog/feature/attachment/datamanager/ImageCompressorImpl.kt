package dev.fanfly.wingslog.feature.attachment.datamanager

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

private const val TAG = "ImageCompressor"
private const val MAX_DOWNSCALE_ATTEMPTS = 3

/**
 * Android [ImageCompressor]. Decodes with EXIF orientation applied, downsamples to
 * [MAX_IMAGE_DIMENSION], and re-encodes JPEG, stepping quality (then scale) down until the
 * result fits [MAX_IMAGE_BYTES].
 */
class ImageCompressorImpl : ImageCompressor {

  override fun compressToJpeg(bytes: ByteArray): ByteArray? {
    // Cheap early out: already within budget, so re-encoding could only lose quality for no
    // size win. Also skips the camera's already-small photos on the rare path they reach here.
    if (bytes.size <= MAX_IMAGE_BYTES) return null
    return try {
      var maxDimension = MAX_IMAGE_DIMENSION
      var out: ByteArray
      var attempt = 0
      while (true) {
        val bitmap = decodeBounded(bytes, maxDimension)
        out = try {
          encodeJpeg(bitmap, MAX_IMAGE_BYTES)
        } finally {
          bitmap.recycle()
        }
        if (out.size <= MAX_IMAGE_BYTES || attempt >= MAX_DOWNSCALE_ATTEMPTS) break
        maxDimension = (maxDimension * 0.7f).toInt()
        attempt++
      }
      // Defensive: if we somehow produced a larger file, keep the original.
      if (out.size >= bytes.size) null else out
    } catch (e: Exception) {
      Log.w(TAG, "Failed to compress image; storing original", e)
      null
    }
  }

  /** Decodes with EXIF orientation applied, downsampled to at most [maxDimension] on a side. */
  private fun decodeBounded(bytes: ByteArray, maxDimension: Int): Bitmap {
    val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
      val longestSide = maxOf(info.size.width, info.size.height)
      if (longestSide > maxDimension) {
        decoder.setTargetSampleSize((longestSide / maxDimension).coerceAtLeast(1))
      }
      // ImageDecoder defaults to a hardware-backed Bitmap, which Bitmap.compress() cannot read —
      // it throws IllegalStateException. Force software allocation so JPEG re-encoding works.
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
  }

  private fun encodeJpeg(bitmap: Bitmap, maxBytes: Long): ByteArray {
    var quality = 90
    var bytes: ByteArray
    while (true) {
      val stream = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
      bytes = stream.toByteArray()
      if (bytes.size <= maxBytes || quality <= 40) break
      quality -= 15
    }
    return bytes
  }
}
