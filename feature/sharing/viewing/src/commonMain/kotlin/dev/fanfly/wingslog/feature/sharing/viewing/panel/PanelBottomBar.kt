package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.SHARE_URL_BASE
import dev.fanfly.wingslog.feature.sharing.viewing.AccessPanelView
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_cancel
import wingslog.feature.sharing.sharedassets.generated.resources.invite_copy
import wingslog.feature.sharing.sharedassets.generated.resources.invite_share
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_create_invite
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_done

@Composable
internal fun PanelBottomBar(
  state: ManageAccessUiState,
  onOpenInvite: () -> Unit,
  onCreateInvite: () -> Unit,
  onShareInvite: (url: String) -> Unit,
  onCopyInvite: (url: String) -> Unit,
  onCancelInvite: (codeId: String) -> Unit,
  onDone: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      // See the matching comment on PanelHeader — only needed on the edge-to-edge COMPACT sheet.
      .let { if (LocalLayoutTier.current == LayoutTier.COMPACT) it.navigationBarsPadding() else it }
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    when (state.view) {
      AccessPanelView.MAIN -> if (state.canManage) {
        PrimaryActionButton(
          label = stringResource(Res.string.manage_access_create_invite),
          onClick = onOpenInvite,
        )
      }

      AccessPanelView.INVITE -> PrimaryActionButton(
        label = stringResource(Res.string.manage_access_create_invite),
        loading = state.creatingInvite,
        onClick = onCreateInvite,
      )

      AccessPanelView.CODE -> {
        val invite = state.activeInvite
        val code = invite?.code
        if (code != null) {
          val url = "$SHARE_URL_BASE#$code"
          Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Button(
              onClick = { onShareInvite(url) },
              modifier = Modifier.weight(1f)
                .height(Spacing.buttonHeight),
            ) {
              Icon(Icons.Filled.IosShare, contentDescription = null)
              Spacer(Modifier.width(Spacing.small))
              Text(stringResource(Res.string.invite_share))
            }
            OutlinedButton(
              onClick = { onCopyInvite(url) },
              modifier = Modifier.weight(1f)
                .height(Spacing.buttonHeight),
            ) {
              Icon(Icons.Filled.ContentCopy, contentDescription = null)
              Spacer(Modifier.width(Spacing.small))
              Text(stringResource(Res.string.invite_copy))
            }
          }
        }
        invite?.let {
          TextButton(
            onClick = { onCancelInvite(it.codeId) },
            enabled = !state.cancellingInvite,
            modifier = Modifier.fillMaxWidth(),
          ) {
            if (state.cancellingInvite) {
              CircularProgressIndicator(
                Modifier.padding(2.dp)
                  .height(20.dp)
                  .width(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error,
              )
            } else {
              Text(
                stringResource(Res.string.invite_cancel),
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      }

      AccessPanelView.MEMBER -> PrimaryActionButton(
        label = stringResource(Res.string.manage_access_done),
        onClick = onDone,
      )
    }
  }
}
