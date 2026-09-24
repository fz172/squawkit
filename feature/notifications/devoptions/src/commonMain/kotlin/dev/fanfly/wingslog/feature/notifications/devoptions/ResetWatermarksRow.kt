package dev.fanfly.wingslog.feature.notifications.devoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.notifications.devoptions.generated.resources.Res
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_reset_watermarks_action
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_reset_watermarks_done
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_reset_watermarks_hint
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_reset_watermarks_no_user
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_reset_watermarks_title

/**
 * Wipes this account's watermarks. Kept next to "scan now" because the two are used together:
 * reset, scan to re-seed, change a record, scan again.
 */
@Composable
internal fun ResetWatermarksRow(
  scope: CoroutineScope,
  onReset: suspend () -> Boolean,
) {
  var status by remember { mutableStateOf<StringResource?>(null) }
  var resetting by remember { mutableStateOf(false) }

  Spacer(Modifier.height(Spacing.medium))
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.small),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(Res.string.notifications_devoptions_reset_watermarks_title),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = stringResource(
          status ?: Res.string.notifications_devoptions_reset_watermarks_hint
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.height(Spacing.small))
    OutlinedButton(
      enabled = !resetting,
      onClick = {
        resetting = true
        scope.launch {
          status =
            if (onReset()) Res.string.notifications_devoptions_reset_watermarks_done
            else Res.string.notifications_devoptions_reset_watermarks_no_user
          resetting = false
        }
      },
    ) {
      Text(stringResource(Res.string.notifications_devoptions_reset_watermarks_action))
    }
  }
}
