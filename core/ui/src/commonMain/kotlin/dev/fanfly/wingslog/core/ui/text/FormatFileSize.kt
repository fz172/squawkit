package dev.fanfly.wingslog.core.ui.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.file_size_bytes
import wingslog.core.sharedassets.generated.resources.file_size_kb
import wingslog.core.sharedassets.generated.resources.file_size_mb
import wingslog.core.sharedassets.generated.resources.file_size_zero_kb

/**
 * Human-readable file size, decimal units: "0 KB" for empty/unknown, bytes below 1 KB,
 * KB rounded up (never "0 KB" for a real file), MB to one decimal from 1 MB.
 */
@Composable
fun Long.formatFileSize(): String {
  val (resource, quantity) = fileSizeParts(this)
  return if (quantity == null) stringResource(resource)
  else stringResource(resource, quantity)
}

/** Pure unit/quantity selection behind [formatFileSize]; split out for unit testing. */
internal fun fileSizeParts(bytes: Long): FileSizeParts = when {
  bytes <= 0L -> FileSizeParts(Res.string.file_size_zero_kb, null)
  bytes < 1_000L -> FileSizeParts(Res.string.file_size_bytes, bytes.toString())
  bytes < 1_000_000L -> FileSizeParts(Res.string.file_size_kb, ((bytes + 999L) / 1_000L).toString())
  else -> FileSizeParts(Res.string.file_size_mb, (bytes / 1_000_000.0).formatToOneDecimalPlace())
}
