package dev.fanfly.wingslog.feature.attachment.viewing

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

actual val isCameraCaptureSupported: Boolean = true

private const val TAG = "CameraCapture"
private const val MAX_PHOTO_BYTES = 1_000_000L
private const val MAX_DIMENSION = 2048
private const val MAX_DOWNSCALE_ATTEMPTS = 3

@Composable
actual fun rememberCameraCapture(
  onResult: (List<PickedFile>) -> Unit,
  onError: () -> Unit,
): () -> Unit {
  val context = LocalContext.current
  // rememberSaveable, not remember: the camera app backgrounds (and may kill) this process while
  // the user is capturing the photo. ActivityResultRegistry survives that and redelivers the
  // result correctly, but a plain `remember` does not — it would reset to null on process
  // recreation, losing track of which file the camera wrote to.
  var pendingFilePath by rememberSaveable { mutableStateOf<String?>(null) }

  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture(),
  ) { success: Boolean ->
    val file = pendingFilePath?.let { File(it) }
    pendingFilePath = null
    if (!success || file == null) {
      Log.w(TAG, "Capture reported failure or missing target file — aborting")
      file?.delete()
      onError()
      return@rememberLauncherForActivityResult
    }
    if (file.length() == 0L) {
      Log.w(TAG, "Capture reported success but wrote an empty file — likely a permission issue")
      file.delete()
      onError()
      return@rememberLauncherForActivityResult
    }
    try {
      val uri = file.toFileProviderUri(context)
      val compressed = compressPhoto(context, uri)
      file.writeBytes(compressed)
      onResult(
        listOf(
          PickedFile(
            uri = uri.toString(),
            name = "IMG_${System.currentTimeMillis()}.jpg",
            mimeType = "image/jpeg",
            sizeBytes = compressed.size.toLong(),
          )
        )
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to decode/compress captured photo", e)
      file.delete()
      onError()
    }
  }

  return {
    try {
      val dir = File(context.cacheDir, "camera_temp").apply { mkdirs() }
      val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
      pendingFilePath = file.path
      val uri = file.toFileProviderUri(context)
      context.grantCameraUriPermission(uri)
      launcher.launch(uri)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to launch camera", e)
      onError()
    }
  }
}

private fun File.toFileProviderUri(context: Context): Uri =
  FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    this
  )

/**
 * `ActivityResultContracts.TakePicture()` passes the destination [uri] via `EXTRA_OUTPUT`, a
 * plain Intent extra — Android's flag-based auto-grant only covers `data`/`clipData`, so without
 * this the resolved camera app can fail to write to the FileProvider uri (silently, or by
 * writing an empty file) on devices that don't already hold broad storage access.
 */
private fun Context.grantCameraUriPermission(uri: Uri) {
  val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
  val resolvedActivities = packageManager.queryIntentActivities(
    captureIntent,
    PackageManager.MATCH_DEFAULT_ONLY,
  )
  for (info in resolvedActivities) {
    grantUriPermission(
      info.activityInfo.packageName,
      uri,
      Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
  }
}

/** Decodes [uri] and re-encodes as JPEG under [maxBytes], downscaling further if needed. */
private fun compressPhoto(
  context: Context,
  uri: Uri,
  maxBytes: Long = MAX_PHOTO_BYTES
): ByteArray {
  var maxDimension = MAX_DIMENSION
  var bytes: ByteArray
  var attempt = 0
  while (true) {
    val bitmap = decodeBounded(context, uri, maxDimension)
    bytes = try {
      encodeJpeg(bitmap, maxBytes)
    } finally {
      bitmap.recycle()
    }
    if (bytes.size <= maxBytes || attempt >= MAX_DOWNSCALE_ATTEMPTS) break
    maxDimension = (maxDimension * 0.7f).toInt()
    attempt++
  }
  return bytes
}

/** Decodes with EXIF orientation already applied, downsampled to at most [maxDimension] on a side. */
private fun decodeBounded(
  context: Context,
  uri: Uri,
  maxDimension: Int
): Bitmap {
  val source = ImageDecoder.createSource(context.contentResolver, uri)
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
