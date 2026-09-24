package dev.fanfly.wingslog.feature.sync.settings

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.sync.data.SyncFailure
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sync.settings.generated.resources.Res
import wingslog.feature.sync.settings.generated.resources.sync_status_auth_expired_body
import wingslog.feature.sync.settings.generated.resources.sync_status_hydration_error_body
import wingslog.feature.sync.settings.generated.resources.sync_status_push_error_body

@Composable
internal fun SyncFailure.displayText(): String = when (this) {
  is SyncFailure.AuthExpired -> stringResource(Res.string.sync_status_auth_expired_body)
  is SyncFailure.Hydration -> stringResource(
    Res.string.sync_status_hydration_error_body,
    kind.wireName,
    failedAttempts,
  )

  is SyncFailure.Push -> stringResource(Res.string.sync_status_push_error_body)
}
