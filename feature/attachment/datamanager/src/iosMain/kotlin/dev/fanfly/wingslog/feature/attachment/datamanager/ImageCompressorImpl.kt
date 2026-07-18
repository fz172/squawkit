package dev.fanfly.wingslog.feature.attachment.datamanager

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/**
 * iOS [ImageCompressor]. Decodes via [UIImage], scales the longest side to [MAX_IMAGE_DIMENSION],
 * and JPEG-encodes, lowering quality (then scale) until the result fits [MAX_IMAGE_BYTES].
 */
class ImageCompressorImpl : ImageCompressor {

  @OptIn(ExperimentalForeignApi::class)
  override fun compressToJpeg(bytes: ByteArray): ByteArray? {
    if (bytes.size <= MAX_IMAGE_BYTES) return null
    val image = UIImage(data = bytes.toNSData()) ?: return null
    val data = compress(image) ?: return null
    // Defensive: never grow the file.
    if (data.length.toLong() >= bytes.size) return null
    return data.toByteArray()
  }

  @OptIn(ExperimentalForeignApi::class)
  private fun compress(original: UIImage): NSData? {
    var image = original.resizedIfNeeded(MAX_IMAGE_DIMENSION.toDouble())
    var quality = 0.9
    var data = UIImageJPEGRepresentation(image, quality)
    while ((data == null || data.length.toLong() > MAX_IMAGE_BYTES) && quality > 0.15) {
      quality -= 0.15
      data = UIImageJPEGRepresentation(image, quality)
    }
    if (data == null || data.length.toLong() > MAX_IMAGE_BYTES) {
      image = image.resizedIfNeeded(MAX_IMAGE_DIMENSION * 0.6)
      data = UIImageJPEGRepresentation(image, 0.6)
    }
    return data
  }

  @OptIn(ExperimentalForeignApi::class)
  private fun UIImage.resizedIfNeeded(maxDimension: Double): UIImage {
    val (width, height) = size.useContents { width to height }
    val longestSide = maxOf(width, height)
    if (longestSide <= maxDimension) return this
    val scale = maxDimension / longestSide
    val newSize = CGSizeMake(width * scale, height * scale)
    UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
    try {
      drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
      return UIGraphicsGetImageFromCurrentImageContext() ?: this
    } finally {
      UIGraphicsEndImageContext()
    }
  }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
  NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
  val length = length.toInt()
  val out = ByteArray(length)
  if (length > 0) {
    out.usePinned { pinned ->
      memcpy(pinned.addressOf(0), bytes, length.toULong())
    }
  }
  return out
}
