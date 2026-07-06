package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import org.jetbrains.compose.resources.StringResource
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
internal fun fileSizeParts(bytes: Long): Pair<StringResource, String?> = when {
  bytes <= 0L -> Res.string.file_size_zero_kb to null
  bytes < 1_000L -> Res.string.file_size_bytes to bytes.toString()
  bytes < 1_000_000L -> Res.string.file_size_kb to ((bytes + 999L) / 1_000L).toString()
  else -> Res.string.file_size_mb to (bytes / 1_000_000.0).formatToOneDecimalPlace()
}
