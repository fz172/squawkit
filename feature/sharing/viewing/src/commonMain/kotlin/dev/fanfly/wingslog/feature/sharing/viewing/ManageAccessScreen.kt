package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.technicianNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.list.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.panel.AccessPanel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_empty_desc
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_empty_title
import wingslog.feature.sharing.sharedassets.generated.resources.sharing_sync_off_body
import wingslog.feature.sharing.sharedassets.generated.resources.sharing_sync_off_title

@Composable
fun ManageAccessScreen(
  state: ManageAccessUiState,
  onOpenInvite: () -> Unit,
  onSelectInviteRole: (ShareRole) -> Unit,
  onCreateInvite: () -> Unit,
  onOpenCode: (codeId: String) -> Unit,
  onCancelInvite: (codeId: String) -> Unit,
  onShareInvite: (url: String) -> Unit,
  onCopyInvite: (url: String) -> Unit,
  onOpenMember: (uid: String) -> Unit,
  onChangeRole: (uid: String, role: ShareRole) -> Unit,
  onRevoke: (uid: String) -> Unit,
  onLeave: () -> Unit,
  onToggleHelp: () -> Unit,
  onBackToMain: () -> Unit,
  onDismiss: () -> Unit,
  onToastShown: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when {
    state.isLoading -> Box(
      modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      CircularProgressIndicator(Modifier.padding(Spacing.xLarge))
    }

    !state.syncEnabled -> EmptyState(
      title = stringResource(Res.string.sharing_sync_off_title),
      description = stringResource(
        Res.string.sharing_sync_off_body,
        LexiconFormatter.sentenceCasePlural(LocalThingLexicon.current.thingNoun),
      ),
      icon = Icons.Filled.CloudOff,
      modifier = modifier.fillMaxSize(),
    )

    state.members.isEmpty() -> EmptyState(
      title = stringResource(Res.string.manage_access_empty_title),
      description = stringResource(
        Res.string.manage_access_empty_desc,
        LocalThingLexicon.current.technicianNoun.singular,
        LocalThingLexicon.current.thingNoun.singular,
      ),
      icon = Icons.Filled.Group,
      modifier = modifier.fillMaxSize(),
    )

    else -> AccessPanel(
      state = state,
      onOpenInvite = onOpenInvite,
      onSelectInviteRole = onSelectInviteRole,
      onCreateInvite = onCreateInvite,
      onOpenCode = onOpenCode,
      onCancelInvite = onCancelInvite,
      onShareInvite = onShareInvite,
      onCopyInvite = onCopyInvite,
      onOpenMember = onOpenMember,
      onChangeRole = onChangeRole,
      onRevoke = onRevoke,
      onLeave = onLeave,
      onToggleHelp = onToggleHelp,
      onBackToMain = onBackToMain,
      onDismiss = onDismiss,
      onToastShown = onToastShown,
      modifier = modifier,
    )
  }
}
