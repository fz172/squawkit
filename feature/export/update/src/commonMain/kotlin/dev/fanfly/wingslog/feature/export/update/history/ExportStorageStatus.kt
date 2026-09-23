package dev.fanfly.wingslog.feature.export.update.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_history_status_cloud
import wingslog.feature.export.sharedassets.generated.resources.export_history_status_device

/**
 * Where the archive currently lives, for cloud-sync users. Returns null when no badge is warranted:
 * a no-email user (everything is local) or a fully synced file (local and remote).
 */
@Composable
internal fun exportStorageStatus(
  canEmailDelivery: Boolean,
  onDevice: Boolean,
  onRemote: Boolean,
): StorageStatus? = when {
  !canEmailDelivery -> null
  onDevice && onRemote -> null
  onRemote -> StorageStatus(
    icon = Icons.Outlined.Cloud,
    color = MaterialTheme.colorScheme.primary,
    label = Res.string.export_history_status_cloud,
  )

  // "On device" is a neutral location, not a caution: keep it informational so it doesn't read
  // as a warning (the Semantic Lock Rule reserves amber for action-required states).
  onDevice -> StorageStatus(
    icon = Icons.Outlined.Smartphone,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    label = Res.string.export_history_status_device,
  )

  else -> null
}
